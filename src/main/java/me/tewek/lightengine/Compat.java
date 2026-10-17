package me.tewek.lightengine;

import me.tewek.lightengine.platform.Platform;

import java.lang.reflect.Method;

/**
 * Optional-compat detection. Sodium/Iris are never required and never
 * touched by hard references: all integration goes through reflection or
 * soft mixins. While a shader pack is active the terrain hue is disabled
 * (the pack does its own lighting); logical levels keep working.
 */
public final class Compat {
    private Compat() {
    }

    private static volatile Boolean sodium;
    private static volatile Boolean iris;
    private static volatile Object irisApi;
    private static volatile Method irisInUse;
    private static volatile boolean irisResolved;

    public static boolean isSodiumLoaded() {
        Boolean v = sodium;
        if (v == null) {
            v = Platform.isModLoaded("sodium");
            sodium = v;
        }
        return v;
    }

    public static boolean isIrisLoaded() {
        Boolean v = iris;
        if (v == null) {
            v = Platform.isModLoaded("iris") || Platform.isModLoaded("oculus");
            iris = v;
        }
        return v;
    }

    /**
     * True when an Iris/Oculus shader pack is in use. Uses the stable public
     * API {@code net.irisshaders.iris.api.v0.IrisApi} (Iris 1.6+, Oculus for
     * 1.21). If the API cannot be resolved the answer is {@code false}: the
     * mod then tints as without shaders rather than silently never tinting.
     */
    public static boolean isShaderPipelineActive() {
        if (!isIrisLoaded()) {
            return false;
        }
        if (!irisResolved) {
            resolveIris();
        }
        Method m = irisInUse;
        if (m == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(m.invoke(irisApi));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    private static synchronized void resolveIris() {
        if (irisResolved) {
            return;
        }
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            irisInUse = api.getMethod("isShaderPackInUse");
            irisApi = instance;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            LightEngine.LOGGER.warn("Iris/Oculus is loaded but its API was not found; shader detection off", ex);
        }
        irisResolved = true;
    }

    public static String statusLine() {
        return "sodium=" + isSodiumLoaded() + " iris=" + isIrisLoaded()
                + " shaders=" + isShaderPipelineActive();
    }
}
