package me.tewek.lightengine.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Custom single-line label, optionally centered. */
public class LeLabel implements LeWidget {
    protected int x;
    protected int y;
    protected int width;
    protected Component text;
    protected int color;
    protected boolean centered;

    public LeLabel(int x, int y, int width, Component text, int color, boolean centered) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.text = text;
        this.color = color;
        this.centered = centered;
    }

    public void setText(Component text) {
        this.text = text;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (this.centered) {
            graphics.centeredText(Minecraft.getInstance().font, this.text,
                    this.x + this.width / 2, this.y, this.color);
        } else {
            graphics.text(Minecraft.getInstance().font, this.text, this.x, this.y, this.color);
        }
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return false;
    }
}
