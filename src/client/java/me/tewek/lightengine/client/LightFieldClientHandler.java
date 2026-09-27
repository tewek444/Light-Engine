package me.tewek.lightengine.client;

import me.tewek.lightengine.lightfield.LightFieldStore;
import me.tewek.lightengine.network.LightFieldPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

/**
 * Client-side apply path for {@link LightFieldPayload}.
 * Runs on the render thread via the payload handler.
 */
public final class LightFieldClientHandler {
    private LightFieldClientHandler() {
    }

    public static void handle(LightFieldPayload payload) {
        if (payload == null) {
            return;
        }
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        LightFieldStore store = LightFieldStore.forLevel(level);
        store.evictChunk(payload.chunkX(), payload.chunkZ());
        if (!payload.sections().isEmpty()) {
            store.decodeChunk(payload.chunkX(), payload.chunkZ(), payload.sections());
        }
    }
}
