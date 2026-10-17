package me.tewek.lightengine.lightfield;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Facade over the v3 chroma field. Mixins talk only to this facade, never to
 * the store directly.
 *
 * <p>Legacy {@code lightengine:extended_light} (v1) and
 * {@code lightengine:light_field_v2} (v2) tags from older worlds are ignored
 * on read and removed on write (gradual on-disk cleanup, no migration —
 * Alpha4 wipe, by decision).</p>
 */
public final class LightField {
    /** NBT keys of the retired formats; kept as literals for disk cleanup. */
    private static final String LEGACY_V1 = "lightengine:extended_light";
    private static final String LEGACY_V2 = "lightengine:light_field_v2";

    private LightField() {
    }

    /** Logical level of a cell: max(R, G, B), 0-255. */
    public static int readCell(Level level, long sectionPos, int x, int y, int z) {
        if (level == null) {
            return 0;
        }
        return LightFieldStore.forLevel(level).getCell(sectionPos, x, y, z);
    }

    /** Packed 0xRRGGBB of a cell, 0 when empty. */
    public static int readColor(Level level, long sectionPos, int x, int y, int z) {
        if (level == null) {
            return 0;
        }
        return LightFieldStore.forLevel(level).getColor(sectionPos, x, y, z);
    }

    public static void writeCell(Level level, long sectionPos, int x, int y, int z, int lightLevel) {
        if (level == null) {
            return;
        }
        LightFieldStore.forLevel(level).setCell(sectionPos, x, y, z, lightLevel);
    }

    public static void writeCellRGB(Level level, long sectionPos, int x, int y, int z, int r, int g, int b) {
        if (level == null) {
            return;
        }
        LightFieldStore.forLevel(level).setCellRGB(sectionPos, x, y, z, r, g, b);
    }

    /** Compressed section blobs of one chunk; {@code null} when there is nothing to send. */
    public static List<SectionBlob> encodeChunk(Level level, int chunkX, int chunkZ,
                                                int minSection, int maxSection) {
        if (level == null) {
            return null;
        }
        return LightFieldStore.forLevel(level).encodeChunk(chunkX, chunkZ, minSection, maxSection);
    }

    public static void writeChunk(Level level, CompoundTag tag, int chunkX, int chunkZ,
                                  int minSection, int maxSection) {
        if (level == null || tag == null) {
            return;
        }
        LightFieldStore.forLevel(level).writeTag(tag, chunkX, chunkZ, minSection, maxSection);
        tag.remove(LEGACY_V1);
        tag.remove(LEGACY_V2);
    }

    public static void readChunk(Level level, CompoundTag tag, int chunkX, int chunkZ) {
        if (level == null || tag == null) {
            return;
        }
        LightFieldStore.forLevel(level).readTag(tag, chunkX, chunkZ);
    }

    public static void evictChunk(Level level, int chunkX, int chunkZ) {
        if (level == null) {
            return;
        }
        LightFieldStore.forLevel(level).evictChunk(chunkX, chunkZ);
    }

    public static void drop(Level level) {
        if (level == null) {
            return;
        }
        LightFieldStore.remove(level);
    }

    public static void dropAll() {
        LightFieldStore.clearAll();
    }
}
