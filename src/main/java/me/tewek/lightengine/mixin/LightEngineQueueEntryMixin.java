package me.tewek.lightengine.mixin;

import net.minecraft.core.Direction;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Widens the packed queue-entry level field from vanilla 4 bits (0-15)
 * to 8 bits (0-255). All factory/query semantics are kept
 * bit-for-bit identical to vanilla, only the field positions move:
 *
 * <p>Vanilla layout: level bits 0-3, direction bits 4-9 (set = propagate),
 * from-empty bit 10, increase-from-emission bit 11.
 *
 * <p>Extended layout: level bits 0-7, direction bits 8-13 (set = propagate),
 * from-empty bit 14, increase-from-emission bit 15.
 *
 * <p>Note the vanilla quirks preserved here: {@code m_284188_}
 * and {@code m_284128_} do NOT set the increase flag, and
 * {@code m_284543_} sets direction bits for the
 * {@code true} inputs at level 15.
 */
@Mixin(LightEngine.QueueEntry.class)
public abstract class LightEngineQueueEntryMixin {
    private static final long LE_LEVEL_MASK = 0xFFL;
    private static final long LE_DIRECTIONS_MASK = 0x3FL << 8;
    private static final long LE_FLAG_FROM_EMPTY_SHAPE = 1L << 14;
    private static final long LE_FLAG_INCREASE_FROM_EMISSION = 1L << 15;

    private static long leBit(Direction direction) {
        return 1L << (direction.ordinal() + 8);
    }

    /**
     * @author LightEngine
     * @reason widen level field beyond vanilla 4 bits
     */
    @Overwrite(remap = false)
    public static int m_284170_(long entry) {
        return (int) (entry & LE_LEVEL_MASK);
    }

    /**
     * @author LightEngine
     * @reason widen level field beyond vanilla 4 bits
     */
    @Overwrite(remap = false)
    private static long m_284455_(long entry, int level) {
        return entry & ~LE_LEVEL_MASK | (long) level & LE_LEVEL_MASK;
    }

    /**
     * @author LightEngine
     * @reason widen level field beyond vanilla 4 bits
     */
    @Overwrite(remap = false)
    public static boolean m_284416_(long entry, Direction direction) {
        return (entry & leBit(direction)) != 0L;
    }

    /**
     * @author LightEngine
     * @reason widen level field beyond vanilla 4 bits
     */
    @Overwrite(remap = false)
    private static long m_284335_(long entry, Direction direction) {
        return entry | leBit(direction);
    }

    /**
     * @author LightEngine
     * @reason widen level field beyond vanilla 4 bits
     */
    @Overwrite(remap = false)
    private static long m_284441_(long entry, Direction direction) {
        return entry & ~leBit(direction);
    }

    /**
     * @author LightEngine
     * @reason widen level field beyond vanilla 4 bits
     */
    @Overwrite(remap = false)
    public static boolean m_284390_(long entry) {
        return (entry & LE_FLAG_FROM_EMPTY_SHAPE) != 0L;
    }

    /**
     * @author LightEngine
     * @reason widen level field beyond vanilla 4 bits
     */
    @Overwrite(remap = false)
    public static boolean m_284312_(long entry) {
        return (entry & LE_FLAG_INCREASE_FROM_EMISSION) != 0L;
    }

    /**
     * @author LightEngine
     * @reason widen level field beyond vanilla 4 bits
     */
    @Overwrite(remap = false)
    public static long m_284290_(int level) {
        return m_284455_(LE_DIRECTIONS_MASK, level);
    }

    /**
     * @author LightEngine
     * @reason widen level field beyond vanilla 4 bits
     */
    @Overwrite(remap = false)
    public static long m_284546_(int level, Direction direction) {
        long entry = m_284441_(LE_DIRECTIONS_MASK, direction);
        return m_284455_(entry, level);
    }

    /**
     * @author LightEngine
     * @reason widen level field beyond vanilla 4 bits
     */
    @Overwrite(remap = false)
    public static long m_284185_(int level, boolean fromEmptyShape) {
        long entry = LE_DIRECTIONS_MASK;
        entry |= LE_FLAG_INCREASE_FROM_EMISSION;
        if (fromEmptyShape) {
            entry |= LE_FLAG_FROM_EMPTY_SHAPE;
        }
        return m_284455_(entry, level);
    }

    /**
     * @author LightEngine
     * @reason widen level field beyond vanilla 4 bits
     */
    @Overwrite(remap = false)
    public static long m_284188_(int level, boolean fromEmptyShape, Direction direction) {
        long entry = m_284441_(LE_DIRECTIONS_MASK, direction);
        if (fromEmptyShape) {
            entry |= LE_FLAG_FROM_EMPTY_SHAPE;
        }
        return m_284455_(entry, level);
    }

    /**
     * @author LightEngine
     * @reason widen level field beyond vanilla 4 bits
     */
    @Overwrite(remap = false)
    public static long m_284128_(int level, boolean fromEmptyShape, Direction direction) {
        long entry = 0L;
        if (fromEmptyShape) {
            entry |= LE_FLAG_FROM_EMPTY_SHAPE;
        }
        entry = m_284335_(entry, direction);
        return m_284455_(entry, level);
    }

    /**
     * @author LightEngine
     * @reason widen level field beyond vanilla 4 bits
     */
    @Overwrite(remap = false)
    public static long m_284543_(boolean down, boolean north, boolean south, boolean west, boolean east) {
        long entry = m_284455_(0L, 15);
        if (down) {
            entry = m_284335_(entry, Direction.DOWN);
        }
        if (north) {
            entry = m_284335_(entry, Direction.NORTH);
        }
        if (south) {
            entry = m_284335_(entry, Direction.SOUTH);
        }
        if (west) {
            entry = m_284335_(entry, Direction.WEST);
        }
        if (east) {
            entry = m_284335_(entry, Direction.EAST);
        }
        return entry;
    }
}
