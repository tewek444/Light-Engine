package me.tewek.lightengine;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * TEMPORARY light-backlog sampler for the stale-light hunt: reports whether
 * the block-light engine still has pending work, to chat and the log.
 * Usage: {@code /lequeues}. DELETE AFTER USE.
 */
public final class LeQueuesCommand {
    private LeQueuesCommand() {
    }

    public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("lequeues")
                .executes(LeQueuesCommand::sample));
    }

    private static int sample(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level;
        try {
            level = src.getLevel();
        } catch (Exception ex) {
            return 0;
        }
        boolean busy = false;
        try {
            busy = level.getLightEngine().hasLightWork();
        } catch (Exception ignored) {
        }
        long quietMs = -1;
        try {
            quietMs = LightCheckStamp.nanosSinceLastCheck() / 1_000_000L;
        } catch (Exception ignored) {
        }
        String msg = "LEQUEUES dim=" + level.dimension().location() + " hasLightWork=" + busy
                + " msSinceLastCheck=" + quietMs;
        LightEngine.LOGGER.warn(msg);
        try {
            if (src.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                player.displayClientMessage(Component.literal(msg), false);
            }
        } catch (Exception ignored) {
        }
        return 1;
    }
}
