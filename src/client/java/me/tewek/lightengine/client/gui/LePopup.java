package me.tewek.lightengine.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom modal popup: dims the screen, draws a centered framed panel with
 * a title, and routes all input to its own children while open.
 */
public class LePopup {
    protected final int width;
    protected final int height;
    protected final Component title;
    protected final List<LeWidget> children = new ArrayList<>();
    protected boolean open = true;
    /** Fixed panel position (< 0 = centered). */
    protected int fixedX = -1;
    protected int fixedY = -1;
    /** Whether to dim the screen behind (context menus don't). */
    protected boolean dim = true;
    /** Whether a click outside children closes the popup. */
    protected boolean closeOnOutsideClick;

    public LePopup(int width, int height, Component title) {
        this.width = width;
        this.height = height;
        this.title = title;
    }

    public void add(LeWidget widget) {
        this.children.add(widget);
    }

    public boolean isOpen() {
        return this.open;
    }

    public void close() {
        this.open = false;
    }

    public void setPosition(int x, int y) {
        this.fixedX = x;
        this.fixedY = y;
    }

    public void setDim(boolean dim) {
        this.dim = dim;
    }

    public void setCloseOnOutsideClick(boolean close) {
        this.closeOnOutsideClick = close;
    }

    public int panelX(int screenWidth) {
        return this.fixedX >= 0 ? this.fixedX : (screenWidth - this.width) / 2;
    }

    public int panelY(int screenHeight) {
        return this.fixedY >= 0 ? this.fixedY : (screenHeight - this.height) / 2;
    }

    public void renderBackground(GuiGraphics graphics, int screenWidth, int screenHeight) {
        if (this.dim) {
            graphics.fill(0, 0, screenWidth, screenHeight, LeTheme.DIM);
        }
        int px = this.panelX(screenWidth);
        int py = this.panelY(screenHeight);
        LeTheme.frame(graphics, px, py, this.width, this.height, LeTheme.PANEL_BG, LeTheme.PANEL_BORDER);
        graphics.drawCenteredString(Minecraft.getInstance().font, this.title,
                px + this.width / 2, py + 8, LeTheme.TEXT);
    }

    public void renderWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (LeWidget child : this.children) {
            child.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = this.children.size() - 1; i >= 0; i--) {
            if (this.children.get(i).mouseClicked(mouseX, mouseY, button)) {
                this.syncFocus(this.children.get(i));
                return true;
            }
        }
        if (this.closeOnOutsideClick) {
            this.close();
        }
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        for (int i = this.children.size() - 1; i >= 0; i--) {
            if (this.children.get(i).mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (int i = this.children.size() - 1; i >= 0; i--) {
            if (this.children.get(i).mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        for (int i = this.children.size() - 1; i >= 0; i--) {
            if (this.children.get(i).mouseScrolled(mouseX, mouseY, delta)) {
                return true;
            }
        }
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (LeWidget child : this.children) {
            if (child.isFocused() && child.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        for (LeWidget child : this.children) {
            if (child.isFocused() && child.charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        return false;
    }

    private void syncFocus(LeWidget clicked) {
        for (LeWidget child : this.children) {
            if (child != clicked) {
                child.setFocused(false);
            }
        }
    }
}
