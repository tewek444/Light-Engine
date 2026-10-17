package me.tewek.lightengine.client;

import me.tewek.lightengine.lightfield.ChromaTint;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.LevelHeightAccessor;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-only halo refresh (never loaded on a dedicated server — callers
 * guard the call with try/catch and a permanent disable flag).
 *
 * <p>Why it exists: chunk meshes bake tint reads. When an emitter becomes
 * known AFTER neighboring surfaces already meshed — the join-order race (a
 * chunk meshed before its neighbor's scan ran), a wide-radius placement the
 * vanilla dirty does not cover, or a removal leaving stale tint — those
 * surfaces keep baking white (or stale color) until something re-meshes
 * them. F3+A papered over it manually; this does the same targeted refresh
 * automatically: dirty every section overlapped by each new emitter's halo
 * cube on the client thread. Meshes re-bake, queries now hit the map, and
 * the refresh itself registers nothing new, so it always terminates.</p>
 */
public final class HaloRefresh {
    private HaloRefresh() {
    }

    /** Sanity cap per scheduled run (degenerate storms must not stall meshing). */
    private static final int MAX_SECTIONS_PER_RUN = 16384;

    /**
     * @param levelKey the level the emitters belong to (stale runs quit: if
     *                 the player already disconnected, {@code mc.level} no
     *                 longer matches and nothing is dirtied).
     * @param added    newly registered emitters with immutable positions.
     */
    public static void schedule(Object levelKey, List<ChromaTint.Added> added) {
        if (levelKey == null || added == null || added.isEmpty()) {
            return;
        }
        Minecraft mc;
        try {
            mc = Minecraft.getInstance();
        } catch (Throwable ignored) {
            return;
        }
        if (mc == null || mc.level != levelKey) {
            return;
        }
        List<int[]> boxes = new ArrayList<>(added.size());
        for (ChromaTint.Added a : added) {
            if (a == null || a.pos() == null || a.radius() <= 0) {
                continue;
            }
            boxes.add(new int[]{a.pos().getX(), a.pos().getY(), a.pos().getZ(), a.radius()});
        }
        if (boxes.isEmpty()) {
            return;
        }
        try {
            mc.execute(() -> runDirty(mc, levelKey, boxes));
        } catch (Throwable ignored) {
        }
    }

    private static void runDirty(Minecraft mc, Object levelKey, List<int[]> boxes) {
        try {
            if (mc.level != levelKey || mc.levelRenderer == null) {
                return;
            }
            LevelHeightAccessor height = mc.level;
            int minS;
            int maxS;
            try {
                minS = height.getMinSectionY() - 1;
                maxS = height.getMaxSectionY() + 1 + 1;
            } catch (Throwable ignored) {
                return;
            }
            // Sections outside the render distance have no mesh to re-bake.
            int view = mc.options.getEffectiveRenderDistance() + 1;
            int camX = mc.gameRenderer.getMainCamera().blockPosition().getX() >> 4;
            int camZ = mc.gameRenderer.getMainCamera().blockPosition().getZ() >> 4;
            int total = 0;
            java.util.HashSet<Long> seen = new java.util.HashSet<>();
            for (int[] b : boxes) {
                // +2: vertices sample up to ~1.5 blocks past the radius.
                int r = b[3] + 4;
                int sx0 = (b[0] - r) >> 4;
                int sx1 = (b[0] + r) >> 4;
                int sy0 = Math.max((b[1] - r) >> 4, minS);
                int sy1 = Math.min((b[1] + r) >> 4, maxS);
                int sz0 = (b[2] - r) >> 4;
                int sz1 = (b[2] + r) >> 4;
                for (int sx = Math.max(sx0, camX - view); sx <= Math.min(sx1, camX + view); sx++) {
                    for (int sy = sy0; sy <= sy1; sy++) {
                        for (int sz = Math.max(sz0, camZ - view); sz <= Math.min(sz1, camZ + view); sz++) {
                            if (!seen.add(net.minecraft.core.SectionPos.asLong(sx, sy, sz))) {
                                continue;
                            }
                            try {
                                mc.levelRenderer.setSectionDirty(sx, sy, sz);
                            } catch (Throwable ignored) {
                            }
                            if (++total >= MAX_SECTIONS_PER_RUN) {
                                return;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
