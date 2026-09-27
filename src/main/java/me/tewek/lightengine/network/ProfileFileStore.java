package me.tewek.lightengine.network;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import me.tewek.lightengine.LightEngine;
import me.tewek.lightengine.LightEngineConfig;
import me.tewek.lightengine.LightProfileRegistry;
import net.minecraft.resources.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Raw read-modify-write of profile tables in the single COMMON file.
 * NightConfig round-trips unknown keys, so nothing else is touched.
 * The config watcher reloads afterwards; refresh finds no changes, no loops.
 */
public final class ProfileFileStore {
    private ProfileFileStore() {
    }

    /** Plain key (no colon, so it can never collide with a block-id table). */
    private static final String ADDED_KEY = "added_blocks";

    public static void writeProfiles(Map<Identifier, LightProfileRegistry.Profile> deltas) {
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
            for (Map.Entry<Identifier, LightProfileRegistry.Profile> e : deltas.entrySet()) {
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

    /** Removes one profile table (reset to vanilla emission). */
    public static void removeProfile(Identifier id) {
        if (id == null) {
            return;
        }
        Path path = LightEngineConfig.filePath();
        if (!Files.isRegularFile(path)) {
            return;
        }
        CommentedFileConfig file = CommentedFileConfig.builder(path).sync().build();
        try {
            file.load();
            file.remove(id.toString());
            file.save();
        } catch (Exception ex) {
            LightEngine.LOGGER.warn("Could not remove profile from {}", LightEngineConfig.FILE_NAME, ex);
        } finally {
            try {
                file.close();
            } catch (Exception ignored) {
            }
        }
    }

    /** Reads the persisted "+" (Added section) ids; empty when never saved. */
    public static Set<Identifier> readAdded() {
        Set<Identifier> out = new LinkedHashSet<>();
        Path path = LightEngineConfig.filePath();
        if (!Files.isRegularFile(path)) {
            return out;
        }
        CommentedFileConfig file = CommentedFileConfig.builder(path).sync().build();
        try {
            file.load();
            Object raw = file.get(ADDED_KEY);
            if (raw instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof String s) {
                        try {
                            out.add(Identifier.parse(s));
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        } catch (Exception ex) {
            LightEngine.LOGGER.warn("Could not read {} from {}", ADDED_KEY, LightEngineConfig.FILE_NAME, ex);
        } finally {
            try {
                file.close();
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    /** Persists the "+" (Added section) ids (round-trips everything else). */
    public static void writeAdded(Collection<Identifier> ids) {
        Path path = LightEngineConfig.filePath();
        try {
            Files.createDirectories(path.getParent());
        } catch (Exception ignored) {
        }
        CommentedFileConfig file = CommentedFileConfig.builder(path).sync().build();
        try {
            file.load();
            List<String> names = new ArrayList<>();
            if (ids != null) {
                for (Identifier id : ids) {
                    if (id != null) {
                        names.add(id.toString());
                    }
                }
            }
            file.set(ADDED_KEY, names);
            file.save();
        } catch (Exception ex) {
            LightEngine.LOGGER.warn("Could not write {} to {}", ADDED_KEY, LightEngineConfig.FILE_NAME, ex);
        } finally {
            try {
                file.close();
            } catch (Exception ignored) {
            }
        }
    }
}
