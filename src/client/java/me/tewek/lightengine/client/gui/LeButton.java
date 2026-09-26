package me.tewek.lightengine.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/** Custom push-button: flat fill, 1px frame, click sound. */
public class LeButton implements LeWidget {
    public interface Press {
        void onPress(LeButton button);
    }

    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected Component label;
    protected final Press onPress;
    protected boolean active = true;
    protected boolean hovered;

    public LeButton(int x, int y, int width, int height, Component label, Press onPress) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.label = label;
        this.onPress = onPress;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setLabel(Component label) {
        this.label = label;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.hovered = this.isMouseOver(mouseX, mouseY);
        int bg = !this.active ? LeTheme.BTN_DISABLED : this.hovered ? LeTheme.BTN_HOVER : LeTheme.BTN_BG;
        LeTheme.frame(graphics, this.x, this.y, this.width, this.height, bg, LeTheme.BTN_BORDER);
        graphics.centeredText(Minecraft.getInstance().font, this.label,
                this.x + this.width / 2, this.y + (this.height - 8) / 2,
                this.active ? LeTheme.TEXT : LeTheme.TEXT_DIM);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // SDL button numbering (button == 1 is left).
        if (button == LightEditorScreen.LEFT_BUTTON && this.active && this.isMouseOver(mouseX, mouseY)) {
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.onPress.onPress(this);
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
