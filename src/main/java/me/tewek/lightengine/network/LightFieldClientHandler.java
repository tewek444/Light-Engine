package me.tewek.lightengine.network;

import me.tewek.lightengine.lightfield.ChromaSection;
import me.tewek.lightengine.lightfield.LightFieldStore;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Client-side apply path for {@link LightFieldPayload}.
 * Runs inside {@code enqueueWork} from the payload handler.
 * After applying, dirties the union of previously-present and newly-arrived
 * sections: without it meshes keep baking with stale brightness and the
 * tint gate never re-fires (invisible live updates).
 */
public final class LightFieldClientHandler {
    private LightFieldClientHandler() {
    }

    public static void handle(LightFieldPayload payload) {
        if (payload == null) {
            return;
        }
        Minecraft mc;
        try {
            mc = Minecraft.getInstance();
        } catch (Exception ignored) {
            return;
        }
        Level level = mc.level;
        if (level == null) {
            return;
        }
        LightFieldStore store = LightFieldStore.forLevel(level);
        Set<Integer> dirtySections = new HashSet<>();
        try {
            for (Map.Entry<Long, ChromaSection> e : store.sections().entrySet()) {
                long key = e.getKey();
                if (SectionPos.x(key) == payload.chunkX() && SectionPos.z(key) == payload.chunkZ()) {
                    dirtySections.add(SectionPos.y(key));
                }
            }
        } catch (Exception ignored) {
        }
        store.evictChunk(payload.chunkX(), payload.chunkZ());
        if (payload.sections() != null && !payload.sections().isEmpty()) {
            store.decodeChunk(payload.chunkX(), payload.chunkZ(), payload.sections());
            try {
                for (var blob : payload.sections()) {
                    if (blob != null) {
                        dirtySections.add(blob.y());
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (dirtySections.isEmpty() || mc.levelRenderer == null) {
            return;
        }
        for (int sy : dirtySections) {
            try {
                mc.levelRenderer.setSectionDirty(payload.chunkX(), sy, payload.chunkZ());
            } catch (Exception ignored) {
            }
        }
    }
}
