package me.tewek.lightengine.lightfield;

import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-level v2 luminance storage: one {@link LuminanceSection} per non-empty
 * 16x16x16 section, keyed by {@link SectionPos#asLong}. Empty sections are
 * never kept (a zeroed cell drops its section).
 *
 * <p>Chunk NBT layout (key {@code lightengine:light_field_v2}): a list of
 * {@code {s: int sectionY, blob: byte[] zlib}} entries. Legacy v1 tags are
 * never read here — no migration, by decision (see STORAGE_V2_PLAN.md).</p>
 */
public final class LightFieldStore {
    public static final String NBT_KEY = "lightengine:light_field_v2";

    private static final Map<Level, LightFieldStore> BY_LEVEL = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Long, LuminanceSection> sections = new ConcurrentHashMap<>();

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

    public int getCell(long sectionPos, int x, int y, int z) {
        LuminanceSection section = sections.get(sectionPos);
        return section == null ? 0 : section.get(x, y, z);
    }

    public void setCell(long sectionPos, int x, int y, int z, int level) {
        int clamped = Math.clamp(level, 0, LuminanceSection.MAX_LEVEL);
        if (clamped <= 0) {
            LuminanceSection section = sections.get(sectionPos);
            if (section == null) {
                return;
            }
            section.set(x, y, z, 0);
            if (section.isEmpty()) {
                sections.remove(sectionPos, section);
            }
            return;
        }
        sections.computeIfAbsent(sectionPos, k -> new LuminanceSection()).set(x, y, z, clamped);
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
        for (Map.Entry<Long, LuminanceSection> e : sections.entrySet()) {
            long pos = e.getKey();
            if (SectionPos.x(pos) != chunkX || SectionPos.z(pos) != chunkZ) {
                continue;
            }
            int sy = SectionPos.y(pos);
            if (sy < minSection || sy > maxSection) {
                continue;
            }
            LuminanceSection section = e.getValue();
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
            LuminanceSection section = LuminanceSection.inflate(blob.blob());
            if (section == null) {
                continue;
            }
            sections.put(SectionPos.asLong(chunkX, blob.y(), chunkZ), section);
        }
    }

    public void writeTag(CompoundTag tag, int chunkX, int chunkZ, int minSection, int maxSection) {
        List<SectionBlob> blobs = encodeChunk(chunkX, chunkZ, minSection, maxSection);
        if (blobs == null) {
            return;
        }
        ListTag list = new ListTag();
        for (SectionBlob blob : blobs) {
            CompoundTag c = new CompoundTag();
            c.putInt("s", blob.y());
            c.putByteArray("blob", blob.blob());
            list.add(c);
        }
        tag.put(NBT_KEY, list);
    }

    public void readTag(CompoundTag tag, int chunkX, int chunkZ) {
        if (tag == null) {
            return;
        }
        ListTag list = tag.getListOrEmpty(NBT_KEY);
        if (list.size() == 0) {
            return;
        }
        List<SectionBlob> blobs = new ArrayList<>(list.size());
        for (Tag t : list) {
            if (!(t instanceof CompoundTag c)) {
                continue;
            }
            Optional<Integer> sOpt = c.getInt("s");
            if (sOpt.isEmpty()) {
                continue;
            }
            Optional<byte[]> blobOpt = c.getByteArray("blob");
            if (blobOpt.isEmpty()) {
                continue;
            }
            byte[] blob = blobOpt.get();
            if (blob.length == 0) {
                continue;
            }
            blobs.add(new SectionBlob(sOpt.get(), blob));
        }
        if (!blobs.isEmpty()) {
            decodeChunk(chunkX, chunkZ, blobs);
        }
    }
}
