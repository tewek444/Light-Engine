package me.tewek.lightengine.network;

import me.tewek.lightengine.LightEngine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/** Forge 1.20.1 SimpleChannel carrying the three Light Engine payloads. */
public final class ModNetwork {
    private ModNetwork() {
    }

    public static final String PROTOCOL_VERSION = LightFieldPayload.PROTOCOL_VERSION;

    public static SimpleChannel CHANNEL;

    public static void register() {
        CHANNEL = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation(LightEngine.MODID, "main"))
                .networkProtocolVersion(() -> PROTOCOL_VERSION)
                // Optional on both sides: vanilla clients/servers can still connect.
                .clientAcceptedVersions(v -> PROTOCOL_VERSION.equals(v) || NetworkRegistry.ABSENT.version().equals(v)
                        || NetworkRegistry.ACCEPTVANILLA.equals(v))
                .serverAcceptedVersions(v -> PROTOCOL_VERSION.equals(v) || NetworkRegistry.ABSENT.version().equals(v)
                        || NetworkRegistry.ACCEPTVANILLA.equals(v))
                .simpleChannel();

        CHANNEL.messageBuilder(LightFieldPayload.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LightFieldPayload::write)
                .decoder(LightFieldPayload::read)
                .consumerMainThread(ModNetwork::handleField)
                .add();
        CHANNEL.messageBuilder(ProfileUpdatePayload.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ProfileUpdatePayload::write)
                .decoder(ProfileUpdatePayload::read)
                .consumerMainThread(ModNetwork::handleProfile)
                .add();
        CHANNEL.messageBuilder(ProfileSyncPayload.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ProfileSyncPayload::write)
                .decoder(ProfileSyncPayload::read)
                .consumerMainThread(ModNetwork::handleSync)
                .add();
    }

    private static void handleField(LightFieldPayload payload, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LightFieldClientHandler.handle(payload));
        ctx.get().setPacketHandled(true);
    }

    private static void handleSync(ProfileSyncPayload payload, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ProfileSyncClientHandler.handle(payload));
        ctx.get().setPacketHandled(true);
    }

    private static void handleProfile(ProfileUpdatePayload payload, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            ProfileUpdateHandler.handle(player, payload.block(), payload.radius(), payload.ignoreConditions(),
                    payload.color(), payload.tintEnabled());
        }
        ctx.get().setPacketHandled(true);
    }
}
