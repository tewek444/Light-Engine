package me.tewek.lightengine.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Indigo (Fabric API renderer, up to 1.21.4) builds smooth-lighting coordinates
 * from the raw block-light level ({@code level << 4}) instead of
 * {@code LevelRenderer.getLightColor}. Extended levels above 15 overflow the
 * nibble and get masked during AO averaging (25 turns into 9), drawing a dark
 * ring around strong lamps. Clamp the block part to the vanilla maximum, like
 * {@link LevelRendererMixin} does for the vanilla path.
 */
@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.aocalc.AoCalculator", remap = false)
public abstract class IndigoAoLightMixin {
    @Inject(method = "getLightmapCoordinates", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void lightengine$clampBlockLight(CallbackInfoReturnable<Integer> cir) {
        int packed = cir.getReturnValueI();
        int block = (packed & 0xFFFFF) >> 4;
        if (block > 15) {
            cir.setReturnValue((packed & 0xFFF00000) | (15 << 4));
        }
    }
}
