package me.tewek.lightengine.lightfield;

import me.tewek.lightengine.LightEngine;
import me.tewek.lightengine.LightProfileRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Colored-light field over a cheap emitter map (Alpha5).
 *
 * <p>The vanilla {@code BlockLightEngine} stays the SOLE propagator of
 * brightness (async light thread, vanilla occlusion, no freeze). This class
 * tracks WHERE colored emitters are and answers "which hue reaches this
 * point" at any continuous position — mesh hooks ask per vertex, so hue
 * gradients are as smooth as vanilla smooth lighting instead of stepping
 * block by block.</p>
 *
 * <p>Hue model: every emitter in range contributes its color in linear light,
 * weighted by its perceived intensity {@code (level/15)^2}, where
 * {@code level = radius - manhattan(point, emitter center)} (the same diamond
 * vanilla propagates). The intensity is capped by the real (extended)
 * block-light level of the lit cell, so light that a wall stopped cannot tint
 * past it. {@code frac} reports how much of the local block light is colored:
 * when an uncolored source (a vanilla torch) out-shines the colored ones, the
 * vanilla warm ramp keeps its share.</p>
 */
public final class ChromaTint {
    private ChromaTint() {
    }

    /** Emitter with its color pre-converted to linear light. */
    public record Emitter(Identifier id, int radius, int color, float lr, float lg, float lb) {
        static Emitter of(Identifier id, int radius, int color) {
            return new Emitter(id, radius, color,
                    srgbToLinear((color >> 16) & 0xFF), srgbToLinear((color >> 8) & 0xFF),
                    srgbToLinear(color & 0xFF));
        }
    }

    /**
     * A newly registered (or removed) emitter placement. Chunk meshes bake
     * hue, so callers refresh the halo area around it.
     */
    public record Added(BlockPos pos, int radius) {
    }

    /** Per-vertex result of {@link #sampleVertices}. */
    public static final class Sample {
        /** Hue per vertex, display space, max channel 1: {@code [i*3 + c]}. */
        public final float[] hue = new float[12];
        /** Colored share of the block light per vertex, 0..1. */
        public final float[] frac = new float[4];
    }

    private static final float HUE_MARGIN = 2.0F;

    /** Set once a client-refresh attempt proves the client classes missing. */
    private static volatile boolean haloUnsupported = false;

    /** Level key -> chunk key -> block pos -> emitter. */
    private static final Map<Object, ConcurrentHashMap<Long, ConcurrentHashMap<Long, Emitter>>> BY_LEVEL =
            new ConcurrentHashMap<>();
    /** Level key -> scanned chunk keys (lazy scan backstop). */
    private static final Map<Object, Set<Long>> SCANNED = new ConcurrentHashMap<>();
    private static final float[] SRGB_TO_LINEAR = new float[256];

    static {
        for (int i = 0; i < SRGB_TO_LINEAR.length; i++) {
            double value = i / 255.0;
            SRGB_TO_LINEAR[i] = (float) (value <= 0.04045
                    ? value / 12.92
                    : Math.pow((value + 0.055) / 1.055, 2.4));
        }
    }

    /** Candidate emitters around the last queried section, per meshing thread. */
    private static final class Candidates {
        Object levelKey;
        long sectionKey = Long.MIN_VALUE;
        long stamp = -1L;
        int count;
        long[] pos = new long[16];
        float[] ex = new float[16];
        float[] ey = new float[16];
        float[] ez = new float[16];
        int[] radius = new int[16];
        float[] lr = new float[16];
        float[] lg = new float[16];
        float[] lb = new float[16];

        void clear() {
            count = 0;
        }

