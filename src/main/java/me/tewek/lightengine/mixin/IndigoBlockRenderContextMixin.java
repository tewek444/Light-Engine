package me.tewek.lightengine.mixin;

import me.tewek.lightengine.client.SodiumTintHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Terrain colored light under Fabric API's Indigo renderer (the default on
 * Fabric). Indigo meshes every block through its own quad pipeline and never
 * calls {@code ModelBlockRenderer.putQuadData}; its quads use the same
 * FRAPI layout as Sodium's, so the shared reflective hook applies the
 * per-vertex solve right before the quad is buffered (after AO and biome
 * tint). Soft target: skipped silently if Indigo changes or is replaced.
 */
@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AbstractBlockRenderContext", remap = false)
public abstract class IndigoBlockRenderContextMixin {
    @Inject(method = "bufferQuad(Lnet/fabricmc/fabric/impl/client/indigo/renderer/mesh/MutableQuadViewImpl;)V", at = @At(value = "INVOKE", target = "bufferQuad"),
            remap = false, require = 0)
    private void lightengine$indigoTint(@Coerce Object quad, CallbackInfo ci) {
        SodiumTintHook.tintSodiumQuad(this, quad);
    }
}
