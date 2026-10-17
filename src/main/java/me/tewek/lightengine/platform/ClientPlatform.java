package me.tewek.lightengine.platform;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client half of the loader seam (Fabric). Never loaded on a server. */
public final class ClientPlatform {
    private ClientPlatform() {
    }

    public static boolean serverAccepts(CustomPacketPayload.Type<?> type) {
        return ClientPlayNetworking.canSend(type);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }
}