        void add(long pos, Emitter em) {
            if (count == ex.length) {
                int n = count * 2;
                this.pos = java.util.Arrays.copyOf(this.pos, n);
                ex =java.util.Arrays.copyOf(ex, n);
                ey = java.util.Arrays.copyOf(ey, n);
                ez = java.util.Arrays.copyOf(ez, n);
                radius = java.util.Arrays.copyOf(radius, n);
                lr = java.util.Arrays.copyOf(lr, n);
                lg = java.util.Arrays.copyOf(lg, n);
                lb = java.util.Arrays.copyOf(lb, n);
            }
            this.pos[count] = pos;
            ex[count] = BlockPos.getX(pos) + 0.5F;
            ey[count] = BlockPos.getY(pos) + 0.5F;
            ez[count] = BlockPos.getZ(pos) + 0.5F;
            radius[count] = em.radius();
            lr[count] = em.lr();
            lg[count] = em.lg();
            lb[count] = em.lb();
            count++;
        }
    }

    private static final ThreadLocal<Candidates> CANDIDATES = ThreadLocal.withInitial(Candidates::new);
    /** Bumped on every map change so cached candidate lists never go stale. */
    private static final java.util.concurrent.atomic.AtomicLong STAMP = new java.util.concurrent.atomic.AtomicLong();

    private static ConcurrentHashMap<Long, ConcurrentHashMap<Long, Emitter>> chunksFor(Object key) {
        return BY_LEVEL.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
    }

