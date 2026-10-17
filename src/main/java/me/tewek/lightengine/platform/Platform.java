package me.tewek.lightengine.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Path;

/**
 * Loader seam (NeoForge implementation). Every port swaps only this class
 * (plus entrypoints, event wiring and the config holder); all other code
 * talks to the loader through it.
 */
public final class Platform {
    private Platform() {
    }

    public static final String LOADER = "neoforge";

    public static Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    private static final boolean CLIENT = detectClient();

    /** Physical client check that survives FML API churn: client classes are absent on servers. */
    private static boolean detectClient() {
        try {
            Class.forName("net.minecraft.client.Minecraft", false, Platform.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ex) {
            return false;
        }
    }

    public static boolean isClient() {
        return CLIENT;
    }

    public static boolean isModLoaded(String modId) {
        ModList list = ModList.get();
        return list != null && list.isLoaded(modId);
    }

    /** Display name of a mod by namespace, or the namespace itself. */
    public static String modName(String namespace) {
        ModList list = ModList.get();
        if (list == null) {
            return namespace;
        }
        return list.getModContainerById(namespace).map(c -> c.getModInfo().getDisplayName()).orElse(namespace);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendToTrackingChunk(ServerLevel level, ChunkPos chunk, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersTrackingChunk(level, chunk, payload);
    }
}
