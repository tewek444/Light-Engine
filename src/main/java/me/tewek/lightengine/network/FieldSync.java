package me.tewek.lightengine.network;

import me.tewek.lightengine.LightProfileRegistry;
import me.tewek.lightengine.lightfield.LightField;
import me.tewek.lightengine.lightfield.LightSectionRange;
import me.tewek.lightengine.lightfield.SectionBlob;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live field sync (server -&gt; tracking clients).
 *
 * <p>Why it exists: the client's extended field fills only from our payloads
 * (vanilla packets carry 4-bit nibbles — extended levels physically cannot
 * ride them). Chunk-send covers joins/teleports; this covers EDITS. Without
 * it the mesh tint's brightness gate never fires on live changes and hue
 * edits stay invisible until the next edit/relog.</p>
 *
 * <p>Debounced by design: edits only mark overlapped chunks dirty, the flush
 * runs every few server ticks (the async vanilla wave it mirrors needs a
 * moment to settle anyway). Payloads are data-or-clear, so removed sources
 * evict client halos instead of leaving them stale. Bounded by
 * {@code EFFECTIVE_MAX_RADIUS} — the same bound as propagation.</p>
 */
public final class FieldSync {
    private FieldSync() {
    }

    private static final int FLUSH_EVERY_TICKS = 4;

    private static final Map<ServerLevel, Set<Long>> DIRTY = new ConcurrentHashMap<>();
    private static int tickCounter;

    /** LevelChunk.setBlockState path (server only). Cheap: a few set adds. */
    public static void markDirty(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server) || pos == null) {
            return;
        }
        if (!LightProfileRegistry.isEnabled()) {
            return;
        }
        int r = LightProfileRegistry.EFFECTIVE_MAX_RADIUS;
        int minCX = (pos.getX() - r) >> 4;
        int maxCX = (pos.getX() + r) >> 4;
        int minCZ = (pos.getZ() - r) >> 4;
        int maxCZ = (pos.getZ() + r) >> 4;
        Set<Long> set = DIRTY.computeIfAbsent(server, s -> ConcurrentHashMap.newKeySet());
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                set.add(ChunkPos.pack(cx, cz));
                if (set.size() > 4096) {
                    return; // degenerate storm protection (TNT + huge radii)
                }
            }
        }
    }

    /** ServerTickEvent.END path. */
    public static void tick() {
        if (DIRTY.isEmpty()) {
            return;
        }
        if ((++tickCounter % FLUSH_EVERY_TICKS) != 0) {
            return;
        }
        for (Map.Entry<ServerLevel, Set<Long>> e : DIRTY.entrySet()) {
            ServerLevel level = e.getKey();
            Set<Long> chunks = e.getValue();
            if (level == null || chunks == null || chunks.isEmpty()) {
                continue;
            }
            Set<Long> batch;
            synchronized (chunks) {
                if (chunks.isEmpty()) {
                    continue;
                }
                batch = new HashSet<>(chunks);
                chunks.clear();
            }
            flushLevel(level, batch);
        }
    }

    private static void flushLevel(ServerLevel level, Set<Long> batch) {
        int minS;
        int maxS;
        try {
            minS = LightSectionRange.minSection(level);
            maxS = LightSectionRange.maxSection(level);
        } catch (Exception ignored) {
            return;
        }
        for (long key : batch) {
            int cx = ChunkPos.getX(key);
            int cz = ChunkPos.getZ(key);
            List<SectionBlob> sections;
            try {
                sections = LightField.encodeChunk(level, cx, cz, minS, maxS);
            } catch (Exception ignored) {
                continue;
            }
            LightFieldPayload payload = sections == null
                    ? LightFieldPayload.clear(cx, cz)
                    : LightFieldPayload.of(cx, cz, sections);
            if (payload == null) {
                continue;
            }
            try {
                me.tewek.lightengine.platform.Platform.sendToTrackingChunk(level, new ChunkPos(cx, cz), payload);
            } catch (Throwable ignored) {
            }
        }
    }
}
