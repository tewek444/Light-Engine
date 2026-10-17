package me.tewek.lightengine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * 1.20.1 payload shape (pre-1.20.5 networking has no CustomPacketPayload):
 * a channel id plus a buffer writer. Each payload also has a static
 * {@code read(FriendlyByteBuf)} used by the loader's receiver.
 */
public interface LePayload {
    ResourceLocation id();

    void write(FriendlyByteBuf buf);
}
