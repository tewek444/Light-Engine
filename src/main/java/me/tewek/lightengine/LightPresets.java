package me.tewek.lightengine;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Built-in profile presets for vanilla light sources.
 *
 * <ul>
 *   <li>{@link Mode#RADIUS_AND_COLOR} (default) - longer radius and a color;</li>
 *   <li>{@link Mode#RADIUS_ONLY} - longer radius, vanilla warm light;</li>
 *   <li>{@link Mode#COLOR_ONLY} - vanilla radius, colored light.</li>
 * </ul>
 * Blocks missing from the running game version are skipped, so the same
 * table serves every port.
 */
public final class LightPresets {
    private LightPresets() {
    }

    public enum Mode {
        RADIUS_AND_COLOR("radius_and_color"),
        RADIUS_ONLY("radius_only"),
        COLOR_ONLY("color_only");

        public final String key;

        Mode(String key) {
            this.key = key;
        }

        public static Mode byKey(String key) {
            if (key != null) {
                String k = key.trim().toLowerCase(Locale.ROOT);
                for (Mode m : values()) {
                    if (m.key.equals(k)) {
                        return m;
                    }
                }
            }
            return RADIUS_AND_COLOR;
        }
    }

    /** Preset table revision; configs seeded with an older one get the new rows added. */
    public static final int REVISION = 2;

    /** One preset row: vanilla radius, boosted radius, light color, revision it was added in. */
    private record Entry(String id, int vanilla, int radius, int color, int since) {
        Entry(String id, int vanilla, int radius, int color) {
            this(id, vanilla, radius, color, 1);
        }
    }

    private static final List<Entry> TABLE = List.of(
            new Entry("torch", 14, 22, 0xFF9A3C),
            new Entry("wall_torch", 14, 22, 0xFF9A3C),
            new Entry("soul_torch", 10, 18, 0x5FD7FF),
            new Entry("soul_wall_torch", 10, 18, 0x5FD7FF),
            new Entry("redstone_torch", 7, 10, 0xFF2A2A),
            new Entry("redstone_wall_torch", 7, 10, 0xFF2A2A),
            new Entry("lantern", 15, 24, 0xFFA040),
            new Entry("soul_lantern", 10, 20, 0x77CFFF),
            new Entry("glowstone", 15, 30, 0xFFD27A),
            new Entry("sea_lantern", 15, 26, 0xBFF3FF),
            new Entry("shroomlight", 15, 24, 0xFF8A3D),
            new Entry("jack_o_lantern", 15, 22, 0xFFA030),
            new Entry("campfire", 15, 22, 0xFF7A2A),
            new Entry("soul_campfire", 10, 18, 0x4FD3FF),
            new Entry("fire", 15, 20, 0xFF6A1A),
            new Entry("soul_fire", 10, 16, 0x3FC8FF),
            new Entry("lava", 15, 20, 0xFF5A10),
            new Entry("magma_block", 3, 6, 0xFF4A10),
            new Entry("redstone_lamp", 15, 24, 0xFFB060),
            new Entry("end_rod", 14, 20, 0xF4E8FF),
            new Entry("beacon", 15, 30, 0xCFF6FF),
            new Entry("conduit", 15, 24, 0x6FE0FF),
            new Entry("ochre_froglight", 15, 24, 0xFFE08A),
            new Entry("verdant_froglight", 15, 24, 0xA8FF8A),
            new Entry("pearlescent_froglight", 15, 24, 0xFFB0F0),
            new Entry("amethyst_cluster", 5, 8, 0xC77DFF),
            new Entry("large_amethyst_bud", 4, 6, 0xB070FF),
            new Entry("crying_obsidian", 10, 14, 0x9B30FF),
            new Entry("glow_lichen", 7, 10, 0x8CFFD0),
            new Entry("copper_bulb", 15, 22, 0xFFB070),
            new Entry("waxed_copper_bulb", 15, 22, 0xFFB070),
            new Entry("sculk_catalyst", 6, 9, 0x30D0C0),
            new Entry("enchanting_table", 7, 10, 0xB060FF),
            new Entry("ender_chest", 7, 10, 0x40E0B0),
            new Entry("nether_portal", 11, 16, 0xA040FF),
            new Entry("end_gateway", 15, 24, 0xCFF0FF),
            new Entry("end_portal", 15, 24, 0x9FE8FF),
            new Entry("furnace", 13, 18, 0xFF8A30),
            new Entry("smoker", 13, 18, 0xFF8A30),
            new Entry("blast_furnace", 13, 18, 0xFF9A50),
            new Entry("candle", 12, 16, 0xFFB050),
            new Entry("copper_torch", 14, 22, 0x7CFF8A, 2),
            new Entry("copper_wall_torch", 14, 22, 0x7CFF8A, 2),
            new Entry("copper_lantern", 15, 24, 0x8CFFA0, 2),
            new Entry("exposed_copper_lantern", 15, 24, 0x8CFFA0, 2),
            new Entry("weathered_copper_lantern", 15, 24, 0x8CFFA0, 2),
            new Entry("oxidized_copper_lantern", 15, 24, 0x8CFFA0, 2),
            new Entry("waxed_copper_lantern", 15, 24, 0x8CFFA0, 2),
            new Entry("waxed_exposed_copper_lantern", 15, 24, 0x8CFFA0, 2),
            new Entry("waxed_weathered_copper_lantern", 15, 24, 0x8CFFA0, 2),
            new Entry("waxed_oxidized_copper_lantern", 15, 24, 0x8CFFA0, 2),
            new Entry("exposed_copper_bulb", 12, 18, 0xFFB070, 2),
            new Entry("weathered_copper_bulb", 8, 12, 0xFFB070, 2),
            new Entry("oxidized_copper_bulb", 4, 6, 0xFFB070, 2),
            new Entry("waxed_exposed_copper_bulb", 12, 18, 0xFFB070, 2),
            new Entry("waxed_weathered_copper_bulb", 8, 12, 0xFFB070, 2),
            new Entry("waxed_oxidized_copper_bulb", 4, 6, 0xFFB070, 2),
            new Entry("lava_cauldron", 15, 20, 0xFF5A10, 2),
            new Entry("redstone_ore", 9, 12, 0xFF3030, 2),
            new Entry("deepslate_redstone_ore", 9, 12, 0xFF3030, 2),
            new Entry("medium_amethyst_bud", 2, 4, 0xB070FF, 2),
            new Entry("small_amethyst_bud", 1, 2, 0xB070FF, 2),
            new Entry("firefly_bush", 2, 4, 0xD8FF6A, 2),
            new Entry("brewing_stand", 1, 3, 0xFFD27A, 2),
            new Entry("dragon_egg", 1, 3, 0xC060FF, 2),
            new Entry("end_portal_frame", 1, 3, 0x9FE8FF, 2),
            new Entry("white_candle", 12, 16, 0xFFF4E0, 2),
            new Entry("orange_candle", 12, 16, 0xFF9A3C, 2),
            new Entry("magenta_candle", 12, 16, 0xFF5AE6, 2),
            new Entry("light_blue_candle", 12, 16, 0x7CCBFF, 2),
            new Entry("yellow_candle", 12, 16, 0xFFE45A, 2),
            new Entry("lime_candle", 12, 16, 0x9CFF5A, 2),
            new Entry("pink_candle", 12, 16, 0xFF9AC8, 2),
            new Entry("gray_candle", 12, 16, 0xB4B4B4, 2),
            new Entry("light_gray_candle", 12, 16, 0xD8D8D8, 2),
            new Entry("cyan_candle", 12, 16, 0x3FE0E0, 2),
            new Entry("purple_candle", 12, 16, 0xB060FF, 2),
            new Entry("blue_candle", 12, 16, 0x4A6BFF, 2),
            new Entry("brown_candle", 12, 16, 0xC8864A, 2),
            new Entry("green_candle", 12, 16, 0x5ABF3C, 2),
            new Entry("red_candle", 12, 16, 0xFF3A3A, 2),
            new Entry("black_candle", 12, 16, 0x9A88B0, 2),
            new Entry("candle_cake", 3, 6, 0xFFB050, 2),
            new Entry("white_candle_cake", 3, 6, 0xFFF4E0, 2),
            new Entry("orange_candle_cake", 3, 6, 0xFF9A3C, 2),
            new Entry("magenta_candle_cake", 3, 6, 0xFF5AE6, 2),
            new Entry("light_blue_candle_cake", 3, 6, 0x7CCBFF, 2),
            new Entry("yellow_candle_cake", 3, 6, 0xFFE45A, 2),
            new Entry("lime_candle_cake", 3, 6, 0x9CFF5A, 2),
            new Entry("pink_candle_cake", 3, 6, 0xFF9AC8, 2),
            new Entry("gray_candle_cake", 3, 6, 0xB4B4B4, 2),
            new Entry("light_gray_candle_cake", 3, 6, 0xD8D8D8, 2),
            new Entry("cyan_candle_cake", 3, 6, 0x3FE0E0, 2),
            new Entry("purple_candle_cake", 3, 6, 0xB060FF, 2),
            new Entry("blue_candle_cake", 3, 6, 0x4A6BFF, 2),
            new Entry("brown_candle_cake", 3, 6, 0xC8864A, 2),
            new Entry("green_candle_cake", 3, 6, 0x5ABF3C, 2),
            new Entry("red_candle_cake", 3, 6, 0xFF3A3A, 2),
            new Entry("black_candle_cake", 3, 6, 0x9A88B0, 2)
    );
    // Not in the table on purpose: blocks whose emission depends on a
    // non-LIT property (respawn_anchor charges, cave_vines berries, light
    // level) - a fixed profile radius would make them glow when "off".

    /**
     * Profiles of one preset for every table block present in this game.
     * Radius is clamped to the vanilla value in {@link Mode#COLOR_ONLY}.
     */
    public static Map<ResourceLocation, LightProfileRegistry.Profile> build(Mode mode) {
        return build(mode, 0);
    }

    /** Rows added after {@code afterRevision} only (0 = the whole table). */
    public static Map<ResourceLocation, LightProfileRegistry.Profile> build(Mode mode, int afterRevision) {
        Map<ResourceLocation, LightProfileRegistry.Profile> out = new LinkedHashMap<>();
        for (Entry e : TABLE) {
            if (e.since() <= afterRevision) {
                continue;
            }
            ResourceLocation id = ResourceLocation.withDefaultNamespace(e.id());
            if (!BuiltInRegistries.BLOCK.containsKey(id)) {
                continue;
            }
            int radius = mode == Mode.COLOR_ONLY ? e.vanilla() : e.radius();
            boolean tint = mode != Mode.RADIUS_ONLY;
            out.put(id, new LightProfileRegistry.Profile(radius, false, e.color(), tint));
        }
        return out;
    }
}
