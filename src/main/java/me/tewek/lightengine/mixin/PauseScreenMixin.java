package me.tewek.lightengine.mixin;

import me.tewek.lightengine.client.gui.LightEditorScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a square lantern button to the pause menu, docked to the right of the
 * "Options... / Open to LAN" row (like other mods' icon buttons), with a
 * tooltip. Falls back to a centered button under the menu if that row
 * cannot be found (another mod rebuilt the layout).
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void lightengine$addEditorButton(CallbackInfo ci) {
        Component label = Component.translatable("lightengine.editor.pause_button");
        Button.OnPress open = button -> this.minecraft.gui.setScreen(new LightEditorScreen((Screen) (Object) this));
        AbstractWidget options = null;
        for (GuiEventListener child : this.children()) {
            if (child instanceof AbstractWidget w
                    && w.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                    && "menu.options".equals(tc.getKey())) {
                options = w;
                break;
            }
        }
        if (options == null) {
            this.addRenderableWidget(Button.builder(label, open)
                    .bounds(this.width / 2 - 60, this.height - 28, 120, 20).build());
            return;
        }
        int rowY = options.getY();
        int right = options.getX() + options.getWidth();
        for (GuiEventListener child : this.children()) {
            if (child instanceof AbstractWidget w && w.getY() == rowY) {
                right = Math.max(right, w.getX() + w.getWidth());
            }
        }
        Button icon = new me.tewek.lightengine.client.gui.LanternButton(right + 4, rowY, label, open);
        icon.setTooltip(Tooltip.create(label));
        this.addRenderableWidget(icon);
    }
}
