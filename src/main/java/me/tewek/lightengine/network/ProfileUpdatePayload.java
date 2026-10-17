package me.tewek.lightengine.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -&gt; server editor delta: upsert one block profile.
 * A negative radius means removal (reset to vanilla emission).
 * The server applies it only for operators (permission level 2+).
 * Color is packed {@code 0xRRGGBB}; unknown/negative values fall back to default.
 * {@code tintEnabled} is the per-profile "use light color" switch: false keeps
 * the extended radius but renders fully default vanilla light.
 */
public record ProfileUpdatePayload(ResourceLocation id, int radius, boolean ignoreConditions, int color,
                                   boolean tintEnabled) implements CustomPacketPayload {
    public static final Type<ProfileUpdatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("lightengine", "profile_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProfileUpdatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, ProfileUpdatePayload::id,
                    ByteBufCodecs.VAR_INT, ProfileUpdatePayload::radius,
                    ByteBufCodecs.BOOL, ProfileUpdatePayload::ignoreConditions,
                    ByteBufCodecs.VAR_INT, ProfileUpdatePayload::color,
                    ByteBufCodecs.BOOL, ProfileUpdatePayload::tintEnabled,
                    ProfileUpdatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
