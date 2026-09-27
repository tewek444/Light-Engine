package me.tewek.lightengine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.LightLayer;

/**
 * Debug overlay toggled by {@code /lightengine light_level}.
 * Draws the incoming block-light value on every visible face of every
 * non-air block in a radius around the camera:
 * green = 8+ (safe), yellow = 1-7, red = 0 (hostile mobs can spawn).
 * Extended values above 15 are shown as-is.
 */
public final class LightOverlayRenderer {
    private LightOverlayRenderer() {
    }

    private static volatile boolean enabled = false;
    private static final int RADIUS = 16;
    private static final int RADIUS_SQ = RADIUS * RADIUS;
    private static final float GLYPH_SCALE = 0.025F;
    private static final int FULL_BRIGHT = 0xF000F0;

    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void render(PoseStack pose, double camX, double camY, double camZ,
                               MultiBufferSource buffer) {
        if (!enabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            return;
        }
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        Font font = mc.font;
        BlockPos center = BlockPos.containing(camX, camY, camZ);
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    if (dx * dx + dy * dy + dz * dz > RADIUS_SQ) {
                        continue;
                    }
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (level.getBlockState(pos).isAir()) {
                        continue;
                    }
                    for (Direction dir : Direction.values()) {
                        BlockPos npos = pos.relative(dir);
                        if (level.getBlockState(npos).isSolidRender(level, npos)) {
                            continue;
                        }
                        int value = level.getBrightness(LightLayer.BLOCK, npos);
                        drawFace(pose, buffer, font, pos, dir, camX, camY, camZ, value);
                    }
                }
            }
        }
    }

    private static void drawFace(PoseStack pose, MultiBufferSource buffer, Font font,
                                 BlockPos pos, Direction dir,
                                 double camX, double camY, double camZ, int value) {
        double fx = pos.getX() + 0.5 + dir.getStepX() * 0.51 - camX;
        double fy = pos.getY() + 0.5 + dir.getStepY() * 0.51 - camY;
        double fz = pos.getZ() + 0.5 + dir.getStepZ() * 0.51 - camZ;
        pose.pushPose();
        pose.translate(fx, fy, fz);
        switch (dir) {
            case NORTH -> pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            case EAST -> pose.mulPose(Axis.YP.rotationDegrees(90.0F));
            case WEST -> pose.mulPose(Axis.YP.rotationDegrees(270.0F));
            case UP -> pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
            case DOWN -> pose.mulPose(Axis.XP.rotationDegrees(90.0F));
            default -> {
            }
        }
        pose.scale(GLYPH_SCALE, -GLYPH_SCALE, GLYPH_SCALE);
        String text = Integer.toString(value);
        float w = font.width(text);
        int color = value <= 0 ? 0xFF5555 : value <= 7 ? 0xFFDD55 : 0x55FF55;
        font.drawInBatch(text, -w / 2.0F, -4.0F, color, false,
                pose.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, FULL_BRIGHT);
        pose.popPose();
    }

    public static void announce(boolean on) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.translatable(on ? "lightengine.overlay.on" : "lightengine.overlay.off"),
                    false);
        }
    }
}
