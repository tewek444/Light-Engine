package me.tewek.lightengine;

/**
 * Remembers the last time any block-light check was queued, so the pre-save
 * drain can tell "engine idle" apart from "work still sitting in the light
 * thread mailbox" (which {@code hasLightWork()} cannot see yet).
 */
public final class LightCheckStamp {
    private LightCheckStamp() {
    }

    private static volatile long lastCheckNanos;

    public static void touch() {
        lastCheckNanos = System.nanoTime();
    }

    public static long nanosSinceLastCheck() {
        return System.nanoTime() - lastCheckNanos;
    }
}
