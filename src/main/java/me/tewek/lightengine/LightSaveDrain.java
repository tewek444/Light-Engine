package me.tewek.lightengine;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Wait until the block-light engine has no pending work before the server
 * serializes chunks.
 *
 * <p>Why: light propagation (especially extended halos) runs asynchronously.
 * If chunks are saved mid-flood after a source was removed, the persisted
 * halo has no source anymore; on load nothing re-queues work for it, and a
 * vanilla 15-plateau can no longer be cleared by a 15-wave, so it stays
 * forever and is re-saved on every quit (the stale-light report). Draining
 * first makes the save observe settled light.</p>
 */
public final class LightSaveDrain {
    private LightSaveDrain() {
    }

    /** Max time to wait for light to settle before a save. */
    private static final long TIMEOUT_MS = 15_000;
    /** Sleeps between polls. */
    private static final long POLL_MS = 50;
    /** Consecutive calm polls required. */
    private static final int CALM_REQUIRED = 3;
    /**
     * Quiet period after the last queued light check: {@code hasLightWork()}
     * cannot see work still sitting in the light thread mailbox, so recent
     * block changes force a wait first.
     */
    private static final long QUIET_NANOS = 1_500_000_000L;

    public static void drainBeforeSave(MinecraftServer server) {
        if (server == null) {
            return;
        }
        long start = System.currentTimeMillis();
        long deadline = start + TIMEOUT_MS;
        int calm = 0;
        boolean waited = false;
        while (System.currentTimeMillis() < deadline) {
            boolean busy = LightCheckStamp.nanosSinceLastCheck() < QUIET_NANOS;
            if (!busy) {
                try {
                    for (ServerLevel level : server.getAllLevels()) {
                        if (level == null) {
                            continue;
                        }
                        try {
                            if (level.getLightEngine().hasLightWork()) {
                                busy = true;
                                break;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            if (!busy) {
                calm++;
                if (calm >= CALM_REQUIRED) {
                    break;
                }
            } else {
                calm = 0;
                waited = true;
            }
            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        long elapsed = System.currentTimeMillis() - start;
        if (waited || elapsed > POLL_MS * CALM_REQUIRED + 20) {
            LightEngine.LOGGER.info("LightEngine pre-save light drain settled in {}ms", elapsed);
        }
        if (System.currentTimeMillis() >= deadline) {
            LightEngine.LOGGER.warn("LightEngine pre-save light drain timed out, saving anyway");
        }
    }
}
