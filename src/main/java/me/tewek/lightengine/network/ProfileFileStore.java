package me.tewek.lightengine.network;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import me.tewek.lightengine.LightEngine;
import me.tewek.lightengine.LightEngineConfig;
import me.tewek.lightengine.LightProfileRegistry;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Raw read-modify-write of profile tables in the single COMMON file.
 * NightConfig round-trips unknown keys, so nothing else is touched.
 * The config watcher reloads afterwards; refresh finds no changes, no loops.
 */
public final class ProfileFileStore {
    private ProfileFileStore() {
    }

    public static void writeProfiles(Map<ResourceLocation, LightProfileRegistry.Profile> deltas) {
        if (deltas == null || deltas.isEmpty()) {
            return;
        }
        Path path = LightEngineConfig.filePath();
        try {
            Files.createDirectories(path.getParent());
        } catch (Exception ignored) {
        }
        CommentedFileConfig file = CommentedFileConfig.builder(path).sync().build();
        try {
            file.load();
            for (Map.Entry<ResourceLocation, LightProfileRegistry.Profile> e : deltas.entrySet()) {
                String key = e.getKey().toString();
                Object raw = file.get(key);
                CommentedConfig table;
                if (raw instanceof CommentedConfig existing) {
                    table = existing;
                } else {
                    table = CommentedConfig.inMemory();
                    file.set(key, table);
                }
                LightProfileRegistry.Profile profile = e.getValue() == null
                        ? new LightProfileRegistry.Profile(0, false)
                        : e.getValue();
                table.set("light_radius", profile.lightRadius());
                table.set("ignore_conditions", profile.ignoreConditions());
            }
            file.save();
        } catch (Exception ex) {
            LightEngine.LOGGER.warn("Could not write profiles to {}", LightEngineConfig.FILE_NAME, ex);
        } finally {
            try {
                file.close();
            } catch (Exception ignored) {
            }
        }
    }
}
