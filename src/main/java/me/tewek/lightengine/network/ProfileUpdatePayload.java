package me.tewek.lightengine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Client -> server single profile edit (editor Apply on a dedicated server). */
public record ProfileUpdatePayload(ResourceLocation block, int radius, boolean ignoreConditions, int color,
                                   boolean tintEnabled) implements LePayload {
    public static final ResourceLocation TYPE = new ResourceLocation("lightengine", "profile_update");

    /** Channel id. The edited block is {@link #block()}: naming it {@code id} clashed with this method. */
    @Override
    public ResourceLocation id() {
        return TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(block);
        buf.writeVarInt(radius);
        buf.writeBoolean(ignoreConditions);
        buf.writeVarInt(color);
        buf.writeBoolean(tintEnabled);
    }

    public static ProfileUpdatePayload read(FriendlyByteBuf buf) {
        return new ProfileUpdatePayload(buf.readResourceLocation(), buf.readVarInt(), buf.readBoolean(),
                buf.readVarInt(), buf.readBoolean());
    }
}
