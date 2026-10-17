package me.tewek.lightengine.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 20x20 vanilla-framed button showing a lantern item instead of text. */
public class LanternButton extends Button {
    private final ItemStack icon = new ItemStack(Items.LANTERN);

    public LanternButton(int x, int y, Component narration, OnPress onPress) {
        super(x, y, 20, 20, narration, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderDefaultSprite(graphics);
        graphics.renderItem(this.icon, this.getX() + 2, this.getY() + 2);
    }
}
