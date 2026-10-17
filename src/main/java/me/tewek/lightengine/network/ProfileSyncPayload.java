package me.tewek.lightengine.network;

import io.netty.handler.codec.DecoderException;
import me.tewek.lightengine.LightProfileRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/** Server -> client authoritative profile snapshot. */
public record ProfileSyncPayload(Map<ResourceLocation, LightProfileRegistry.Profile> profiles) implements LePayload {
    public static final ResourceLocation TYPE = new ResourceLocation("lightengine", "profile_sync");
    private static final int MAX_PROFILES = 4096;

    @Override
    public ResourceLocation id() {
        return TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        int n = profiles == null ? 0 : Math.min(profiles.size(), MAX_PROFILES);
        buf.writeVarInt(n);
        if (profiles == null) {
            return;
        }
        int i = 0;
        for (Map.Entry<ResourceLocation, LightProfileRegistry.Profile> e : profiles.entrySet()) {
            if (i++ >= n) {
                break;
            }
            buf.writeResourceLocation(e.getKey());
            LightProfileRegistry.Profile p = e.getValue() == null
                    ? new LightProfileRegistry.Profile(0, false, LightProfileRegistry.DEFAULT_COLOR)
                    : e.getValue();
            buf.writeVarInt(p.lightRadius());
            buf.writeBoolean(p.ignoreConditions());
            buf.writeVarInt(p.color());
            buf.writeBoolean(p.tintEnabled());
        }
    }

    public static ProfileSyncPayload read(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_PROFILES) {
            throw new DecoderException("bad profile count " + n);
        }
        Map<ResourceLocation, LightProfileRegistry.Profile> map = new LinkedHashMap<>(n);
        for (int i = 0; i < n; i++) {
            ResourceLocation id = buf.readResourceLocation();
            int radius = buf.readVarInt();
            boolean ignore = buf.readBoolean();
            int color = buf.readVarInt();
            boolean tint = buf.readBoolean();
            map.put(id, new LightProfileRegistry.Profile(radius, ignore, color, tint));
        }
        return new ProfileSyncPayload(Map.copyOf(map));
    }

    public static ProfileSyncPayload of(Map<ResourceLocation, LightProfileRegistry.Profile> profiles) {
        return new ProfileSyncPayload(profiles == null ? Map.of() : Map.copyOf(profiles));
    }
}
