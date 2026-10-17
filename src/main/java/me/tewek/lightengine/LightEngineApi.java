package me.tewek.lightengine;

import me.tewek.lightengine.lightfield.ChromaTint;
import me.tewek.lightengine.lightfield.LightField;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.Objects;

/**
 * Public API for other mods and shader-pack bridges (Alpha5).
 *
 * <h2>Giving your blocks colored / longer-range light</h2>
 * Call from {@code FMLCommonSetupEvent} (both sides, so client and server
 * agree):
 * <pre>{@code
 * // radius 24, cyan light
 * LightEngineApi.registerLight(MyBlocks.CRYSTAL_LAMP.get(), 24, 0x77CFFF);
 * // keep the vanilla radius, just color it
 * LightEngineApi.registerColor(MyBlocks.RUBY_TORCH.get(), 0xFF3030);
 * // color per block state (e.g. by a COLOR property); -1 = vanilla light
 * LightEngineApi.registerColorProvider(MyBlocks.RGB_LAMP.get(), state -> state.getValue(COLOR).rgb());
 * }</pre>
 * Profiles the user writes in {@code lightengine-common.toml} (or the
 * in-game editor) always override what a mod registered.
 *
 * <h2>Without a compile dependency (IMC)</h2>
 * Send during {@code InterModEnqueueEvent}:
 * <pre>{@code
 * InterModComms.sendTo("lightengine", "register_light", () -> Map.of(
 *         "block", "mymod:crystal_lamp", "radius", 24, "color", "#77CFFF"));
 * InterModComms.sendTo("lightengine", "register_color", () -> Map.of(
 *         "block", "mymod:ruby_torch", "color", 0xFF3030));
 * }</pre>
 * Keys: {@code block} (required), {@code radius} (0-255),
 * {@code color} ({@code "#RRGGBB"} or int), {@code ignore_conditions}
 * (boolean: emit even when the block's LIT/POWERED property is false).
 *
 * <h2>Reading light</h2>
 * {@link #getLightColor} (hue reaching a block), {@link #getLightLevel}
 * (extended level 0-255), {@link #getEmitterColor}, {@link #snapshotProfiles}.
 */
public final class LightEngineApi {
    private LightEngineApi() {
    }

    /** "No color": the block emits vanilla warm light. */
    public static final int NO_COLOR = -1;

    /** Per-state light color: packed {@code 0xRRGGBB}, or {@link #NO_COLOR}. */
    @FunctionalInterface
    public interface ColorProvider {
        int getColor(BlockState state);
    }

    // ----- registration -----

    /**
     * Registers a light profile for a block: emission radius (0-255; values
     * above {@link LightProfileRegistry#EFFECTIVE_MAX_RADIUS} are capped at
     * runtime) and light color ({@link #NO_COLOR} for vanilla warm light).
     * LIT/POWERED properties are respected (unlit = vanilla emission).
     */
    public static void registerLight(ResourceLocation block, int radius, int color) {
        registerLight(block, radius, color, false);
    }

    /**
     * @param ignoreConditions emit even when the block's LIT/POWERED property
     *                         says it is off
     */
    public static void registerLight(ResourceLocation block, int radius, int color, boolean ignoreConditions) {
        Objects.requireNonNull(block, "block");
        LightProfileRegistry.registerApiDefault(block, new LightProfileRegistry.Profile(radius, ignoreConditions,
                color < 0 ? LightProfileRegistry.DEFAULT_COLOR : color, color >= 0));
    }

    public static void registerLight(Block block, int radius, int color) {
        registerLight(keyOf(block), radius, color, false);
    }

    public static void registerLight(Block block, int radius, int color, boolean ignoreConditions) {
        registerLight(keyOf(block), radius, color, ignoreConditions);
    }

    /** Colors a block's light while keeping its vanilla radius. */
    public static void registerColor(ResourceLocation block, int color) {
        Objects.requireNonNull(block, "block");
        int c = color & 0xFFFFFF;
        LightProfileRegistry.registerColorProvider(block, state -> c);
    }

    public static void registerColor(Block block, int color) {
        registerColor(keyOf(block), color);
    }

    /**
     * Per-state color (keeps the vanilla radius unless a profile sets one).
     * Called on meshing threads: must be fast, pure and thread-safe.
     */
    public static void registerColorProvider(ResourceLocation block, ColorProvider provider) {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(provider, "provider");
        LightProfileRegistry.registerColorProvider(block, provider);
    }

    public static void registerColorProvider(Block block, ColorProvider provider) {
        registerColorProvider(keyOf(block), provider);
    }

    /** Removes everything a mod registered for a block. */
    public static boolean unregister(ResourceLocation block) {
        return block != null && LightProfileRegistry.unregisterApi(block);
    }

    private static ResourceLocation keyOf(Block block) {
        Objects.requireNonNull(block, "block");
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null || (id.getPath().equals("air") && block != net.minecraft.world.level.block.Blocks.AIR)) {
            throw new IllegalStateException("Block is not registered yet: " + block
                    + " (register lights in FMLCommonSetupEvent or later)");
        }
        return id;
    }

    /** IMC entry point (see class docs). Returns whether the message was valid. */
    static boolean handleImc(String method, Object payload) {
        if (!(payload instanceof Map<?, ?> map)) {
            return false;
        }
        Object rawBlock = map.get("block");
        ResourceLocation id = rawBlock instanceof ResourceLocation rl ? rl
                : rawBlock instanceof String s ? ResourceLocation.tryParse(s) : null;
        if (id == null) {
            return false;
        }
        Object rawColor = map.get("color");
        int color = rawColor == null ? NO_COLOR : LightProfileRegistry.parseColor(rawColor);
        switch (method) {
            case "register_light" -> {
                if (!(map.get("radius") instanceof Number radius)) {
                    return false;
                }
                boolean ignore = Boolean.TRUE.equals(map.get("ignore_conditions"));
                registerLight(id, radius.intValue(), color, ignore);
                return true;
            }
            case "register_color" -> {
                if (rawColor == null) {
                    return false;
                }
                registerColor(id, color);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    // ----- queries -----

    /** Packed 0xRRGGBB hue falling on a block, 0 when vanilla applies. */
    public static int getLightColor(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return 0;
        }
        return Math.max(0, ChromaTint.forSurface(level, pos, level.getBlockState(pos)));
    }

    /** Logical extended level at a block: max(R,G,B), 0-255. */
    public static int getLightLevel(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return 0;
        }
        long section = SectionPos.blockToSection(pos.asLong());
        return LightField.readCell(level, section,
                SectionPos.sectionRelative(pos.getX()),
                SectionPos.sectionRelative(pos.getY()),
                SectionPos.sectionRelative(pos.getZ()));
    }

    /** Emitter color of a block state, or {@link #NO_COLOR} when vanilla applies. */
    public static int getEmitterColor(BlockState state) {
        return state == null ? NO_COLOR : LightProfileRegistry.colorForState(state);
    }

    /** Config profiles (what the user configured): block id to radius/tint. */
    public static Map<ResourceLocation, LightProfileRegistry.Profile> snapshotProfiles() {
        return LightProfileRegistry.snapshot();
    }
}
