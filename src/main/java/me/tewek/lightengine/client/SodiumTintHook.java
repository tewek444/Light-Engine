package me.tewek.lightengine.client;

import me.tewek.lightengine.LightEngine;
import me.tewek.lightengine.LightProfileRegistry;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndLightGetter;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Sodium terrain colored light (client only, no hard dependency).
 *
 * <p>Sodium 0.6+ builds chunk meshes in its own {@code BlockRenderer} and
 * never calls {@code ModelBlockRenderer.putQuadData}. The Sodium mixin calls
 * {@link #tintSodiumQuad} at the head of {@code bufferQuad} — after Sodium's
 * biome tint and light sampling, before vertices are packed — and this
 * applies the same per-vertex solve as the vanilla path
 * ({@link QuadTinter}): per-vertex color multiply plus new lightmap
 * coordinates on Sodium's mutable quad.</p>
 *
 * <p>All Sodium access goes through method handles resolved once per class,
 * so Sodium is never a compile dependency. Every failure leaves the quad
 * untouched; after the first failure the hook logs once and disables
 * itself.</p>
 */
public final class SodiumTintHook {
    private SodiumTintHook() {
    }

    private static volatile boolean disabled;

    // ---- renderer-field cache (resolved once per Sodium renderer class) ----
    private static volatile Class<?> cachedRendererClass;
    private static volatile Field posField;
    private static volatile Field levelField;
    /** Indigo keeps pos/level in a {@code blockInfo} holder instead of on the context. */
    private static volatile Field infoField;

    // ---- quad accessors (resolved once per Sodium quad class) ----
    private static volatile Class<?> cachedQuadClass;
    private static volatile MethodHandle faceHandle;
    private static volatile MethodHandle getColor;
    private static volatile MethodHandle setColor;
    private static volatile MethodHandle getLight;
    private static volatile MethodHandle setLight;
    private static volatile MethodHandle posX;
    private static volatile MethodHandle posY;
    private static volatile MethodHandle posZ;

    private static final ThreadLocal<float[][]> POS = ThreadLocal.withInitial(() -> new float[3][4]);
    private static final ThreadLocal<int[]> LIGHT = ThreadLocal.withInitial(() -> new int[4]);

    /**
     * Called from the Sodium {@code bufferQuad} head. Both arguments are
     * Sodium internals ({@code BlockRenderer}, {@code MutableQuadViewImpl})
     * kept as {@link Object} on purpose.
     */
    public static void tintSodiumQuad(Object renderer, Object quad) {
        if (disabled || renderer == null || quad == null) {
            return;
        }
        if (!LightProfileRegistry.isEnabled() || LightProfileRegistry.isEmpty()) {
            return;
        }
        try {
            ensureRendererFields(renderer.getClass());
            ensureQuadHandles(quad.getClass());
            if (getLight == null || setLight == null) {
                return;
            }
            int[] light = LIGHT.get();
            for (int i = 0; i < 4; i++) {
                int lm = (int) getLight.invoke(quad, i);
                // Extended block light never reaches the shader (overexposure).
                if (LightCoordsUtil.block(lm) > 15) {
                    lm = LightCoordsUtil.pack(15, LightCoordsUtil.sky(lm));
                    setLight.invoke(quad, i, lm);
                }
                light[i] = lm;
            }
            Object holder = infoField == null ? renderer : infoField.get(renderer);
            BlockPos pos = posField == null || holder == null ? null : (BlockPos) posField.get(holder);
            Object rawLevel = levelField == null || holder == null ? null : levelField.get(holder);
            if (pos == null || !(rawLevel instanceof BlockAndLightGetter level)
                    || getColor == null || setColor == null || posX == null || posY == null || posZ == null) {
                return;
            }
            Direction face = faceHandle == null ? null : (Direction) faceHandle.invoke(quad);
            float[][] p = POS.get();
            for (int i = 0; i < 4; i++) {
                p[0][i] = (float) posX.invoke(quad, i);
                p[1][i] = (float) posY.invoke(quad, i);
                p[2][i] = (float) posZ.invoke(quad, i);
            }
            QuadTinter.Result res = QuadTinter.compute(level, pos, face, p[0], p[1], p[2], light, 4);
            if (res == null) {
                return;
            }
            for (int i = 0; i < 4; i++) {
                int argb = (int) getColor.invoke(quad, i);
                setColor.invoke(quad, i, QuadTinter.scaleArgb(argb, res.mult, i * 3));
                setLight.invoke(quad, i, res.light[i]);
            }
        } catch (Throwable t) {
            disabled = true;
            LightEngine.LOGGER.warn("Sodium colored-light hook failed; disabling it for this session", t);
        }
    }

    private static synchronized void ensureRendererFields(Class<?> cls) {
        if (cls == cachedRendererClass) {
            return;
        }
        // `level` is the meshing view (a LevelSlice) in every Sodium line;
        // `slice` is only a fallback for a hypothetical layout without it.
        levelField = findField(cls, "level", "slice");
        posField = findField(cls, "pos");
        infoField = null;
        if (levelField == null || posField == null) {
            // Fabric Indigo: AbstractBlockRenderContext.blockInfo -> BlockRenderInfo.
            Field info = findField(cls, "blockInfo");
            if (info != null) {
                infoField = info;
                levelField = findField(info.getType(), "blockView", "level");
                posField = findField(info.getType(), "blockPos", "pos");
            }
        }
        cachedRendererClass = cls;
        if (levelField == null || posField == null) {
            LightEngine.LOGGER.warn("Sodium renderer layout not recognized ({}); colored light off under Sodium",
                    cls.getName());
        }
    }

    private static Field findField(Class<?> cls, String... names) {
        for (String name : names) {
            for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    return f;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // try the next class / name
                }
            }
        }
        return null;
    }

    private static synchronized void ensureQuadHandles(Class<?> cls) {
        if (cls == cachedQuadClass) {
            return;
        }
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        faceHandle = handle(lookup, cls, Direction.class, new Class<?>[0],
                "lightFace", "getLightFace", "nominalFace", "getNominalFace");
        getColor = handle(lookup, cls, int.class, new Class<?>[]{int.class}, "color", "getColor", "baseColor");
        setColor = handle(lookup, cls, null, new Class<?>[]{int.class, int.class}, "color", "setColor");
        getLight = handle(lookup, cls, int.class, new Class<?>[]{int.class}, "lightmap", "getLight", "getLightmap");
        setLight = handle(lookup, cls, null, new Class<?>[]{int.class, int.class}, "lightmap", "setLight",
                "setLightmap");
        posX = handle(lookup, cls, float.class, new Class<?>[]{int.class}, "x", "getX");
        posY = handle(lookup, cls, float.class, new Class<?>[]{int.class}, "y", "getY");
        posZ = handle(lookup, cls, float.class, new Class<?>[]{int.class}, "z", "getZ");
        cachedQuadClass = cls;
        if (getColor == null || setColor == null || getLight == null || setLight == null || posX == null) {
            LightEngine.LOGGER.warn("Sodium quad layout not recognized ({}); colored light off under Sodium",
                    cls.getName());
        }
    }

    /**
     * Finds a public-or-declared method and adapts it to
     * {@code (Object, params...) -> ret} (setters return void).
     *
     * @param ret required return type, or null for a setter (any return, dropped)
     */
    private static MethodHandle handle(MethodHandles.Lookup lookup, Class<?> cls, Class<?> ret,
                                       Class<?>[] params, String... names) {
        for (String name : names) {
            Method m = null;
            for (Class<?> c = cls; c != null && c != Object.class && m == null; c = c.getSuperclass()) {
                try {
                    m = c.getDeclaredMethod(name, params);
                } catch (NoSuchMethodException ignored) {
                    // walk up
                }
            }
            if (m == null) {
                try {
                    m = cls.getMethod(name, params);
                } catch (NoSuchMethodException ignored) {
                    continue;
                }
            }
            if (ret != null && !ret.isAssignableFrom(m.getReturnType())) {
                continue;
            }
            try {
                m.setAccessible(true);
                MethodHandle h = lookup.unreflect(m);
                Class<?> outRet = ret == null ? void.class : ret;
                Class<?>[] outParams = new Class<?>[params.length + 1];
                outParams[0] = Object.class;
                System.arraycopy(params, 0, outParams, 1, params.length);
                return h.asType(MethodType.methodType(outRet, outParams));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // inaccessible or incompatible: try the next name
            }
        }
        return null;
    }
}
