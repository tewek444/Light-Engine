package me.tewek.lightengine.network;

import me.tewek.lightengine.LightEngine;
import me.tewek.lightengine.LightProfileRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * Server-side apply path for editor deltas. Authoritative: only operators
 * (permission level 2+) may change profiles; everyone else gets a denial.
 * Existing placed blocks are intentionally NOT relit here (deferred).
 */
public final class ProfileUpdateHandler {
    public static final int REQUIRED_OP_LEVEL = 2;

    private ProfileUpdateHandler() {
    }

    public static void handle(ServerPlayer player, Identifier id, int radius, boolean ignoreConditions) {
        if (player == null || id == null) {
            return;
        }
        if (!player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)) {
            player.sendSystemMessage(Component.translatable("lightengine.editor.denied"));
            LightEngine.LOGGER.warn("Rejected profile update from non-operator {}",
                    player.getName().getString());
            return;
        }
        if (radius < 0) {
            LightProfileRegistry.remove(id);
            ProfileFileStore.removeProfile(id);
            player.sendSystemMessage(
                    Component.translatable("lightengine.editor.removed", id.toString()));
            return;
        }
        int clamped = Math.clamp(radius, LightProfileRegistry.MIN_RADIUS, LightProfileRegistry.MAX_RADIUS);
        LightProfileRegistry.upsert(id, clamped, ignoreConditions);
        ProfileFileStore.writeProfiles(Map.of(id, new LightProfileRegistry.Profile(clamped, ignoreConditions)));
        player.sendSystemMessage(
                Component.translatable("lightengine.editor.applied", id.toString(), clamped));
    }
}
