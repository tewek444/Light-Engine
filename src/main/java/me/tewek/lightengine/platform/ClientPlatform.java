package me.tewek.lightengine.platform;

import me.tewek.lightengine.network.LePayload;
import me.tewek.lightengine.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Client half of the loader seam (Forge 1.20.1). */
public final class ClientPlatform {
    private ClientPlatform() {
    }

    public static boolean serverAccepts(ResourceLocation channel) {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && ModNetwork.CHANNEL.isRemotePresent(connection.getConnection());
    }

    public static void sendToServer(LePayload payload) {
        ModNetwork.CHANNEL.sendToServer(payload);
    }
}
