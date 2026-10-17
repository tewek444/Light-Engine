package me.tewek.lightengine.platform;

import me.tewek.lightengine.network.LePayload;
import me.tewek.lightengine.network.ModNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;

import java.nio.file.Path;

/** Loader seam (Forge 1.20.1 implementation). */
public final class Platform {
    private Platform() {
    }

    public static final String LOADER = "forge";

    public static Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    public static boolean isClient() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }

    public static boolean isModLoaded(String modId) {
        ModList list = ModList.get();
        return list != null && list.isLoaded(modId);
    }

    public static String modName(String namespace) {
        ModList list = ModList.get();
        if (list == null) {
            return namespace;
        }
        return list.getModContainerById(namespace).map(c -> c.getModInfo().getDisplayName()).orElse(namespace);
    }

    public static void sendToPlayer(ServerPlayer player, LePayload payload) {
        if (ModNetwork.CHANNEL.isRemotePresent(player.connection.connection)) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
        }
    }

    public static void sendToTrackingChunk(ServerLevel level, ChunkPos chunk, LePayload payload) {
        for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(chunk, false)) {
            sendToPlayer(player, payload);
        }
    }
}
