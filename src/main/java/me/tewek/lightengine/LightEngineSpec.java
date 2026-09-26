package me.tewek.lightengine;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableCommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

/**
 * COMMON spec for the single {@code lightengine-common.toml} file.
 *
 * <p>Unlike {@code ModConfigSpec}, this spec never removes unknown keys:
 * block profile tables of the form {@code ["namespace:block"]} are preserved
 * on load, correction and save. Only {@code enabled} and {@code cnflvl} are
 * validated and defaulted; a legacy {@code storage_format} key is ignored.
 */
public final class LightEngineSpec implements IConfigSpec {
    public static final LightEngineSpec INSTANCE = new LightEngineSpec();

    public static final boolean DEFAULT_ENABLED = true;
    public static final String DEFAULT_CNFLVL = "1.0.0";
    public static final boolean DEFAULT_CHECK_UPDATES = true;
    public static final String FILE_NAME = "lightengine-common.toml";

    private LightEngineSpec() {
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public void validateSpec(ModConfig config) {
        // No per-value restart constraints: hot-reload is fully supported.
    }

    @Override
    public boolean isCorrect(UnmodifiableCommentedConfig config) {
        return config.get("enabled") instanceof Boolean
                && config.get("cnflvl") instanceof String
                && config.get("check_updates") instanceof Boolean;
    }

    @Override
    public void correct(CommentedConfig config) {
        Config.refresh(config);
    }

    @Override
    public void acceptConfig(ILoadedConfig loadedConfig) {
        boolean changed = Config.refresh(loadedConfig.config());
        if (changed) {
            try {
                loadedConfig.save();
            } catch (Exception ex) {
                LightEngine.LOGGER.warn("Could not save {}", FILE_NAME, ex);
            }
        }
        Config.ensureExampleComment();
    }

    /**
     * Writes {@code enabled}/{@code cnflvl} from the settings screen.
     * Loads and saves the raw file, so profile tables and comments survive.
     */
    public static void saveManual(boolean enabled, String cnflvl) {
        Path path = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
        CommentedFileConfig file = CommentedFileConfig.builder(path).sync().build();
        try {
            file.load();
            file.set("enabled", enabled);
            file.set("cnflvl", cnflvl == null || cnflvl.isBlank() ? DEFAULT_CNFLVL : cnflvl);
            file.save();
            Config.ensureExampleComment();
        } catch (Exception ex) {
            LightEngine.LOGGER.warn("Could not save {}", FILE_NAME, ex);
        } finally {
            try {
                file.close();
            } catch (Exception ignored) {
            }
        }
    }
}
