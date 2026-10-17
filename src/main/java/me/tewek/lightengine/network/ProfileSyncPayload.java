package me.tewek.lightengine.network;

import me.tewek.lightengine.LightProfileRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server -&gt; client authoritative profile snapshot (tint + radius).
 * Sent on login and rebroadcast on every editor change, so remote clients
 * resolve the same hues as the server without touching their file.
 */
public record ProfileSyncPayload(Map<Identifier, LightProfileRegistry.Profile> profiles)
        implements CustomPacketPayload {
    public static final Type<ProfileSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("lightengine", "profile_sync"));

    private static final int MAX_PROFILES = 4096;

    public static final StreamCodec<RegistryFriendlyByteBuf, ProfileSyncPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        Map<Identifier, LightProfileRegistry.Profile> map = payload.profiles();
                        int n = map == null ? 0 : Math.min(map.size(), MAX_PROFILES);
                        buf.writeVarInt(n);
                        if (map == null) {
                            return;
                        }
                        int i = 0;
                        for (Map.Entry<Identifier, LightProfileRegistry.Profile> e : map.entrySet()) {
                            if (i++ >= n) {
                                break;
                            }
                            Identifier.STREAM_CODEC.encode(buf, e.getKey());
                            LightProfileRegistry.Profile p = e.getValue() == null
                                    ? new LightProfileRegistry.Profile(0, false, LightProfileRegistry.DEFAULT_COLOR)
                                    : e.getValue();
                            buf.writeVarInt(p.lightRadius());
                            buf.writeBoolean(p.ignoreConditions());
                            buf.writeVarInt(p.color());
                            buf.writeBoolean(p.tintEnabled());
                        }
                    },
                    buf -> {
                        int n = buf.readVarInt();
                        if (n < 0 || n > MAX_PROFILES) {
                            throw new io.netty.handler.codec.DecoderException("bad profile count " + n);
                        }
                        Map<Identifier, LightProfileRegistry.Profile> map = new LinkedHashMap<>(n);
                        for (int i = 0; i < n; i++) {
                            Identifier id = Identifier.STREAM_CODEC.decode(buf);
                            int radius = buf.readVarInt();
                            boolean ignore = buf.readBoolean();
                            int color = buf.readVarInt();
                            boolean tint = buf.readBoolean();
                            map.put(id, new LightProfileRegistry.Profile(radius, ignore, color, tint));
                        }
                        return new ProfileSyncPayload(Map.copyOf(map));
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ProfileSyncPayload of(Map<Identifier, LightProfileRegistry.Profile> profiles) {
        return new ProfileSyncPayload(profiles == null ? Map.of() : Map.copyOf(profiles));
    }
}
