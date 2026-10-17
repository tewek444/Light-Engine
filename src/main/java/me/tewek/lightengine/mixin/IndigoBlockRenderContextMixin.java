package me.tewek.lightengine.mixin;

import me.tewek.lightengine.client.SodiumTintHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Terrain colored light under Fabric API's Indigo renderer (the default on
 * Fabric). In 26.x Fabric API meshes every block through Indigo's
 * {@code AltModelBlockRendererImpl}, a quad transform that shades and tints
 * each quad and never reaches {@code ModelBlockRenderer}. Its quads use the
 * same FRAPI layout as Sodium's, so the shared reflective hook applies the
 * per-vertex solve right after vanilla shading and biome tint, while vertex
 * positions are still block-local (the block offset is applied after).
 * Soft target: skipped silently if Indigo changes or is replaced.
 */
@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl", remap = false)
public abstract class IndigoBlockRenderContextMixin {
    @Inject(method = "transform", at = @At(value = "INVOKE", target = "tintQuad", shift = At.Shift.AFTER),
            remap = false, require = 0)
    private void lightengine$indigoTint(@Coerce Object quad, CallbackInfoReturnable<Boolean> cir) {
        SodiumTintHook.tintSodiumQuad(this, quad);
    }
}
