package me.tewek.lightengine.lightfield;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Facade over the v2 light field. Mixins talk only to this facade, never to
 * the store directly.
 *
 * <p>Legacy {@code lightengine:extended_light} tags from pre-v2 worlds are
 * ignored on read and removed on write (gradual on-disk cleanup, no
 * migration).</p>
 */
public final class LightField {
    /** NBT key of the retired v1 format; kept as a literal for disk cleanup. */
    private static final String LEGACY_NBT_KEY = "lightengine:extended_light";

    private LightField() {
    }

    public static int readCell(Level level, long sectionPos, int x, int y, int z) {
        if (level == null) {
            return 0;
        }
        return LightFieldStore.forLevel(level).getCell(sectionPos, x, y, z);
    }

    public static void writeCell(Level level, long sectionPos, int x, int y, int z, int lightLevel) {
        if (level == null) {
            return;
        }
        LightFieldStore.forLevel(level).setCell(sectionPos, x, y, z, lightLevel);
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
        tag.remove(LEGACY_NBT_KEY);
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
