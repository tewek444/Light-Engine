package me.tewek.lightengine.network;

import me.tewek.lightengine.LightEngine;
import me.tewek.lightengine.LightProfileRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

    public static void handle(ServerPlayer player, ResourceLocation id, int radius, boolean ignoreConditions) {
        if (player == null || id == null) {
            return;
        }
        if (!player.hasPermissions(REQUIRED_OP_LEVEL)) {
            player.displayClientMessage(Component.translatable("lightengine.editor.denied"), false);
            LightEngine.LOGGER.warn("Rejected profile update from non-operator {}",
                    player.getName().getString());
            return;
        }
        int clamped = Math.clamp(radius, LightProfileRegistry.MIN_RADIUS, LightProfileRegistry.MAX_RADIUS);
        LightProfileRegistry.upsert(id, clamped, ignoreConditions);
        ProfileFileStore.writeProfiles(Map.of(id, new LightProfileRegistry.Profile(clamped, ignoreConditions)));
        player.displayClientMessage(
                Component.translatable("lightengine.editor.applied", id.toString(), clamped), false);
    }
}
