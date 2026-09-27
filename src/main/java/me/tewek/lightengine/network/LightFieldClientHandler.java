package me.tewek.lightengine.network;

import me.tewek.lightengine.lightfield.LightFieldStore;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

/**
 * Client-side apply path for {@link LightFieldPayload}.
 * Runs inside {@code enqueueWork} from the payload handler.
 */
public final class LightFieldClientHandler {
    private LightFieldClientHandler() {
    }

    public static void handle(LightFieldPayload payload) {
        if (payload == null || payload.sections() == null || payload.sections().isEmpty()) {
            return;
        }
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        LightFieldStore store = LightFieldStore.forLevel(level);
        store.evictChunk(payload.chunkX(), payload.chunkZ());
        store.decodeChunk(payload.chunkX(), payload.chunkZ(), payload.sections());
    }
}
