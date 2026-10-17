package me.tewek.lightengine.client;

import me.tewek.lightengine.Compat;
import me.tewek.lightengine.LightEngine;
import me.tewek.lightengine.LightProfileRegistry;
import me.tewek.lightengine.lightfield.ChromaTint;
import me.tewek.lightengine.lightfield.LightmapModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.attribute.EnvironmentAttributes;

/**
 * Client-thread snapshot of the lightmap inputs for mesh workers (Alpha5).
 *
 * <p>Chunk meshes bake the colored-light solve, which depends on the
 * lightmap inputs (26.x: sky light factor and color from the day timeline,
 * block light tint and ambient color per biome/dimension) and the gamma
 * option. Vanilla never
 * re-meshes on those changes, so this watcher quantizes them and, when the
 * bucket moves, re-meshes only the halos of known colored emitters
 * (throttled). Mesh threads read {@link #current()} lock-free.</p>
 */
public final class ColorLightEnv {
    private ColorLightEnv() {
    }

    /** Sky darkness buckets: ~16 re-bakes across a full dusk. */
    private static final float SKY_STEP = 1.0F / 20.0F;
    private static final float COLOR_STEP = 1.0F / 32.0F;
    private static final int MIN_REFRESH_TICKS = 40;
    private static final int SHADER_POLL_TICKS = 20;

    private static volatile LightmapModel.Env current;
    private static volatile boolean shaderActive;
    private static boolean pendingRefresh;
    private static int ticksSinceRefresh = MIN_REFRESH_TICKS;
    private static int shaderPoll;
    private static Object lastLevel;

    /** Env for meshing, or null when colored light must stay off. */
    public static LightmapModel.Env current() {
        return shaderActive ? null : current;
    }

    /** Color attributes drift smoothly across biomes: bucket them like sky light. */
    private static float[] quantize(int rgb) {
        return new float[]{
                Math.round(((rgb >> 16) & 0xFF) / 255.0F / COLOR_STEP) * COLOR_STEP,
                Math.round(((rgb >> 8) & 0xFF) / 255.0F / COLOR_STEP) * COLOR_STEP,
                Math.round((rgb & 0xFF) / 255.0F / COLOR_STEP) * COLOR_STEP};
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            current = null;
            lastLevel = null;
            return;
        }
        if (--shaderPoll <= 0) {
            shaderPoll = SHADER_POLL_TICKS;
            boolean active = Compat.isShaderPipelineActive();
            if (active != shaderActive) {
                shaderActive = active;
                pendingRefresh = true;
            }
        }
        var probe = mc.gameRenderer.mainCamera().attributeProbe();
        float[] tint = quantize(probe.getValue(EnvironmentAttributes.BLOCK_LIGHT_TINT, 1.0F));
        float[] skyColor = quantize(probe.getValue(EnvironmentAttributes.SKY_LIGHT_COLOR, 1.0F));
        float[] ambient = quantize(probe.getValue(EnvironmentAttributes.AMBIENT_LIGHT_COLOR, 1.0F));
        float sky = Math.round(probe.getValue(EnvironmentAttributes.SKY_LIGHT_FACTOR, 1.0F) / SKY_STEP) * SKY_STEP;
        float gamma = mc.options.gamma().get().floatValue();
        LightmapModel.Env env = current;
        if (env == null || lastLevel != level || !env.sameAs(tint, skyColor, sky, ambient, gamma)) {
            current = LightmapModel.env(tint, skyColor, sky, ambient, gamma);
            lastLevel = level;
            // Also on a fresh level: sections meshed before the first tick
            // had no env and baked vanilla light.
            pendingRefresh = true;
        }
        ticksSinceRefresh++;
        if (pendingRefresh && ticksSinceRefresh >= MIN_REFRESH_TICKS) {
            pendingRefresh = false;
            ticksSinceRefresh = 0;
            if (LightProfileRegistry.isEnabled() && ChromaTint.hasEmitters(level)) {
                LightEngine.LOGGER.debug("Colored light env changed (sky={}, gamma={}), re-baking halos", sky, gamma);
                HaloRefresh.schedule(level, ChromaTint.allEmitters(level));
            }
        }
    }
}
