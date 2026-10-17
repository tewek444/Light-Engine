package me.tewek.lightengine.client.gui;

import net.minecraft.client.gui.GuiGraphics;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * Clickable color square (profile color row, picker preview). Shows the
 * supplied color, or flat gray while disabled.
 */
public class LeColorSwatch implements LeWidget {
    protected final int x;
    protected final int y;
    protected final int size;
    protected final IntSupplier color;
    protected final BooleanSupplier enabled;
    protected final Runnable onClick;

    /** Disabled fill: flat gray ("no color" look). */
    public static final int DISABLED_FILL = 0xFF777777;

    public LeColorSwatch(int x, int y, int size, IntSupplier color, BooleanSupplier enabled, Runnable onClick) {
        this.x = x;
        this.y = y;
        this.size = size;
        this.color = color;
        this.enabled = enabled;
        this.onClick = onClick;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean on = this.enabled == null || this.enabled.getAsBoolean();
        int fill = on ? (0xFF000000 | (this.color.getAsInt() & 0xFFFFFF)) : DISABLED_FILL;
        LeTheme.frame(graphics, this.x, this.y, this.size, this.size, fill, LeTheme.PANEL_BORDER);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && (this.enabled == null || this.enabled.getAsBoolean())
                && this.isMouseOver(mouseX, mouseY)) {
            if (this.onClick != null) {
                this.onClick.run();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= this.x && mouseX < this.x + this.size
                && mouseY >= this.y && mouseY < this.y + this.size;
    }
}
