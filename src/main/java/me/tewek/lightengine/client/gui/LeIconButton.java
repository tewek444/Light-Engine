package me.tewek.lightengine.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Square icon button: same frame/click/sound behavior as {@link LeButton},
 * with a pixel icon instead of a label and an optional round dot
 * (e.g. update available).
 */
public class LeIconButton extends LeButton {
    /** Draws a glyph in an s*s box at (x, y). */
    public interface Icon {
        void draw(GuiGraphicsExtractor graphics, int x, int y, int size);
    }

    private final Icon icon;
    private boolean dot;

    public LeIconButton(int x, int y, int size, Icon icon, Press onPress) {
        super(x, y, size, size, Component.empty(), onPress);
        this.icon = icon;
    }

    public void setDot(boolean dot) {
        this.dot = dot;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.hovered = this.isMouseOver(mouseX, mouseY);
        int bg = !this.active ? LeTheme.BTN_DISABLED : this.hovered ? LeTheme.BTN_HOVER : LeTheme.BTN_BG;
        LeTheme.frame(graphics, this.x, this.y, this.width, this.height, bg, LeTheme.BTN_BORDER);
        int s = 12;
        int ix = this.x + (this.width - s) / 2;
        int iy = this.y + (this.height - s) / 2;
        this.icon.draw(graphics, ix, iy, s);
        if (this.dot) {
            int bx = this.x + this.width - 6;
            int by = this.y + 1;
            int c = LeTheme.GOOD;
            graphics.fill(bx + 1, by, bx + 4, by + 1, c);
            graphics.fill(bx, by + 1, bx + 5, by + 4, c);
            graphics.fill(bx + 1, by + 4, bx + 4, by + 5, c);
        }
    }

    /** Gear glyph, 12x12: six teeth at 60 degrees, dark hub. */
    public static void drawGear(GuiGraphicsExtractor graphics, int x, int y, int s) {
        int c = LeTheme.TEXT;
        graphics.fill(x + 3, y + 4, x + 9, y + 8, c);
        graphics.fill(x + 4, y + 3, x + 8, y + 9, c);
        graphics.fill(x + 5, y + 1, x + 7, y + 3, c);
        graphics.fill(x + 5, y + 9, x + 7, y + 11, c);
        graphics.fill(x + 9, y + 3, x + 11, y + 5, c);
        graphics.fill(x + 9, y + 7, x + 11, y + 9, c);
        graphics.fill(x + 1, y + 7, x + 3, y + 9, c);
        graphics.fill(x + 1, y + 3, x + 3, y + 5, c);
        graphics.fill(x + 5, y + 5, x + 7, y + 7, 0xFF101014);
    }

    /** X glyph, 12x12: single-pixel diagonals, exactly centered. */
    public static void drawCross(GuiGraphicsExtractor graphics, int x, int y, int s) {
        int c = LeTheme.TEXT;
        for (int i = 0; i < 8; i++) {
            graphics.fill(x + 2 + i, y + 2 + i, x + 3 + i, y + 3 + i, c);
            graphics.fill(x + 9 - i, y + 2 + i, x + 10 - i, y + 3 + i, c);
        }
    }

    /** Chevron up (collapse), 12x12. */
    public static void drawChevronUp(GuiGraphicsExtractor graphics, int x, int y, int s) {
        int cx = x + s / 2;
        int c = LeTheme.TEXT;
        graphics.fill(cx - 1, y + 4, cx + 1, y + 6, c);
        graphics.fill(cx - 3, y + 6, cx - 1, y + 8, c);
        graphics.fill(cx + 1, y + 6, cx + 3, y + 8, c);
    }

    /** Chevron down (expand), 12x12. */
    public static void drawChevronDown(GuiGraphicsExtractor graphics, int x, int y, int s) {
        int cx = x + s / 2;
        int c = LeTheme.TEXT;
        graphics.fill(cx - 3, y + 4, cx - 1, y + 6, c);
        graphics.fill(cx + 1, y + 4, cx + 3, y + 6, c);
        graphics.fill(cx - 1, y + 6, cx + 1, y + 8, c);
    }

    /** Download arrow into a tray, 12x12. */
    public static void drawDownload(GuiGraphicsExtractor graphics, int x, int y, int s) {
        int cx = x + s / 2;
        int c = LeTheme.TEXT;
        graphics.fill(cx - 1, y + 1, cx + 1, y + 6, c);
        graphics.fill(cx - 3, y + 4, cx + 3, y + 6, c);
        graphics.fill(cx - 1, y + 6, cx + 1, y + 8, c);
        graphics.fill(cx - 5, y + 10, cx + 5, y + 11, c);
        graphics.fill(cx - 5, y + 8, cx - 4, y + 11, c);
        graphics.fill(cx + 4, y + 8, cx + 5, y + 11, c);
    }

    public static Component dotTooltip() {
        String remote = UpdateChecker.remoteVersion();
        if (UpdateChecker.getState() == UpdateChecker.State.AVAILABLE && !remote.isEmpty()) {
            return Component.translatable("lightengine.editor.update_available", remote);
        }
        return Component.translatable("lightengine.editor.update_tooltip");
    }

    public static void renderTooltip(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY,
                                     int screenW, int screenH, Component text) {
        String raw = text.getString();
        int w = Math.max(font.width(raw), 8) + 10;
        int h = 18;
        int tx = Math.min(mouseX + 12, screenW - w - 4);
        int ty = Math.min(mouseY + 12, screenH - h - 4);
        graphics.nextStratum();
        LeTheme.frame(graphics, tx, ty, w, h, 0xF0101014, LeTheme.PANEL_BORDER);
        graphics.text(font, raw, tx + 5, ty + 5, LeTheme.TEXT);
    }
}