    private static Set<Long> scannedFor(Object key) {
        return SCANNED.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());
    }

    public static void drop(Object levelKey) {
        if (levelKey != null) {
            BY_LEVEL.remove(levelKey);
            SCANNED.remove(levelKey);
            STAMP.incrementAndGet();
        }
    }

    public static void dropAll() {
        BY_LEVEL.clear();
        SCANNED.clear();
        STAMP.incrementAndGet();
    }

    // ----- maintenance (both sides call with their own Level) -----

    /** LevelChunk.setBlockState hook. O(1) map puts; never floods, never sends. */
    public static void onBlockChanged(Object levelKey, BlockPos pos, BlockState oldState, BlockState newState) {
        if (levelKey == null || pos == null || oldState == newState) {
            return;
        }
        if (!LightProfileRegistry.isEnabled()) {
            return;
        }
        int oldR = emissionOf(oldState);
        int newR = emissionOf(newState);
        int oldC = colorOf(oldState);
        int newC = colorOf(newState);
        boolean was = oldR > 0 && oldC >= 0;
        boolean is = newR > 0 && newC >= 0;
        if (was == is && (!is || (oldR == newR && oldC == newC))) {
            return;
        }
        ConcurrentHashMap<Long, ConcurrentHashMap<Long, Emitter>> chunks = chunksFor(levelKey);
        long chunkKey = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        long key = pos.asLong();
        if (was) {
            ConcurrentHashMap<Long, Emitter> cell = chunks.get(chunkKey);
            if (cell != null) {
                cell.remove(key);
                if (cell.isEmpty()) {
                    chunks.remove(chunkKey, cell);
                }
            }
        }
        if (is) {
            chunks.computeIfAbsent(chunkKey, k -> new ConcurrentHashMap<>())
                    .put(key, Emitter.of(idOf(newState), newR, newC));
        }
        STAMP.incrementAndGet();
        // Meshes bake hue: refresh the larger of the old/new halo so neither
        // stale color lingers nor a wide new halo stays white.
        scheduleHaloRefresh(levelKey, List.of(new Added(pos.immutable(), Math.max(was ? oldR : 0, is ? newR : 0))));
    }

    /**
     * Best-effort halo refresh on the client thread. Never throws; disables
     * itself permanently on a dedicated server, where meshing never queries.
     */
    private static void scheduleHaloRefresh(Object levelKey, List<Added> added) {
        if (haloUnsupported || levelKey == null || added == null || added.isEmpty()) {
            return;
        }
        if (!me.tewek.lightengine.platform.Platform.isClient()) {
            haloUnsupported = true;
            return;
        }
        try {
            me.tewek.lightengine.client.HaloRefresh.schedule(levelKey, added);
        } catch (Throwable t) {
            haloUnsupported = true;
            LightEngine.LOGGER.debug("Halo refresh unavailable", t);
        }
    }

    /** Chunk unload: drop the chunk's entries and its scanned mark. */
    public static void onChunkUnload(Object levelKey, ChunkPos chunk) {
        if (levelKey == null || chunk == null) {
            return;
        }
        ConcurrentHashMap<Long, ConcurrentHashMap<Long, Emitter>> chunks = BY_LEVEL.get(levelKey);
        if (chunks != null) {
            chunks.remove(ChunkPos.asLong(chunk.x, chunk.z));
        }
        Set<Long> scanned = SCANNED.get(levelKey);
        if (scanned != null) {
            scanned.remove(ChunkPos.asLong(chunk.x, chunk.z));
        }
        STAMP.incrementAndGet();
    }

    /**
     * Ensures one chunk column is scanned (idempotent, cached). Scans through
     * the real level — never through a rebuild region, which only sees a
     * section-wide window. Skips unloaded columns without forcing loads.
     *
     * @return emitters this scan newly registered (usually empty).
     */
    public static List<Added> ensureScanned(Object levelKey, BlockAndTintGetter accessor, int chunkX, int chunkZ) {
        if (levelKey == null || accessor == null) {
            return List.of();
        }
        if (!LightProfileRegistry.isEnabled() || LightProfileRegistry.isEmpty()) {
            return List.of();
        }
        long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
        if (!scannedFor(levelKey).add(chunkKey)) {
            return List.of();
        }
        Level scanLevel = levelKey instanceof Level l ? l : accessor instanceof Level l2 ? l2 : null;
        if (scanLevel != null && !scanLevel.hasChunk(chunkX, chunkZ)) {
            // Not loaded yet: forget the mark so a later query scans it.
            scannedFor(levelKey).remove(chunkKey);
            return List.of();
        }
        int minY;
        int maxY;
        try {
            BlockAndTintGetter h = scanLevel != null ? scanLevel : accessor;
            minY = h.getMinY();
            maxY = h.getMaxY();
        } catch (Exception ex) {
            return List.of();
        }
        List<Added> added = new ArrayList<>();
        ConcurrentHashMap<Long, ConcurrentHashMap<Long, Emitter>> chunks = chunksFor(levelKey);
        ConcurrentHashMap<Long, Emitter> cell = chunks.computeIfAbsent(chunkKey, k -> new ConcurrentHashMap<>());
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        for (int bx = baseX; bx < baseX + 16; bx++) {
            for (int bz = baseZ; bz < baseZ + 16; bz++) {
                for (int by = minY; by <= maxY; by++) {
                    BlockState state;
                    try {
                        mpos.set(bx, by, bz);
                        state = scanLevel != null ? scanLevel.getBlockState(mpos) : accessor.getBlockState(mpos);
                    } catch (Exception ex) {
                        continue;
                    }
                    if (state.isAir()) {
                        continue;
                    }
                    int r = emissionOf(state);
                    if (r <= 0) {
                        continue;
                    }
                    int c = colorOf(state);
                    if (c >= 0) {
                        long k = mpos.asLong();
                        Emitter prev = cell.put(k, Emitter.of(idOf(state), r, c));
                        if (prev == null) {
                            added.add(new Added(mpos.immutable(), r));
                        }
                    }
                }
            }
        }
        if (cell.isEmpty()) {
            chunks.remove(chunkKey, cell);
        } else {
            STAMP.incrementAndGet();
            LightEngine.LOGGER.debug("ChromaTint scanned chunk {},{}: {} emitter(s)", chunkX, chunkZ, cell.size());
        }
        return added;
    }

    /** Re-resolves every entry of one level (profile edits change meaning). */
    public static void rescanLevel(Object levelKey, BlockAndTintGetter accessor) {
        if (levelKey == null || accessor == null) {
            return;
        }
        // Profiles may have gained colors: chunks must be scanned again.
        Set<Long> scanned = SCANNED.get(levelKey);
        if (scanned != null) {
            scanned.clear();
        }
        ConcurrentHashMap<Long, ConcurrentHashMap<Long, Emitter>> chunks = BY_LEVEL.get(levelKey);
        if (chunks != null) {
            List<long[]> drop = new ArrayList<>();
            for (Map.Entry<Long, ConcurrentHashMap<Long, Emitter>> ce : chunks.entrySet()) {
                for (Map.Entry<Long, Emitter> e : ce.getValue().entrySet()) {
                    long key = e.getKey();
                    BlockState state;
                    try {
                        state = accessor.getBlockState(BlockPos.of(key));
                    } catch (Exception ex) {
                        continue;
                    }
                    int r = emissionOf(state);
                    int c = colorOf(state);
                    if (r > 0 && c >= 0) {
                        e.setValue(Emitter.of(idOf(state), r, c));
                    } else {
                        drop.add(new long[]{ce.getKey(), key});
                    }
                }
            }
            for (long[] d : drop) {
                ConcurrentHashMap<Long, Emitter> cell = chunks.get(d[0]);
                if (cell != null) {
                    cell.remove(d[1]);
                    if (cell.isEmpty()) {
                        chunks.remove(d[0], cell);
                    }
                }
            }
        }
        STAMP.incrementAndGet();
    }

    /** Block positions of live emitters with an id (for targeted mesh refresh). */
    public static List<BlockPos> positionsOf(Object levelKey, Identifier id) {
        List<BlockPos> out = new ArrayList<>();
        if (levelKey == null || id == null) {
            return out;
        }
        ConcurrentHashMap<Long, ConcurrentHashMap<Long, Emitter>> chunks = BY_LEVEL.get(levelKey);
        if (chunks == null) {
            return out;
        }
        for (ConcurrentHashMap<Long, Emitter> cell : chunks.values()) {
            for (Map.Entry<Long, Emitter> e : cell.entrySet()) {
                if (id.equals(e.getValue().id())) {
                    out.add(BlockPos.of(e.getKey()));
                }
            }
        }
        return out;
    }

    /** Every known emitter of a level with its radius (environment re-bake). */
    public static List<Added> allEmitters(Object levelKey) {
        List<Added> out = new ArrayList<>();
        ConcurrentHashMap<Long, ConcurrentHashMap<Long, Emitter>> chunks = levelKey == null ? null : BY_LEVEL.get(levelKey);
        if (chunks == null) {
            return out;
        }
        for (ConcurrentHashMap<Long, Emitter> cell : chunks.values()) {
            for (Map.Entry<Long, Emitter> e : cell.entrySet()) {
                out.add(new Added(BlockPos.of(e.getKey()), e.getValue().radius()));
            }
        }
        return out;
    }

    public static boolean hasEmitters(Object levelKey) {
        ConcurrentHashMap<Long, ConcurrentHashMap<Long, Emitter>> chunks = levelKey == null ? null : BY_LEVEL.get(levelKey);
        return chunks != null && !chunks.isEmpty();
    }

    // ----- query -----

    /**
     * Hue reaching the center of a cell: packed {@code 0xRRGGBB} (max channel
     * 255), or -1 when no colored light reaches it. Used by the overlay and
     * the public API; meshing uses {@link #sampleVertices}.
     */
    public static int forSurface(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        if (level == null || pos == null) {
            return -1;
        }
        float[] px = {pos.getX() + 0.5F, 0, 0, 0};
        float[] py = {pos.getY() + 0.5F, 0, 0, 0};
        float[] pz = {pos.getZ() + 0.5F, 0, 0, 0};
        Sample s = new Sample();
        if (!sampleVertices(level, null, pos, px, py, pz, 1, s) || s.frac[0] <= 0.0F) {
            return -1;
        }
        int r = Math.round(s.hue[0] * 255.0F);
        int g = Math.round(s.hue[1] * 255.0F);
        int b = Math.round(s.hue[2] * 255.0F);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * Hue + colored share at up to four world-space points lit by one cell.
     *
     * @param ownPos   the block being meshed; an emitter never tints its own
     *                 faces (they glow with their texture), may be null
     * @param lightPos the cell whose real light level bounds the result
     * @param count    number of points (1..4)
     * @return false when no colored light reaches any point
     */
    public static boolean sampleVertices(BlockAndTintGetter level, BlockPos ownPos, BlockPos lightPos,
                                         float[] px, float[] py, float[] pz, int count, Sample out) {
        if (!LightProfileRegistry.isEnabled() || LightProfileRegistry.isEmpty()) {
            return false;
        }
        Object key = levelKeyOf(level);
        if (key == null) {
            return false;
        }
        Candidates cand = candidatesAround(key, level, lightPos);
        if (cand.count == 0) {
            return false;
        }
        int actual;
        try {
            actual = level.getBrightness(LightLayer.BLOCK, lightPos);
        } catch (Exception ex) {
            return false;
        }
        float cap = actual + 1.5F;
        long own = ownPos == null ? Long.MIN_VALUE : ownPos.asLong();
        boolean any = false;
        for (int v = 0; v < count; v++) {
            float r = 0.0F;
            float g = 0.0F;
            float b = 0.0F;
            float sumW = 0.0F;
            float maxEff = 0.0F;
            for (int i = 0; i < cand.count; i++) {
                if (cand.pos[i] == own) {
                    continue;
                }
                float d = Math.abs(px[v] - cand.ex[i]) + Math.abs(py[v] - cand.ey[i]) + Math.abs(pz[v] - cand.ez[i]);
                // +2: smooth lighting keeps the outermost vertices lit a
                // little past the cell-center diamond; without the margin the
                // hue ends a ring early and the rim stair-steps to vanilla.
                float lvl = cand.radius[i] + HUE_MARGIN - d;
                if (lvl <= 0.0F) {
                    continue;
                }
                float eff = Math.min(lvl, cap);
                float w = eff * eff;
                r += cand.lr[i] * w;
                g += cand.lg[i] * w;
                b += cand.lb[i] * w;
                sumW += w;
                if (eff > maxEff) {
                    maxEff = eff;
                }
            }
            int o = v * 3;
            if (sumW <= 0.0F) {
                out.hue[o] = 1.0F;
                out.hue[o + 1] = 1.0F;
                out.hue[o + 2] = 1.0F;
                out.frac[v] = 0.0F;
                continue;
            }
            float hr = linearToSrgb(r / sumW);
            float hg = linearToSrgb(g / sumW);
            float hb = linearToSrgb(b / sumW);
            float peak = Math.max(hr, Math.max(hg, hb));
            if (peak <= 1e-4F) {
                out.frac[v] = 0.0F;
                continue;
            }
            out.hue[o] = hr / peak;
            out.hue[o + 1] = hg / peak;
            out.hue[o + 2] = hb / peak;
            // Brighter uncolored light (vanilla torch) keeps its share.
            float frac = actual <= 0 ? 1.0F : Math.min(1.0F, (maxEff + 1.5F) / actual);
            out.frac[v] = frac;
            any = true;
        }
        return any;
    }

    /**
     * Gathers emitters that may reach any point of {@code center}'s 16^3
     * section into the thread's reusable list: one gather per section and
     * map version, then every quad of the section only does distance math.
     */
    private static Candidates candidatesAround(Object key, BlockAndTintGetter level, BlockPos center) {
        Candidates cand = CANDIDATES.get();
        int sx = center.getX() >> 4;
        int sy = center.getY() >> 4;
        int sz = center.getZ() >> 4;
        long sectionKey = net.minecraft.core.SectionPos.asLong(sx, sy, sz);
        // Lazy backstop: the section's own column is scanned on first touch;
        // neighbors scan when they mesh and refresh their halos then.
        List<Added> added = ensureScanned(key, level, sx, sz);
        if (!added.isEmpty()) {
            scheduleHaloRefresh(key, added);
        }
        long stamp = STAMP.get();
        if (cand.levelKey == key && cand.sectionKey == sectionKey && cand.stamp == stamp) {
            return cand;
        }
        cand.clear();
        cand.levelKey = key;
        cand.sectionKey = sectionKey;
        cand.stamp = stamp;
        int minX = sx << 4;
        int minY = sy << 4;
        int minZ = sz << 4;
        int maxX = minX + 15;
        int maxY = minY + 15;
        int maxZ = minZ + 15;
        int reach = LightProfileRegistry.EFFECTIVE_MAX_RADIUS + 3;
        int minCX = (minX - reach) >> 4;
        int maxCX = (maxX + reach) >> 4;
        int minCZ = (minZ - reach) >> 4;
        int maxCZ = (maxZ + reach) >> 4;
        ConcurrentHashMap<Long, ConcurrentHashMap<Long, Emitter>> chunks = BY_LEVEL.get(key);
        if (chunks == null || chunks.isEmpty()) {
            return cand;
        }
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                ConcurrentHashMap<Long, Emitter> cell = chunks.get(ChunkPos.asLong(cx, cz));
                if (cell == null || cell.isEmpty()) {
                    continue;
                }
                for (Map.Entry<Long, Emitter> e : cell.entrySet()) {
                    Emitter em = e.getValue();
                    if (em == null || em.radius() <= 0) {
                        continue;
                    }
                    long k = e.getKey();
                    int d = boxDist(BlockPos.getX(k), minX, maxX)
                            + boxDist(BlockPos.getY(k), minY, maxY)
                            + boxDist(BlockPos.getZ(k), minZ, maxZ);
                    // +3: sample points sit up to ~1.5 blocks outside the box.
                    if (d < em.radius() + 5) {
                        cand.add(k, em);
                    }
                }
            }
        }
        return cand;
    }

    private static int boxDist(int v, int min, int max) {
        return v < min ? min - v : v > max ? v - max : 0;
    }

    static float srgbToLinear(int channel) {
        return SRGB_TO_LINEAR[channel & 0xFF];
    }

    static float linearToSrgb(float value) {
        float c = Math.max(0.0F, Math.min(1.0F, value));
        return c <= 0.0031308F ? c * 12.92F : (float) (1.055 * Math.pow(c, 1.0 / 2.4) - 0.055);
    }

    /** Level key: Levels directly, anything else (mesh regions) via the client world. */
    private static Object levelKeyOf(BlockAndTintGetter accessor) {
        if (accessor instanceof Level level) {
            return level;
        }
        if (!me.tewek.lightengine.platform.Platform.isClient()) {
            return null;
        }
        return ClientLevelKey.get();
    }

    /** Isolated so the common class never links client classes on a server. */
    private static final class ClientLevelKey {
        static Object get() {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            return mc == null ? null : mc.level;
        }
    }

    private static int emissionOf(BlockState state) {
        if (state == null) {
            return 0;
        }
        try {
            return Math.min(state.getLightEmission(), LightProfileRegistry.EFFECTIVE_MAX_RADIUS);
        } catch (Exception ex) {
            return 0;
        }
    }

    private static int colorOf(BlockState state) {
        if (state == null) {
            return -1;
        }
        try {
            return LightProfileRegistry.colorForState(state);
        } catch (Exception ex) {
            return -1;
        }
    }

    private static Identifier idOf(BlockState state) {
        Identifier id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id == null ? Identifier.withDefaultNamespace("air") : id;
    }
}
