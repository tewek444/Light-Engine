package me.tewek.lightengine.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-only clamp: the packed block component is folded back to the
 * vanilla maximum so extended levels never overexpose the frame.
 * Sky is passed through untouched. (26.x moved the helper from
 * {@code LevelRenderer.getLightColor} to {@code LightCoordsUtil}.)
 */
@Mixin(LightCoordsUtil.class)
public abstract class LevelRendererMixin {
    @Inject(method = "getLightCoords(Lnet/minecraft/util/LightCoordsUtil$BrightnessGetter;Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"), cancellable = true)
    private static void lightengine$clampBlockLight(LightCoordsUtil.BrightnessGetter getter, BlockAndLightGetter level,
                                                   BlockState state, BlockPos pos,
                                                   CallbackInfoReturnable<Integer> cir) {
        int packed = cir.getReturnValue();
        int block = (packed & 0xFFFF) >> 4;
        if (block > 15) {
            cir.setReturnValue(LightCoordsUtil.pack(15, LightCoordsUtil.sky(packed)));
        }
    }
}
