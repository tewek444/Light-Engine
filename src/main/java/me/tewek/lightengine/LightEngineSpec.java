package me.tewek.lightengine;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableCommentedConfig;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;


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
                && config.get("check_updates") instanceof Boolean
                && config.get("color_strength") instanceof Number
                && !config.contains("min_color_correction");
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
                LightEngine.LOGGER.warn("Could not save {}", ConfigFile.FILE_NAME, ex);
            }
        }
        Config.ensureExampleComment();
    }

    static void onLoad(net.neoforged.fml.event.config.ModConfigEvent.Loading event) {
        reload(event.getConfig());
    }

    static void onReload(net.neoforged.fml.event.config.ModConfigEvent.Reloading event) {
        reload(event.getConfig());
    }

    private static void reload(ModConfig config) {
        if (config.getSpec() != INSTANCE || config.getLoadedConfig() == null) {
            return;
        }
        if (Config.refresh(config.getLoadedConfig().config())) {
            try {
                config.getLoadedConfig().save();
            } catch (Exception ex) {
                LightEngine.LOGGER.warn("Could not save {}", ConfigFile.FILE_NAME, ex);
            }
        }
        Config.ensureExampleComment();
    }
}
