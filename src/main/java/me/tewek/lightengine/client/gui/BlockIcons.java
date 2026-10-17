package me.tewek.lightengine.client.gui;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Icons for blocks that have no item of their own (wall torches, fire,
 * fluids, portals, candle cakes...). Without a fallback those rows showed an
 * empty square in the editor lists.
 */
final class BlockIcons {
    private BlockIcons() {
    }

    private static final Map<Block, ItemStack> CACHE = new ConcurrentHashMap<>();

    static ItemStack iconFor(Block block) {
        if (block == null) {
            return ItemStack.EMPTY;
        }
        return CACHE.computeIfAbsent(block, BlockIcons::resolve).copy();
    }

    private static ItemStack resolve(Block block) {
        Item own = block.asItem();
        if (own != Items.AIR) {
            return new ItemStack(own);
        }
        Item special = special(block);
        if (special != null) {
            return new ItemStack(special);
        }
        // Name-based fallback: "x_wall_torch" -> "x_torch", "x_wall_fan" ->
        // "x_fan", "candle_cake" variants -> candle, "potted_x" -> x, etc.
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (id != null) {
            String path = id.getPath();
            String[] guesses = {
                    path.replace("_wall_", "_"),
                    path.replace("wall_", ""),
                    path.startsWith("potted_") ? path.substring("potted_".length()) : null,
                    path.endsWith("_candle_cake") ? path.substring(0, path.length() - "_cake".length()) : null,
                    path.startsWith("lit_") ? path.substring("lit_".length()) : null,
                    path.endsWith("_plant") ? path.substring(0, path.length() - "_plant".length()) : null,
            };
            for (String g : guesses) {
                if (g == null || g.equals(path)) {
                    continue;
                }
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), g));
                if (item != Items.AIR) {
                    return new ItemStack(item);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static Item special(Block block) {
        if (block == Blocks.FIRE || block == Blocks.SOUL_FIRE) {
            return Items.FLINT_AND_STEEL;
        }
        if (block == Blocks.LAVA || block == Blocks.LAVA_CAULDRON) {
            return Items.LAVA_BUCKET;
        }
        if (block == Blocks.WATER || block == Blocks.BUBBLE_COLUMN) {
            return Items.WATER_BUCKET;
        }
        if (block == Blocks.NETHER_PORTAL) {
            return Items.OBSIDIAN;
        }
        if (block == Blocks.END_PORTAL || block == Blocks.END_GATEWAY) {
            return Items.ENDER_EYE;
        }
        if (block == Blocks.CANDLE_CAKE) {
            return Items.CANDLE;
        }
        if (block == Blocks.CAVE_VINES || block == Blocks.CAVE_VINES_PLANT) {
            return Items.GLOW_BERRIES;
        }
        if (block == Blocks.REDSTONE_WIRE) {
            return Items.REDSTONE;
        }
        if (block == Blocks.MOVING_PISTON) {
            return Items.PISTON;
        }
        return null;
    }
}
