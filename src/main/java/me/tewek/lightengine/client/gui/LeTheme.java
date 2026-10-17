package me.tewek.lightengine.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shared palette: flat fills + 1px frames, no vanilla widgets involved. */
public final class LeTheme {
    private LeTheme() {
    }

    public static final int DIM = 0x80000000;
    public static final int PANEL_BG = 0xFF181820;
    public static final int PANEL_BORDER = 0xFF55555F;
    public static final int FIELD_BG = 0xFF0C0C10;
    public static final int FIELD_BORDER = 0xFF55555F;
    public static final int FIELD_FOCUSED = 0xFFFFFFFF;
    public static final int BTN_BG = 0xFF3A3A44;
    public static final int BTN_HOVER = 0xFF50505E;
    public static final int BTN_BORDER = 0xFF8B8B96;
    public static final int BTN_DISABLED = 0xFF24242A;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_DIM = 0xFFAAAAAA;
    public static final int ACCENT = 0xFFFFD866;
    public static final int ACCENT_DARK = 0xFF4A3F16;
    public static final int ROW_HOVER = 0xFF2C2C36;
    public static final int SCROLL_BG = 0xFF0C0C10;
    public static final int SCROLL_THUMB = 0xFF6E6E7A;
    public static final int GOOD = 0xFF55FF55;
    public static final int WARN = 0xFFFFDD55;
    public static final int BAD = 0xFFFF5555;

    public static void frame(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int bg, int border) {
        graphics.fill(x, y, x + w, y + h, bg);
        graphics.fill(x, y, x + w, y + 1, border);
        graphics.fill(x, y + h - 1, x + w, y + h, border);
        graphics.fill(x, y, x + 1, y + h, border);
        graphics.fill(x + w - 1, y, x + w, y + h, border);
    }

    public static void centeredText(GuiGraphicsExtractor graphics, String text, int x, int y, int w, int color) {
        graphics.centeredText(Minecraft.getInstance().font, text, x + w / 2, y, color);
    }
}
