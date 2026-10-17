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
 * Returns whether the passed config was mutated
 * (caller decides about saving).
 */
public final class Config {
    private Config() {
    }

    /** Whether the background update check may run (client reads this). */
    private static volatile boolean checkUpdates = true;
    private static volatile int colorStrength = ConfigFile.DEFAULT_COLOR_STRENGTH;

    public static boolean isCheckUpdates() {
        return checkUpdates;
    }

    /** How strongly profile colors replace the vanilla warm block light, 0-100 percent. */
    public static int colorStrength() {
        return colorStrength;
    }

    public static void setColorStrength(int value) {
        colorStrength = Math.clamp(value, 0, 100);
    }

    /**
     * Appends the copy-paste profile example to the end of the file when it
     * has neither profile tables nor the example marker yet. NightConfig has
     * no trailing-comment API (comments always attach above a key), hence
     * the file-level append. Idempotent: converges after at most one extra
     * watcher cycle, never loops.
     */
    public static void ensureExampleComment() {
        Path path;
        try {
            path = ConfigFile.path();
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
            out.append("#light_color = \"#FF914C\"\n");
            out.append("#light_color_enabled = true\n");
            Files.writeString(path, out.toString(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            LightEngine.LOGGER.warn("Could not update {}", ConfigFile.FILE_NAME, ex);
        }
    }

    public static boolean refresh(CommentedConfig root) {
        boolean changed = false;

        String cnflvl = ConfigFile.DEFAULT_CNFLVL;
        Object cnflvlRaw = root.get("cnflvl");
        if (cnflvlRaw instanceof String s) {
            cnflvl = s;
        } else {
            root.set("cnflvl", cnflvl);
            changed = true;
        }

        boolean enabled = ConfigFile.DEFAULT_ENABLED;
        Object enabledRaw = root.get("enabled");
        if (enabledRaw instanceof Boolean b) {
            enabled = b;
        } else {
            root.set("enabled", enabled);
            changed = true;
        }

        boolean check = ConfigFile.DEFAULT_CHECK_UPDATES;
        Object checkRaw = root.get("check_updates");
        if (checkRaw instanceof Boolean b) {
            check = b;
        } else {
            root.set("check_updates", check);
            changed = true;
        }
        checkUpdates = check;

        int strength = ConfigFile.DEFAULT_COLOR_STRENGTH;
        Object strengthRaw = root.get("color_strength");
        if (strengthRaw instanceof Number number) {
            strength = Math.clamp(number.intValue(), 0, 100);
        } else {
            root.set("color_strength", strength);
            changed = true;
        }
        colorStrength = strength;
        // Alpha4 key, meaningless with the Alpha5 color model.
        if (root.contains("min_color_correction")) {
            root.remove("min_color_correction");
            changed = true;
        }

        Map<net.minecraft.resources.Identifier, LightProfileRegistry.Profile> fresh = new HashMap<>();
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
            net.minecraft.resources.Identifier id;
            try {
                id = net.minecraft.resources.Identifier.parse(key);
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
            int color = LightProfileRegistry.DEFAULT_COLOR;
            Object colorRaw = table.get("light_color");
            if (colorRaw != null) {
                color = LightProfileRegistry.parseColor(colorRaw);
            }
            boolean tintEnabled = true;
            Object tintRaw = table.get("light_color_enabled");
            if (tintRaw instanceof Boolean b) {
                tintEnabled = b;
            }
            fresh.put(id, new LightProfileRegistry.Profile(radius, ignoreConditions, color, tintEnabled));
        }

        // No profiles at all is fine: the registry stays empty (vanilla
        // lighting) and ensureExampleComment() documents the format in the
        // file after each save.
        // First run: seed the default preset so the mod works out of the box.
        // `preset_applied` keeps it one-shot (deleting every profile later is
        // respected; delete the flag to re-seed). `preset` picks which one.
        if (fresh.isEmpty() && !(root.get("preset_applied") instanceof Boolean)) {
            Object presetRaw = root.get("preset");
            LightPresets.Mode mode = LightPresets.Mode.byKey(presetRaw instanceof String ps ? ps : null);
            for (Map.Entry<net.minecraft.resources.Identifier, LightProfileRegistry.Profile> e
                    : LightPresets.build(mode).entrySet()) {
                LightProfileRegistry.Profile p = e.getValue();
                CommentedConfig table = root.createSubConfig();
                table.set("light_radius", p.lightRadius());
                table.set("light_color", LightProfileRegistry.formatColor(p.color()));
                table.set("light_color_enabled", p.tintEnabled());
                table.set("ignore_conditions", p.ignoreConditions());
                root.set(java.util.List.of(e.getKey().toString()), table);
                fresh.put(e.getKey(), p);
            }
            root.set("preset", mode.key);
            root.set("preset_applied", true);
            root.set("preset_revision", LightPresets.REVISION);
            changed = true;
        }
        // Newer mod version with new preset rows (new light blocks): add only
        // the blocks the config does not have yet, never touch existing ones.
        if (root.get("preset_applied") instanceof Boolean) {
            int revision = root.get("preset_revision") instanceof Number n ? n.intValue() : 1;
            if (revision < LightPresets.REVISION) {
                Object presetRaw = root.get("preset");
                LightPresets.Mode mode = LightPresets.Mode.byKey(presetRaw instanceof String ps ? ps : null);
                for (var e : LightPresets.build(mode, revision).entrySet()) {
                    if (fresh.containsKey(e.getKey())) {
                        continue;
                    }
                    LightProfileRegistry.Profile p = e.getValue();
                    CommentedConfig table = root.createSubConfig();
                    table.set("light_radius", p.lightRadius());
                    table.set("light_color", LightProfileRegistry.formatColor(p.color()));
                    table.set("light_color_enabled", p.tintEnabled());
                    table.set("ignore_conditions", p.ignoreConditions());
                    root.set(java.util.List.of(e.getKey().toString()), table);
                    fresh.put(e.getKey(), p);
                }
                root.set("preset_revision", LightPresets.REVISION);
                changed = true;
            }
        }
        LightProfileRegistry.apply(enabled, cnflvl, fresh);
        StringBuilder detail = new StringBuilder();
        int shown = 0;
        int capped = 0;
        String cappedExample = "";
        for (Map.Entry<net.minecraft.resources.Identifier, LightProfileRegistry.Profile> e : fresh.entrySet()) {
            if (shown >= 12) {
                detail.append(", ...");
                break;
            }
            if (shown > 0) {
                detail.append(", ");
            }
            LightProfileRegistry.Profile p = e.getValue();
            detail.append(e.getKey()).append('=').append(p.lightRadius())
                    .append(LightProfileRegistry.formatColor(p.color()));
            if (p.ignoreConditions()) {
                detail.append('*');
            }
            if (p.lightRadius() > LightProfileRegistry.EFFECTIVE_MAX_RADIUS) {
                capped++;
                if (cappedExample.isEmpty()) {
                    cappedExample = e.getKey().toString();
                }
            }
            shown++;
        }
        if (capped > 0) {
            LightEngine.LOGGER.warn(
                    "LightEngine: {} profile(s) exceed the effective propagation cap {} (e.g. {}); stored values kept, waves capped (perf)",
                    capped, LightProfileRegistry.EFFECTIVE_MAX_RADIUS, cappedExample);
        }
        LightEngine.LOGGER.info("LightEngine config: enabled={}, cnflvl={}, check_updates={}, profiles={} [{}]",
                enabled, cnflvl, check, fresh.size(), detail);
        return changed;
    }
}
