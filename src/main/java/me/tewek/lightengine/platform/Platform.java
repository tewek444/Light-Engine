package me.tewek.lightengine.platform;

import me.tewek.lightengine.network.LePayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.nio.file.Path;

/** Loader seam (Fabric 1.20.1 implementation). */
public final class Platform {
    private Platform() {
    }

    public static final String LOADER = "fabric";

    public static Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    public static boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    public static boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    public static String modName(String namespace) {
        return FabricLoader.getInstance().getModContainer(namespace)
                .map(c -> c.getMetadata().getName()).orElse(namespace);
    }

    public static void sendToPlayer(ServerPlayer player, LePayload payload) {
        if (ServerPlayNetworking.canSend(player, payload.id())) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            payload.write(buf);
            ServerPlayNetworking.send(player, payload.id(), buf);
        }
    }

    public static void sendToTrackingChunk(ServerLevel level, ChunkPos chunk, LePayload payload) {
        for (ServerPlayer player : PlayerLookup.tracking(level, chunk)) {
            sendToPlayer(player, payload);
        }
    }
}
