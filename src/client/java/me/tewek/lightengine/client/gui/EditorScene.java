package me.tewek.lightengine.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.block.Block;

/**
 * Diorama placeholder. The scene is intentionally cleared for now;
 * a redesigned preview will be built here later.
 */
public final class EditorScene {
    private EditorScene() {
    }

    public static final int GRID = 16;

    public static int valueAt(int radius, int gx, int gz) {
        int d = Math.max(Math.abs(gx - 7), Math.abs(gz - 7));
        return Math.max(0, radius - d);
    }

    public static int colorFor(int value) {
        return value <= 0 ? 0xFF5555 : value <= 7 ? 0xFFDD55 : 0x55FF55;
    }

    public static void render(GuiGraphics graphics, int x, int y, int w, int h,
                              Block center, int radius, boolean showLevels) {
        LeTheme.frame(graphics, x, y, w, h, 0xFF101014, LeTheme.PANEL_BORDER);
    }
}
