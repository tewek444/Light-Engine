package me.tewek.lightengine.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Function;

/** Custom integer slider with drag support. */
public class LeSlider implements LeWidget {
    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected final Component label;
    protected final int min;
    protected final int max;
    protected int value;
    protected final Consumer<Integer> onChange;
    protected final Function<Integer, String> formatter;
    protected boolean dragging;
    protected Integer markedValue;
    protected Runnable onRelease;

    public LeSlider(int x, int y, int width, int height, Component label,
                    int min, int max, int value,
                    Consumer<Integer> onChange, Function<Integer, String> formatter) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.label = label;
        this.min = min;
        this.max = Math.max(min + 1, max);
        this.value = Math.min(this.max, Math.max(min, value));
        this.onChange = onChange;
        this.formatter = formatter;
    }

    public int getValue() {
        return this.value;
    }

    public void setValue(int value) {
        this.setValue(value, true);
    }

    public void setValue(int value, boolean fire) {
        int clamped = Math.min(this.max, Math.max(this.min, value));
        if (clamped != this.value) {
            this.value = clamped;
            if (fire) {
                this.onChange.accept(this.value);
            }
        }
    }

    public void setMarkedValue(Integer marked) {
        this.markedValue = marked;
    }

    public void setOnRelease(Runnable onRelease) {
        this.onRelease = onRelease;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean labeled = !this.label.getString().isEmpty();
        int trackY = this.y + (labeled ? 12 : 8);
        if (labeled) {
            String text = this.label.getString() + ": " + this.formatter.apply(this.value);
            graphics.drawString(Minecraft.getInstance().font, text, this.x, this.y, LeTheme.TEXT);
        }
        graphics.fill(this.x, trackY, this.x + this.width, trackY + 4, LeTheme.FIELD_BG);
        double frac = (double) (this.value - this.min) / (double) (this.max - this.min);
        int filled = (int) (frac * this.width);
        graphics.fill(this.x, trackY, this.x + filled, trackY + 4, LeTheme.ACCENT_DARK);
        if (this.markedValue != null) {
            double mfrac = (double) (Math.min(this.max, Math.max(this.min, this.markedValue)) - this.min)
                    / (double) (this.max - this.min);
            int mx = this.x + (int) (mfrac * this.width);
            graphics.fill(mx - 1, trackY - 3, mx + 1, trackY + 7, LeTheme.ACCENT);
        }
        int knobX = this.x + (int) (frac * (this.width - 8));
        boolean hovered = this.dragging || this.isMouseOver(mouseX, mouseY);
        LeTheme.frame(graphics, knobX, trackY - 4, 8, 12,
                hovered ? LeTheme.BTN_HOVER : LeTheme.BTN_BG, LeTheme.BTN_BORDER);
    }

    private void slideTo(double mouseX) {
        double frac = (mouseX - (this.x + 4)) / (double) (this.width - 8);
        this.setValue(this.min + (int) Math.round(frac * (this.max - this.min)));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.isMouseOver(mouseX, mouseY)) {
            this.dragging = true;
            this.slideTo(mouseX);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.dragging && button == 0) {
            this.slideTo(mouseX);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.dragging && button == 0) {
            this.dragging = false;
            if (this.onRelease != null) {
                this.onRelease.run();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= this.x && mouseX < this.x + this.width
                && mouseY >= this.y - 2 && mouseY < this.y + this.height;
    }
}
