package me.tewek.lightengine.client;

import me.tewek.lightengine.lightfield.LightFieldStore;
import me.tewek.lightengine.lightfield.LightSectionRange;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Client-side dump for stale-light diagnosis: counts CLIENT extended cells
 * around the player plus vanilla brightness, prints to chat and the log,
 * and copies the summary to the clipboard. Compare with server-side
 * {@code /ledump}. Y range covers the light padding sections below/above
 * the buildable world.
 */
public final class LeClientDump {
    private LeClientDump() {
    }

    public static int run() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return 0;
        }
        BlockPos center = mc.player == null ? BlockPos.containing(mc.gameRenderer.getMainCamera().getPosition())
                : mc.player.blockPosition();
        LightFieldStore store = LightFieldStore.forLevel(level);
        int r = 40;
        int total = 0;
        int maxV = 0;
        int vanillaLit = 0;
        for (int bx = center.getX() - r; bx <= center.getX() + r; bx++) {
            for (int by = Math.max(LightSectionRange.minY(level), center.getY() - r);
                    by <= Math.min(LightSectionRange.maxY(level), center.getY() + r); by++) {
                for (int bz = center.getZ() - r; bz <= center.getZ() + r; bz++) {
                    long s = SectionPos.asLong(bx >> 4, by >> 4, bz >> 4);
                    int v = store.getCell(s, SectionPos.sectionRelative(bx),
                            SectionPos.sectionRelative(by), SectionPos.sectionRelative(bz));
                    if (v > 0) {
                        total++;
                        if (v > maxV) {
                            maxV = v;
                        }
                    }
                    int w = 0;
                    try {
                        w = level.getBrightness(LightLayer.BLOCK, new BlockPos(bx, by, bz));
                    } catch (Exception ignored) {
                    }
                    if (w > 0) {
                        vanillaLit++;
                    }
                }
            }
        }
        BlockState atCenter;
        try {
            atCenter = level.getBlockState(center);
        } catch (Exception ex) {
            atCenter = null;
        }
        String msg = "LECLIENT center=" + center.getX() + "," + center.getY() + "," + center.getZ()
                + " clientExtendedCells=" + total + " maxExtended=" + maxV
                + " clientVanillaLit=" + vanillaLit + " blockAtCenter=" + atCenter;
        System.out.println(msg);
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(msg), false);
        }
        try {
            mc.keyboardHandler.setClipboard(msg);
        } catch (Exception ignored) {
            // Headless / unexpected environment: chat + log still carry the output.
        }
        return 1;
    }
}
