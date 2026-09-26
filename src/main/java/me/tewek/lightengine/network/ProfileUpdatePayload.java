package me.tewek.lightengine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -&gt; server editor delta: upsert one block profile.
 * A negative radius means removal (reset to vanilla emission).
 * The server applies it only for operators (permission level 2+).
 *
 * <p>Fabric 1.20.1 port: manual {@link FriendlyByteBuf} codec, same field
 * order as Alpha2 on 1.21.1 (id, radius varint, ignoreConditions bool).
 */
public record ProfileUpdatePayload(ResourceLocation id, int radius, boolean ignoreConditions) {
    public static final ResourceLocation ID =
            new ResourceLocation("lightengine", "profile_update");

    public static void encode(FriendlyByteBuf buf, ProfileUpdatePayload payload) {
        buf.writeResourceLocation(payload.id());
        buf.writeVarInt(payload.radius());
        buf.writeBoolean(payload.ignoreConditions());
    }

    public static ProfileUpdatePayload decode(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        int radius = buf.readVarInt();
        boolean ignoreConditions = buf.readBoolean();
        return new ProfileUpdatePayload(id, radius, ignoreConditions);
    }
}
