package me.tewek.lightengine.client;

import me.tewek.lightengine.Compat;
import me.tewek.lightengine.LightEngine;
import me.tewek.lightengine.LightProfileRegistry;
import me.tewek.lightengine.lightfield.ChromaTint;
import me.tewek.lightengine.lightfield.LightmapModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Client-thread snapshot of the lightmap inputs for mesh workers (Alpha5).
 *
 * <p>Chunk meshes bake the colored-light solve, which depends on sky
 * darkness (day/night), dimension ambient and the gamma option. Vanilla never
 * re-meshes on those changes, so this watcher quantizes them and, when the
 * bucket moves, re-meshes only the halos of known colored emitters
 * (throttled). Mesh threads read {@link #current()} lock-free.</p>
 */
public final class ColorLightEnv {
    private ColorLightEnv() {
    }

    /** Sky darkness buckets: ~16 re-bakes across a full dusk. */
    private static final float SKY_STEP = 1.0F / 20.0F;
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
        float sky = Math.round(level.getSkyDarken(1.0F) / SKY_STEP) * SKY_STEP;
        float ambient = level.dimensionType().ambientLight();
        float gamma = mc.options.gamma().get().floatValue();
        // 1.21.9+ lightmap has no bright mode (ambient color instead); overworld is exact.
        boolean forceBright = false;
        LightmapModel.Env env = current;
        if (env == null || lastLevel != level || !env.sameAs(sky, ambient, gamma, forceBright)) {
            current = LightmapModel.env(sky, ambient, gamma, forceBright);
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
