package me.tewek.lightengine.network;

import me.tewek.lightengine.LightEngine;
import me.tewek.lightengine.client.LightFieldClientHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/**
 * Forge 1.20.1 networking: a single {@code SimpleChannel} carrying the same
 * v2 wire format as the Fabric/NeoForge branches (hand-encoded section blobs,
 * no NBT). Protocol version matches the payload version so mismatched
 * peers refuse the connection during the Forge handshake.
 */
public final class ModNetwork {
    private ModNetwork() {
    }

    /** Must stay in sync with the payload format (v2 light-field blobs). */
    public static final String PROTOCOL_VERSION = LightFieldPayload.PROTOCOL_VERSION;

    public static SimpleChannel CHANNEL;

    public static void register() {
        CHANNEL = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation(LightEngine.MODID, "main"))
                .networkProtocolVersion(() -> PROTOCOL_VERSION)
                .clientAcceptedVersions(PROTOCOL_VERSION::equals)
                .serverAcceptedVersions(PROTOCOL_VERSION::equals)
                .simpleChannel();

        CHANNEL.messageBuilder(LightFieldPayload.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((payload, buf) -> LightFieldPayload.encode(buf, payload))
                .decoder(LightFieldPayload::decode)
                .consumerMainThread(ModNetwork::handleField)
                .add();

        CHANNEL.messageBuilder(ProfileUpdatePayload.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder((payload, buf) -> ProfileUpdatePayload.encode(buf, payload))
                .decoder(ProfileUpdatePayload::decode)
                .consumerMainThread(ModNetwork::handleProfile)
                .add();
    }

    private static void handleField(LightFieldPayload payload, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> LightFieldClientHandler.handle(payload));
        ctx.setPacketHandled(true);
    }

    private static void handleProfile(ProfileUpdatePayload payload, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                ProfileUpdateHandler.handle(player, payload.id(), payload.radius(), payload.ignoreConditions());
            }
        });
        ctx.setPacketHandled(true);
    }
}
