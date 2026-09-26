package me.tewek.lightengine.client;

import me.tewek.lightengine.LightEngine;
import me.tewek.lightengine.LightProfileRegistry;
import me.tewek.lightengine.network.ModNetwork;
import me.tewek.lightengine.network.ProfileFileStore;
import me.tewek.lightengine.network.ProfileUpdatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Editor apply routing. Without a live server connection (title screen) or
 * on singleplayer (integrated server shares this JVM, registry and file),
 * applies locally; on a remote server deltas go through the
 * {@code profile_update} packet and the server stays authoritative.
 *
 * <p>Forge 1.20.1 port: a remote vanilla server simply ignores the unknown
 * custom packet, so "connected to remote" is a sufficient send gate.
 */
public final class EditorSync {
    private EditorSync() {
    }

    private static boolean canSendToServer() {
        Minecraft mc = Minecraft.getInstance();
        try {
            return mc.getSingleplayerServer() == null && mc.getConnection() != null;
        } catch (Exception ignored) {
            return false;
        }
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
                ModNetwork.CHANNEL.sendToServer(
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
            ModNetwork.CHANNEL.sendToServer(new ProfileUpdatePayload(id, -1, false));
        }
    }
}
