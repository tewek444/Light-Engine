package me.tewek.lightengine.client;

import me.tewek.lightengine.LightEngine;
import me.tewek.lightengine.LightProfileRegistry;
import me.tewek.lightengine.network.ProfileFileStore;
import me.tewek.lightengine.network.ProfileUpdatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

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
        if (mc.getSingleplayerServer() == null && mc.getConnection() != null) {
            try {
                return me.tewek.lightengine.platform.ClientPlatform.serverAccepts(ProfileUpdatePayload.TYPE);
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
                        ? new LightProfileRegistry.Profile(0, false, LightProfileRegistry.DEFAULT_COLOR) : e.getValue();
                LightProfileRegistry.upsert(e.getKey(), p.lightRadius(), p.ignoreConditions(), p.color(),
                        p.tintEnabled());
            }
            ProfileFileStore.writeProfiles(copy);
            LightEngine.LOGGER.info("LightEngine editor applied {} profile(s) locally", copy.size());
            refreshClient(copy);
        } else {
            for (Map.Entry<ResourceLocation, LightProfileRegistry.Profile> e : copy.entrySet()) {
                LightProfileRegistry.Profile p = e.getValue() == null
                        ? new LightProfileRegistry.Profile(0, false, LightProfileRegistry.DEFAULT_COLOR) : e.getValue();
                LightProfileRegistry.upsert(e.getKey(), p.lightRadius(), p.ignoreConditions(), p.color(),
                        p.tintEnabled());
                me.tewek.lightengine.platform.ClientPlatform.sendToServer(
                        new ProfileUpdatePayload(e.getKey(), p.lightRadius(), p.ignoreConditions(), p.color(),
                                p.tintEnabled()));
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
            refreshClient(java.util.Map.of(id,
                    new LightProfileRegistry.Profile(0, false, LightProfileRegistry.DEFAULT_COLOR)));
        } else {
            LightProfileRegistry.remove(id);
            me.tewek.lightengine.platform.ClientPlatform.sendToServer(
                    new ProfileUpdatePayload(id, -1, false, LightProfileRegistry.DEFAULT_COLOR, true));
        }
    }

    /**
     * Re-resolves the client emitter map and dirties sections around affected
     * emitters, so a hue-only edit shows without any block change (vanilla
     * waves don't fire for config edits).
     */
    private static void refreshClient(Map<ResourceLocation, LightProfileRegistry.Profile> changed) {
        Minecraft mc;
        try {
            mc = Minecraft.getInstance();
        } catch (Exception ignored) {
            return;
        }
        if (mc == null || mc.level == null) {
            return;
        }
        try {
            me.tewek.lightengine.lightfield.ChromaTint.rescanLevel(mc.level, mc.level);
        } catch (Exception ignored) {
        }
        if (changed == null) {
            return;
        }
        java.util.HashSet<Long> sections = new java.util.HashSet<>();
        for (ResourceLocation id : changed.keySet()) {
            java.util.List<net.minecraft.core.BlockPos> spots;
            try {
                spots = me.tewek.lightengine.lightfield.ChromaTint.positionsOf(mc.level, id);
            } catch (Exception ignored) {
                continue;
            }
            for (net.minecraft.core.BlockPos pos : spots) {
                long key = net.minecraft.core.SectionPos.asLong(
                        pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
                if (!sections.add(key)) {
                    continue;
                }
                try {
                    mc.levelRenderer.setSectionDirty(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
                } catch (Exception ignored) {
                }
            }
        }
    }
}
