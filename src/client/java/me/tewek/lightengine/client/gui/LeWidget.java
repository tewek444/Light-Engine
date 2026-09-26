package me.tewek.lightengine.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Minimal custom widget contract. Vanilla {@code Button}/{@code Checkbox}/
 * {@code EditBox} are intentionally not used anywhere in the editor;
 * widgets only rely on raw {@link GuiGraphics} primitives.
 */
public interface LeWidget {
    void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    default boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    default boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return false;
    }

    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    default boolean charTyped(char codePoint, int modifiers) {
        return false;
    }

    default boolean isMouseOver(double mouseX, double mouseY) {
        return false;
    }

    default void setFocused(boolean focused) {
    }

    default boolean isFocused() {
        return false;
    }
}
