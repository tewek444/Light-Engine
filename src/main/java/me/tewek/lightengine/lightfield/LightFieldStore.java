package me.tewek.lightengine.lightfield;

import net.minecraft.core.SectionPos;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-level v3 chroma storage: one {@link ChromaSection} per non-empty
 * 16x16x16 section, keyed by {@link SectionPos#asLong}. Empty sections are
 * never kept (a zeroed cell drops its section).
 *
 * <p>Chunk NBT layout (key {@code lightengine:light_field_v3}): a list of
 * {@code {s: int sectionY, blob: byte[] zlib}} entries, each blob a
 * {@code 4096 * 3} RGB block in Morton order. Legacy v1/v2 tags are never
 * read — no migration, by decision (Alpha4 wipe) — and are removed on write.</p>
 */
public final class LightFieldStore {
    public static final String NBT_KEY = "lightengine:light_field_v3";
    /** Retired keys, removed on write for gradual on-disk cleanup. */
    private static final String LEGACY_V2 = "lightengine:light_field_v2";
    private static final String LEGACY_V1 = "lightengine:extended_light";

    private static final Map<Level, LightFieldStore> BY_LEVEL = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Long, ChromaSection> sections = new ConcurrentHashMap<>();

    private LightFieldStore() {
    }

    public static LightFieldStore forLevel(Level level) {
        return BY_LEVEL.computeIfAbsent(level, l -> new LightFieldStore());
    }

    public static void remove(Level level) {
        BY_LEVEL.remove(level);
    }

    public static void clearAll() {
        BY_LEVEL.clear();
    }

    /** Logical level of a cell: max(R, G, B), 0-255. */
    public int getCell(long sectionPos, int x, int y, int z) {
        ChromaSection section = sections.get(sectionPos);
        return section == null ? 0 : section.getLevel(x, y, z);
    }

    /** Packed 0xRRGGBB of a cell, 0 when empty. */
    public int getColor(long sectionPos, int x, int y, int z) {
        ChromaSection section = sections.get(sectionPos);
        return section == null ? 0 : section.getPacked(x, y, z);
    }

    public void setCell(long sectionPos, int x, int y, int z, int level) {
        int clamped = Math.clamp(level, 0, ChromaSection.MAX_CHANNEL);
        if (clamped <= 0) {
            ChromaSection section = sections.get(sectionPos);
            if (section == null) {
                return;
            }
            section.setRGB(x, y, z, 0, 0, 0);
            if (section.isEmpty()) {
                sections.remove(sectionPos, section);
            }
            return;
        }
        sections.computeIfAbsent(sectionPos, k -> new ChromaSection()).setLevel(x, y, z, clamped);
    }

    public void setCellRGB(long sectionPos, int x, int y, int z, int r, int g, int b) {
        if ((r | g | b) <= 0) {
            setCell(sectionPos, x, y, z, 0);
            return;
        }
        sections.computeIfAbsent(sectionPos, k -> new ChromaSection()).setRGB(x, y, z, r, g, b);
    }

    /**
     * Render-only view of live sections (no copy). Callers must only read;
     * entries may vanish concurrently, values are benign-race byte reads.
     */
    public ConcurrentHashMap<Long, ChromaSection> sections() {
        return sections;
    }

    /** Zeroes every cell in a block box; drops sections that go empty. */
    public void clearBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (int sx = minX >> 4; sx <= maxX >> 4; sx++) {
            for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
                for (int sz = minZ >> 4; sz <= maxZ >> 4; sz++) {
                    long key = SectionPos.asLong(sx, sy, sz);
                    ChromaSection section = sections.get(key);
                    if (section == null) {
                        continue;
                    }
                    int lx0 = sx == minX >> 4 ? minX & 15 : 0;
                    int lx1 = sx == maxX >> 4 ? maxX & 15 : 15;
                    int ly0 = sy == minY >> 4 ? minY & 15 : 0;
                    int ly1 = sy == maxY >> 4 ? maxY & 15 : 15;
                    int lz0 = sz == minZ >> 4 ? minZ & 15 : 0;
                    int lz1 = sz == maxZ >> 4 ? maxZ & 15 : 15;
                    for (int x = lx0; x <= lx1; x++) {
                        for (int y = ly0; y <= ly1; y++) {
                            for (int z = lz0; z <= lz1; z++) {
                                section.setRGB(x, y, z, 0, 0, 0);
                            }
                        }
                    }
                    if (section.isEmpty()) {
                        sections.remove(key, section);
                    }
                }
            }
        }
    }

    /** Server chunks unloaded but maybe not saved yet: packed chunk key -> unload time (nanos). */
    private final ConcurrentHashMap<Long, Long> pendingEvict = new ConcurrentHashMap<>();
    private static final long PENDING_EVICT_NANOS = 5_000_000_000L;

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    /**
     * Chunk unload. The unload event fires before the chunk is serialized, so a
     * server chunk only gets marked here and is evicted by {@link #onChunkSaved}
     * once its field is written. Marks that never see a save (unchanged chunks)
     * are swept on later unloads. Client chunks are evicted at once.
     */
    public void evictAfterSave(Level level, int chunkX, int chunkZ) {
        if (level == null || level.isClientSide()) {
            evictChunk(chunkX, chunkZ);
            return;
        }
        long now = System.nanoTime();
        pendingEvict.entrySet().removeIf(e -> {
            if (now - e.getValue() < PENDING_EVICT_NANOS) {
                return false;
            }
            int x = (int) (e.getKey() >> 32);
            int z = (int) (long) e.getKey();
            if (!level.hasChunk(x, z)) {
                evictChunk(x, z);
            }
            return true;
        });
        pendingEvict.put(chunkKey(chunkX, chunkZ), now);
    }

    /** The chunk is being loaded again: its field must not be evicted by an old unload mark. */
    public void onChunkLoaded(int chunkX, int chunkZ) {
        pendingEvict.remove(chunkKey(chunkX, chunkZ));
    }

    /** The chunk's field was just encoded for saving: evict it if it was unloaded. */
    public void onChunkSaved(int chunkX, int chunkZ) {
        if (pendingEvict.remove(chunkKey(chunkX, chunkZ)) != null) {
            evictChunk(chunkX, chunkZ);
        }
    }

    public void evictChunk(int chunkX, int chunkZ) {
        sections.keySet().removeIf(pos -> SectionPos.x(pos) == chunkX && SectionPos.z(pos) == chunkZ);
    }

    public void clear() {
        sections.clear();
    }

    public boolean isEmpty() {
        return sections.isEmpty();
    }

    /**
     * Compressed section blobs of one chunk; {@code null} when there is
     * nothing to persist or send.
     */
    public List<SectionBlob> encodeChunk(int chunkX, int chunkZ, int minSection, int maxSection) {
        List<SectionBlob> out = null;
        for (Map.Entry<Long, ChromaSection> e : sections.entrySet()) {
            long pos = e.getKey();
            if (SectionPos.x(pos) != chunkX || SectionPos.z(pos) != chunkZ) {
                continue;
            }
            int sy = SectionPos.y(pos);
            if (sy < minSection || sy > maxSection) {
                continue;
            }
            ChromaSection section = e.getValue();
            if (section == null || section.isEmpty()) {
                continue;
            }
            if (out == null) {
                out = new ArrayList<>();
            }
            out.add(new SectionBlob(sy, section.deflate()));
        }
        return out == null || out.isEmpty() ? null : out;
    }

    /** Merges blobs back; corrupt entries are skipped. */
    public void decodeChunk(int chunkX, int chunkZ, List<SectionBlob> blobs) {
        if (blobs == null) {
            return;
        }
        for (SectionBlob blob : blobs) {
            if (blob == null || blob.blob() == null) {
                continue;
            }
            ChromaSection section = ChromaSection.inflate(blob.blob());
            if (section == null) {
                continue;
            }
            sections.put(SectionPos.asLong(chunkX, blob.y(), chunkZ), section);
        }
    }

    public void writeTag(CompoundTag tag, int chunkX, int chunkZ, int minSection, int maxSection) {
        List<SectionBlob> blobs = encodeChunk(chunkX, chunkZ, minSection, maxSection);
        if (blobs == null) {
            tag.remove(NBT_KEY);
            return;
        }
        ListTag list = new ListTag();
        for (SectionBlob blob : blobs) {
            CompoundTag c = new CompoundTag();
            c.putInt("s", blob.y());
            c.put("blob", new ByteArrayTag(blob.blob()));
            list.add(c);
        }
        tag.put(NBT_KEY, list);
    }

    public void readTag(CompoundTag tag, int chunkX, int chunkZ) {
        if (tag == null || !tag.contains(NBT_KEY)) {
            return;
        }
        ListTag list = tag.getList(NBT_KEY).orElse(null);
        if (list == null) {
            return;
        }
        List<SectionBlob> blobs = new ArrayList<>(list.size());
        for (Tag t : list) {
            if (!(t instanceof CompoundTag c)) {
                continue;
            }
            if (!c.contains("s") || !c.contains("blob")) {
                continue;
            }
            byte[] blob = c.getByteArray("blob").orElse(null);
            if (blob == null || blob.length == 0) {
                continue;
            }
            blobs.add(new SectionBlob(c.getIntOr("s", 0), blob));
        }
        if (!blobs.isEmpty()) {
            decodeChunk(chunkX, chunkZ, blobs);
        }
    }
}
