package me.tewek.lightengine;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import me.tewek.lightengine.platform.Platform;

import java.nio.file.Path;

/**
 * The single {@code lightengine-common.toml}: defaults and raw-file writes
 * that keep profile tables and comments intact. Loader-neutral; the loading
 * and hot-reload wiring lives in the loader layer.
 */
public final class ConfigFile {
    private ConfigFile() {
    }

    public static final boolean DEFAULT_ENABLED = true;
    public static final String DEFAULT_CNFLVL = "1.0.0";
    public static final boolean DEFAULT_CHECK_UPDATES = true;
    public static final int DEFAULT_COLOR_STRENGTH = 100;
    public static final String FILE_NAME = "lightengine-common.toml";

    public static Path path() {
        return Platform.configDir().resolve(FILE_NAME);
    }

    /** Writes {@code enabled}/{@code cnflvl} from the settings screen. */
    public static void saveManual(boolean enabled, String cnflvl) {
        CommentedFileConfig file = CommentedFileConfig.builder(path()).sync().build();
        try {
            file.load();
            file.set("enabled", enabled);
            file.set("cnflvl", cnflvl == null || cnflvl.isBlank() ? DEFAULT_CNFLVL : cnflvl);
            file.save();
            Config.ensureExampleComment();
        } catch (Exception ex) {
            LightEngine.LOGGER.warn("Could not save {}", FILE_NAME, ex);
        } finally {
            closeQuietly(file);
        }
    }

    /** Persists the colored-light strength (0-100%). */
    public static void saveColorStrength(int value) {
        CommentedFileConfig file = CommentedFileConfig.builder(path()).sync().build();
        try {
            file.load();
            file.set("color_strength", Math.max(0, Math.min(100, value)));
            file.save();
        } catch (Exception ex) {
            LightEngine.LOGGER.warn("Could not save {}", FILE_NAME, ex);
        } finally {
            closeQuietly(file);
        }
    }

    private static void closeQuietly(CommentedFileConfig file) {
        try {
            file.close();
        } catch (Exception ignored) {
            // nothing to do
        }
    }
}
