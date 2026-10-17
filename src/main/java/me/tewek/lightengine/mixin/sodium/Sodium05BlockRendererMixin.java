package me.tewek.lightengine.mixin.sodium;

import me.tewek.lightengine.client.Sodium05TintHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sodium 0.5 terrain hue tint (1.20.x, client only, optional). Sodium 0.5
 * still ships in the {@code me.jellysquid} package, so the 0.6+ hook in
 * {@link SodiumBlockRendererMixin} never matches it. Targets by name only and
 * coerces every Sodium/Minecraft type to {@code Object}: nothing here needs
 * remapping, so dev and production behave the same, and a different Sodium
 * build can only skip silently ({@code require = 0}).
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public abstract class Sodium05BlockRendererMixin {
    @Inject(method = "writeGeometry", at = @At("HEAD"), remap = false, require = 0)
    private void lightengine$prepare(@Coerce Object ctx, @Coerce Object builder, @Coerce Object offset,
                                     @Coerce Object material, @Coerce Object quad, int[] colors,
                                     @Coerce Object light, CallbackInfo ci) {
        Sodium05TintHook.prepare(ctx, quad, colors, light);
    }

    @ModifyVariable(method = "writeGeometry", at = @At("LOAD"), argsOnly = true, remap = false, require = 0)
    private int[] lightengine$colors(int[] colors) {
        return Sodium05TintHook.colors(colors);
    }
}
