package me.tewek.lightengine;

import com.electronwill.nightconfig.core.CommentedConfig;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Parsing of the single COMMON file: {@code cnflvl} + {@code enabled}
 * scalars plus block profiles {@code ["namespace:block"] light_radius} with
 * optional {@code ignore_conditions}. A legacy {@code storage_format} key,
 * if present, is ignored. Never removes keys; only adds missing defaults.
 * With no profiles present, no default entry is created — a copy-paste
 * example is appended to the file text (after the scalars) instead, so
 * lighting stays vanilla until configured.
 * Returns whether the passed config was mutated (caller decides about saving).
 */
public final class Config {
    private Config() {
    }

    /** Whether the background update check may run (client reads this). */
    private static volatile boolean checkUpdates = true;

    public static boolean isCheckUpdates() {
        return checkUpdates;
    }

    /**
     * Appends the copy-paste profile example to the end of the file when it
     * has neither profile tables nor the example marker yet. NightConfig has
     * no trailing-comment API (comments always attach above a key), hence
     * the file-level append. Idempotent: converges after at most one extra
     * watcher cycle, never loops.
     */
    static void ensureExampleComment() {
        Path path;
        try {
            path = LightEngineConfig.filePath();
        } catch (Exception ex) {
            return;
        }
        try {
            if (!Files.isRegularFile(path)) {
                return;
            }
            String text = Files.readString(path, StandardCharsets.UTF_8);
            boolean hasMarker = false;
            boolean hasTable = false;
            for (String line : text.split("\n")) {
                String t = line.strip();
                if (t.contains("Block profiles:")) {
                    hasMarker = true;
                    break;
                }
                if (!t.startsWith("#") && t.startsWith("[")) {
                    hasTable = true;
                }
            }
            if (hasMarker || hasTable) {
                return;
            }
            StringBuilder out = new StringBuilder(text);
            if (!text.isEmpty() && !text.endsWith("\n")) {
                out.append('\n');
            }
            out.append('\n');
            out.append("#Block profiles: one table per block, e.g.\n");
            out.append("#[\"minecraft:glowstone\"]\n");
            out.append("#light_radius = 30\n");
            out.append("#ignore_conditions = false\n");
            Files.writeString(path, out.toString(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            LightEngine.LOGGER.warn("Could not update {}", LightEngineConfig.FILE_NAME, ex);
        }
    }

    static boolean refresh(CommentedConfig root) {
        boolean changed = false;

        String cnflvl = LightEngineConfig.DEFAULT_CNFLVL;
        Object cnflvlRaw = root.get("cnflvl");
        if (cnflvlRaw instanceof String s) {
            cnflvl = s;
        } else {
            root.set("cnflvl", cnflvl);
            changed = true;
        }

        boolean enabled = LightEngineConfig.DEFAULT_ENABLED;
        Object enabledRaw = root.get("enabled");
        if (enabledRaw instanceof Boolean b) {
            enabled = b;
        } else {
            root.set("enabled", enabled);
            changed = true;
        }

        boolean check = LightEngineConfig.DEFAULT_CHECK_UPDATES;
        Object checkRaw = root.get("check_updates");
        if (checkRaw instanceof Boolean b) {
            check = b;
        } else {
            root.set("check_updates", check);
            changed = true;
        }
        checkUpdates = check;

        Map<net.minecraft.resources.ResourceLocation, LightProfileRegistry.Profile> fresh = new HashMap<>();
        for (Map.Entry<String, Object> entry : root.valueMap().entrySet()) {
            String key = entry.getKey();
            if (key.equals("enabled") || key.equals("cnflvl") || key.equals("check_updates")
                    || key.equals("storage_format")) {
                continue;
            }
            Object raw = entry.getValue();
            if (!(raw instanceof CommentedConfig table)) {
                continue;
            }
            net.minecraft.resources.ResourceLocation id;
            try {
                id = new net.minecraft.resources.ResourceLocation(key);
            } catch (Exception ex) {
                continue;
            }
            Object radiusRaw = table.get("light_radius");
            if (!(radiusRaw instanceof Number number)) {
                continue;
            }
            // Reject non-integral TOML types (doubles etc).
            double d = number.doubleValue();
            if (d != Math.rint(d)) {
                continue;
            }
            int radius = number.intValue();
            if (radius < 0) {
                radius = 0;
            } else if (radius > 255) {
                radius = 255;
            }
            boolean ignoreConditions = false;
            Object ignoreRaw = table.get("ignore_conditions");
            if (ignoreRaw instanceof Boolean b) {
                ignoreConditions = b;
            }
            fresh.put(id, new LightProfileRegistry.Profile(radius, ignoreConditions));
        }

        // No profiles at all is fine: the registry stays empty (vanilla
        // lighting) and ensureExampleComment() documents the format in the
        // file after each save.
        LightProfileRegistry.apply(enabled, cnflvl, fresh);
        StringBuilder detail = new StringBuilder();
        int shown = 0;
        for (Map.Entry<net.minecraft.resources.ResourceLocation, LightProfileRegistry.Profile> e : fresh.entrySet()) {
            if (shown >= 12) {
                detail.append(", ...");
                break;
            }
            if (shown > 0) {
                detail.append(", ");
            }
            detail.append(e.getKey()).append('=').append(e.getValue().lightRadius());
            if (e.getValue().ignoreConditions()) {
                detail.append('*');
            }
            shown++;
        }
        LightEngine.LOGGER.info("LightEngine config: enabled={}, cnflvl={}, check_updates={}, profiles={} [{}]",
                enabled, cnflvl, check, fresh.size(), detail);
        return changed;
    }
}
