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
    /** Light profiles are server-wide settings: owner level (4) only. */
    public static final int REQUIRED_OP_LEVEL = 4;

    private ProfileUpdateHandler() {
    }

    /**
     * The host of a singleplayer/LAN world may always edit; on a server only
     * the owner (operator level 4) may, not every operator.
     */
    public static boolean canEdit(ServerPlayer player) {
        var server = player.level().getServer();
        if (server != null && server.isSingleplayerOwner(player.getGameProfile())) {
            return true;
        }
        return player.hasPermissions(REQUIRED_OP_LEVEL);
    }

    public static void handle(ServerPlayer player, ResourceLocation id, int radius, boolean ignoreConditions,
                              int color, boolean tintEnabled) {
        if (player == null || id == null) {
            return;
        }
        if (!canEdit(player)) {
            player.displayClientMessage(Component.translatable("lightengine.editor.denied"), false);
            LightEngine.LOGGER.warn("Rejected profile update from {} (owner level 4 required)",
                    player.getName().getString());
            // The editor applied the change locally first: roll it back.
            resync(player);
            return;
        }
        if (radius < 0) {
            LightProfileRegistry.remove(id);
            ProfileFileStore.removeProfile(id);
            broadcast(player);
            player.displayClientMessage(
                    Component.translatable("lightengine.editor.removed", id.toString()), false);
            return;
        }
        int clamped = Math.clamp(radius, LightProfileRegistry.MIN_RADIUS, LightProfileRegistry.MAX_RADIUS);
        int rgb = color < 0 ? LightProfileRegistry.DEFAULT_COLOR : color & 0xFFFFFF;
        LightProfileRegistry.upsert(id, clamped, ignoreConditions, rgb, tintEnabled);
        ProfileFileStore.writeProfiles(
                Map.of(id, new LightProfileRegistry.Profile(clamped, ignoreConditions, rgb, tintEnabled)));
        broadcast(player);
        if (clamped > LightProfileRegistry.EFFECTIVE_MAX_RADIUS) {
            LightEngine.LOGGER.warn("LightEngine: {} radius {} stored, propagation capped at {} (perf)",
                    id, clamped, LightProfileRegistry.EFFECTIVE_MAX_RADIUS);
        }
        player.displayClientMessage(
                Component.translatable("lightengine.editor.applied", id.toString(), clamped), false);
    }

    /** Sends the authoritative profiles to one player (undoes a rejected local edit). */
    private static void resync(ServerPlayer player) {
        try {
            me.tewek.lightengine.platform.Platform.sendToPlayer(player, ProfileSyncPayload.of(LightProfileRegistry.snapshot()));
        } catch (Exception ignored) {
        }
    }

    /** Rebroadcasts the authoritative snapshot so remote clients tint alike. */
    private static void broadcast(ServerPlayer player) {
        if (player == null) {
            return;
        }
        try {
            var server = player.getServer();
            if (server == null) {
                return;
            }
            var payload = ProfileSyncPayload.of(LightProfileRegistry.snapshot());
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                try {
                    me.tewek.lightengine.platform.Platform.sendToPlayer(p, payload);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }
}
