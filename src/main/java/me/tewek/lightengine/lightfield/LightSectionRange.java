package me.tewek.lightengine.lightfield;

import net.minecraft.world.level.LevelHeightAccessor;

/**
 * Vanilla-parity section range for extended-light persistence and repair.
 *
 * <p>Vanilla itself saves block light for one padding section below and above
 * the buildable range ({@code [minSection - 1, maxSection + 1]}, e.g.
 * {@code -5..20} in the overworld) and reads it back without Y filtering.
 * Extended data must cover exactly the same range: light legitimately
 * propagates into the padding (including below a bedrock floor), and a halo
 * whose extended cells are dropped while vanilla cells persist can no longer
 * be cleared (a 15-wave cannot clear a 15-plateau).</p>
 */
public final class LightSectionRange {
    private LightSectionRange() {
    }

    public static int minSection(LevelHeightAccessor height) {
        return Math.floorDiv(height.getMinBuildHeight(), 16) - 1;
    }

    public static int maxSection(LevelHeightAccessor height) {
        return Math.floorDiv(height.getMaxBuildHeight() - 1, 16) + 1;
    }

    public static int minY(LevelHeightAccessor height) {
        return minSection(height) * 16;
    }

    public static int maxY(LevelHeightAccessor height) {
        return maxSection(height) * 16 + 15;
    }
}
