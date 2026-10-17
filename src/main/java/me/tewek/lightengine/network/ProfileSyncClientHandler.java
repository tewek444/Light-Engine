package me.tewek.lightengine.network;

import me.tewek.lightengine.LightEngine;
import me.tewek.lightengine.LightProfileRegistry;
import me.tewek.lightengine.lightfield.ChromaTint;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Client-side apply path for {@link ProfileSyncPayload}.
 * Registry only: never touches the file. Re-resolves the emitter map and
 * refreshes meshes around affected emitters so a hue-only edit shows live.
 */
public final class ProfileSyncClientHandler {
    private ProfileSyncClientHandler() {
    }

    public static void handle(ProfileSyncPayload payload) {
        if (payload == null) {
            return;
        }
        try {
            LightProfileRegistry.applySnapshot(payload.profiles());
        } catch (Exception ex) {
            LightEngine.LOGGER.warn("Could not apply profile sync", ex);
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
        try {
            ChromaTint.rescanLevel(level, level);
        } catch (Exception ignored) {
        }
        if (payload.profiles() == null) {
            return;
        }
        for (ResourceLocation id : payload.profiles().keySet()) {
            List<net.minecraft.core.BlockPos> spots;
            try {
                spots = ChromaTint.positionsOf(level, id);
            } catch (Exception ignored) {
                continue;
            }
            for (net.minecraft.core.BlockPos pos : spots) {
                try {
                    mc.levelRenderer.setSectionDirty(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
                } catch (Exception ignored) {
                }
            }
        }
    }
}
