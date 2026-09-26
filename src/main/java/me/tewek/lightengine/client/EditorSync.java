package me.tewek.lightengine.client;

import me.tewek.lightengine.LightEngine;
import me.tewek.lightengine.LightProfileRegistry;
import me.tewek.lightengine.network.ProfileFileStore;
import me.tewek.lightengine.network.ProfileUpdatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Editor apply routing. Without a live server connection (title screen) or
 * on singleplayer (integrated server shares this JVM, registry and file),
 * applies locally; on a remote modded server deltas go through the
 * {@code profile_update} packet and the server stays authoritative.
 */
public final class EditorSync {
    private EditorSync() {
    }

    private static boolean canSendToServer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() == null && mc.getConnection() != null
                && mc.getConnection() instanceof ICommonPacketListener listener) {
            try {
                return listener.hasChannel(ProfileUpdatePayload.TYPE);
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    public static void pushDirty(Map<ResourceLocation, LightProfileRegistry.Profile> dirty) {
        if (dirty == null || dirty.isEmpty()) {
            return;
        }
        Map<ResourceLocation, LightProfileRegistry.Profile> copy = new LinkedHashMap<>(dirty);
        boolean canSend = canSendToServer();
        if (!canSend) {
            for (Map.Entry<ResourceLocation, LightProfileRegistry.Profile> e : copy.entrySet()) {
                LightProfileRegistry.Profile p = e.getValue() == null
                        ? new LightProfileRegistry.Profile(0, false) : e.getValue();
                LightProfileRegistry.upsert(e.getKey(), p.lightRadius(), p.ignoreConditions());
            }
            ProfileFileStore.writeProfiles(copy);
            LightEngine.LOGGER.info("LightEngine editor applied {} profile(s) locally", copy.size());
        } else {
            for (Map.Entry<ResourceLocation, LightProfileRegistry.Profile> e : copy.entrySet()) {
                LightProfileRegistry.Profile p = e.getValue() == null
                        ? new LightProfileRegistry.Profile(0, false) : e.getValue();
                LightProfileRegistry.upsert(e.getKey(), p.lightRadius(), p.ignoreConditions());
                PacketDistributor.sendToServer(
                        new ProfileUpdatePayload(e.getKey(), p.lightRadius(), p.ignoreConditions()));
            }
        }
    }

    /** Removes one profile (reset to vanilla emission), same routing as deltas. */
    public static void removeProfile(ResourceLocation id) {
        if (id == null) {
            return;
        }
        if (!canSendToServer()) {
            LightProfileRegistry.remove(id);
            ProfileFileStore.removeProfile(id);
            LightEngine.LOGGER.info("LightEngine editor removed profile {} locally", id);
        } else {
            LightProfileRegistry.remove(id);
            PacketDistributor.sendToServer(new ProfileUpdatePayload(id, -1, false));
        }
    }
}
