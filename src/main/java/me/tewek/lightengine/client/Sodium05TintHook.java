package me.tewek.lightengine.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import me.tewek.lightengine.LightEngine;
import me.tewek.lightengine.LightProfileRegistry;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;

/**
 * Colored light under Sodium 0.5 (the only Sodium line for 1.20.x), whose
 * classes still live in {@code me.jellysquid.mods.sodium}. Every terrain quad
 * passes {@code BlockRenderer.writeGeometry(ctx, builder, offset, material,
 * quad, colors, light)}: {@code light.lm} holds the per-vertex packed light
 * and {@code colors} the per-vertex ABGR biome tint, or null for untinted
 * blocks. {@link #prepare} solves the quad and rewrites {@code lm} in place;
 * {@link #colors} then swaps in the tinted colors when the method reads them.
 *
 * <p>Sodium types are reached by reflection only (cached per class), so this
 * survives any Sodium build and can only ever skip silently.</p>
 */
public final class Sodium05TintHook {
    private Sodium05TintHook() {
    }

    private static final ThreadLocal<int[]> TINTED = ThreadLocal.withInitial(() -> new int[4]);
    private static final ThreadLocal<boolean[]> ACTIVE = ThreadLocal.withInitial(() -> new boolean[1]);
    private static final ThreadLocal<float[][]> POS = ThreadLocal.withInitial(() -> new float[3][4]);
    private static final ThreadLocal<int[]> LIGHT = ThreadLocal.withInitial(() -> new int[4]);

    private static volatile boolean disabled;
    private static Class<?> ctxClass;
    private static Method posMethod;
    private static Method worldMethod;
    private static Class<?> lightClass;
    private static Field lmField;

    /** Head of {@code writeGeometry}: solve the quad, rewrite its light. */
    public static void prepare(Object ctx, Object rawQuad, int[] colors, Object lightData) {
        boolean[] active = ACTIVE.get();
        active[0] = false;
        if (disabled || ctx == null || lightData == null || !(rawQuad instanceof BakedQuad quad)) {
            return;
        }
        if (!LightProfileRegistry.isEnabled() || LightProfileRegistry.isEmpty()) {
            return;
        }
        try {
            resolve(ctx.getClass(), lightData.getClass());
            BlockPos pos = (BlockPos) posMethod.invoke(ctx);
            if (pos == null || !(worldMethod.invoke(ctx) instanceof BlockAndTintGetter level)) {
                return;
            }
            int[] lm = (int[]) lmField.get(lightData);
            int[] data = quad.getVertices();
            int stride = data.length / 4;
            if (lm == null || lm.length < 4 || stride < 3) {
                return;
            }
            float[][] p = POS.get();
            int[] light = LIGHT.get();
            for (int i = 0; i < 4; i++) {
                p[0][i] = Float.intBitsToFloat(data[i * stride]);
                p[1][i] = Float.intBitsToFloat(data[i * stride + 1]);
                p[2][i] = Float.intBitsToFloat(data[i * stride + 2]);
                light[i] = lm[i];
            }
            QuadTinter.Result res = QuadTinter.compute(level, pos, quad.getDirection(), p[0], p[1], p[2], light, 4);
            if (res == null) {
                return;
            }
            int[] tinted = TINTED.get();
            for (int i = 0; i < 4; i++) {
                lm[i] = res.light[i];
                tinted[i] = scaleAbgr(colors == null ? 0xFFFFFFFF : colors[i], res.mult, i * 3);
            }
            active[0] = true;
        } catch (Throwable t) {
            disabled = true;
            LightEngine.LOGGER.warn("Sodium 0.5 colored-light hook failed; disabling it for this session", t);
        }
    }

    /** Every read of the {@code colors} argument: the tinted copy when {@link #prepare} solved the quad. */
    public static int[] colors(int[] original) {
        return ACTIVE.get()[0] ? TINTED.get() : original;
    }

    /** Multiplies the RGB of a packed ABGR color (red in the low byte), keeping alpha. */
    static int scaleAbgr(int abgr, float[] mult, int o) {
        int r = Math.min(Math.round((abgr & 0xFF) * mult[o]), 255);
        int g = Math.min(Math.round(((abgr >> 8) & 0xFF) * mult[o + 1]), 255);
        int b = Math.min(Math.round(((abgr >> 16) & 0xFF) * mult[o + 2]), 255);
        return (abgr & 0xFF000000) | (b << 16) | (g << 8) | r;
    }

    private static synchronized void resolve(Class<?> ctx, Class<?> lightData) throws ReflectiveOperationException {
        if (ctx != ctxClass) {
            posMethod = ctx.getMethod("pos");
            worldMethod = ctx.getMethod("world");
            ctxClass = ctx;
        }
        if (lightData != lightClass) {
            lmField = lightData.getField("lm");
            lightClass = lightData;
        }
    }
}
