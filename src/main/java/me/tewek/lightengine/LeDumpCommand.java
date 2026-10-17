package me.tewek.lightengine;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.tewek.lightengine.lightfield.LightFieldStore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;

/**
 * Diagnostic command: dumps non-zero v2 cells plus vanilla brightness in a
 * cube, to the log and (for players) to chat. Usage:
 * {@code /ledump <x> <y> <z>}. Raw integer coordinates on purpose: block-pos
 * arguments reject positions outside the world, but extended light
 * legitimately lives in the padding sections below/above it.
 */
public final class LeDumpCommand {
    private LeDumpCommand() {
    }

    public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("ledump")
                .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(ctx -> dump(ctx.getSource(),
                                                new BlockPos(
                                                        IntegerArgumentType.getInteger(ctx, "x"),
                                                        IntegerArgumentType.getInteger(ctx, "y"),
                                                        IntegerArgumentType.getInteger(ctx, "z")),
                                                24))))));
    }

    private static int dump(CommandSourceStack src, BlockPos center, int r) {
        ServerLevel level = src.getLevel();
        LightFieldStore store = LightFieldStore.forLevel(level);
        int minY = Math.max(level.getMinBuildHeight() - 32, center.getY() - r - 16);
        int maxY = Math.min(level.getMaxBuildHeight() + 32, center.getY() + r + 16);
        int total = 0;
        int shown = 0;
        StringBuilder sb = new StringBuilder();
        for (int bx = center.getX() - r; bx <= center.getX() + r; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = center.getZ() - r; bz <= center.getZ() + r; bz++) {
                    long s = SectionPos.asLong(bx >> 4, by >> 4, bz >> 4);
                    int v = store.getCell(s,
                            SectionPos.sectionRelative(bx),
                            SectionPos.sectionRelative(by),
                            SectionPos.sectionRelative(bz));
                    if (v > 0) {
                        total++;
                        if (shown < 80) {
                            int w = 0;
                            try {
                                w = level.getBrightness(LightLayer.BLOCK, new BlockPos(bx, by, bz));
                            } catch (Exception ignored) {
                            }
                            sb.append(' ').append(bx).append(',').append(by).append(',').append(bz)
                                    .append('=').append(v).append('/').append(w);
                            shown++;
                        }
                    }
                }
            }
        }
        String msg = "LEDUMP center=" + center.getX() + "," + center.getY() + "," + center.getZ()
                + " total=" + total + " shown" + shown + sb;
        LightEngine.LOGGER.info(msg);
        try {
            if (src.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(msg), false);
            }
        } catch (Exception ignored) {
        }
        return 1;
    }
}
