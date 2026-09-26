package me.tewek.lightengine.client.mixin;

import me.tewek.lightengine.client.gui.LightEditorScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a "Light Engine" button to the pause menu, below the vanilla grid.
 * Opens the in-game light editor with the pause menu as parent.
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void lightengine$addEditorButton(CallbackInfo ci) {
        this.addRenderableWidget(Button.builder(
                        Component.translatable("lightengine.editor.pause_button"),
                        button -> this.minecraft.gui.setScreen(new LightEditorScreen((Screen) (Object) this)))
                .bounds(this.width / 2 - 60, this.height - 28, 120, 20)
                .build());
    }
}
