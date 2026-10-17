package me.tewek.lightengine.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client half of the loader seam (NeoForge). Never loaded on a server. */
public final class ClientPlatform {
    private ClientPlatform() {
    }

    /** Whether the connected (remote) server accepts this payload. */
    public static boolean serverAccepts(CustomPacketPayload.Type<?> type) {
        Minecraft mc = Minecraft.getInstance();
        return mc.getConnection() instanceof ICommonPacketListener listener && listener.hasChannel(type);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }
}
