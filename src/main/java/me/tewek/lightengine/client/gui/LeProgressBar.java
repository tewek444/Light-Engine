package me.tewek.lightengine.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Custom progress bar with centered percent text. Fraction below 0 = indeterminate. */
public class LeProgressBar implements LeWidget {
    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected double fraction = -1.0;
    protected String text = "";

    public LeProgressBar(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setFraction(double fraction) {
        this.fraction = fraction;
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
    }

    public void setText(Component text) {
        this.text = text == null ? "" : text.getString();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        LeTheme.frame(graphics, this.x, this.y, this.width, this.height,
                LeTheme.FIELD_BG, LeTheme.FIELD_BORDER);
        if (this.fraction >= 0.0) {
            double frac = Math.clamp(this.fraction, 0.0, 1.0);
            int filled = (int) (frac * (this.width - 2));
            graphics.fill(this.x + 1, this.y + 1, this.x + 1 + filled, this.y + this.height - 1, LeTheme.ACCENT_DARK);
        }
        if (!this.text.isEmpty()) {
            graphics.drawCenteredString(Minecraft.getInstance().font, this.text,
                    this.x + this.width / 2, this.y + (this.height - 8) / 2, LeTheme.TEXT);
        }
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= this.x && mouseX < this.x + this.width
                && mouseY >= this.y && mouseY < this.y + this.height;
    }
}
