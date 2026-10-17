package me.tewek.lightengine.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Custom checkbox: 12px box drawn with fills, label on the right. */
public class LeToggle implements LeWidget {
    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected final Component label;
    protected boolean checked;
    protected final Consumer<Boolean> onChange;

    public LeToggle(int x, int y, int width, int height, Component label, boolean checked, Consumer<Boolean> onChange) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.label = label;
        this.checked = checked;
        this.onChange = onChange;
    }

    public boolean isChecked() {
        return this.checked;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int boxY = this.y + (this.height - 12) / 2;
        LeTheme.frame(graphics, this.x, boxY, 12, 12, LeTheme.FIELD_BG, LeTheme.FIELD_BORDER);
        if (this.checked) {
            graphics.fill(this.x + 3, boxY + 3, this.x + 9, boxY + 9, LeTheme.ACCENT);
        }
        boolean hovered = this.isMouseOver(mouseX, mouseY);
        graphics.drawString(Minecraft.getInstance().font, this.label,
                this.x + 17, this.y + (this.height - 8) / 2,
                hovered ? LeTheme.TEXT : LeTheme.TEXT_DIM);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.isMouseOver(mouseX, mouseY)) {
            this.checked = !this.checked;
            this.onChange.accept(this.checked);
            return true;
        }
        return false;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= this.x && mouseX < this.x + this.width
                && mouseY >= this.y && mouseY < this.y + this.height;
    }
}
