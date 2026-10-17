package me.tewek.lightengine.platform;

import me.tewek.lightengine.network.LePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Client half of the loader seam (Fabric 1.20.1). */
public final class ClientPlatform {
    private ClientPlatform() {
    }

    public static boolean serverAccepts(ResourceLocation channel) {
        return ClientPlayNetworking.canSend(channel);
    }

    public static void sendToServer(LePayload payload) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        payload.write(buf);
        ClientPlayNetworking.send(payload.id(), buf);
    }
}
