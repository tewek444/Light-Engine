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
 * Packed-light clamp: the block component is folded back to the vanilla
 * maximum so extended levels never overexpose the frame. Sky is passed
 * through untouched.
 *
 * <p>26.x note: replaces the old {@code LevelRenderer.getLightColor} hook —
 * that method is gone; packed light for rendering now flows through
 * {@code LightCoordsUtil.getLightCoords}, which is the new choke point.
 */
@Mixin(LightCoordsUtil.class)
public abstract class LightCoordsUtilMixin {
    private static int lightengine$foldBlock(int packed) {
        int block = LightCoordsUtil.block(packed);
        return block > 15 ? LightCoordsUtil.withBlock(packed, 15) : packed;
    }

    @Inject(method = "getLightCoords(Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"), cancellable = true)
    private static void lightengine$clampBlockLight(BlockAndLightGetter level, BlockPos pos,
                                                    CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(lightengine$foldBlock(cir.getReturnValue()));
    }

    @Inject(method = "getLightCoords(Lnet/minecraft/util/LightCoordsUtil$BrightnessGetter;Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"), cancellable = true)
    private static void lightengine$clampBlockLightEx(LightCoordsUtil.BrightnessGetter getter,
                                                      BlockAndLightGetter level, BlockState state, BlockPos pos,
                                                      CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(lightengine$foldBlock(cir.getReturnValue()));
    }
}
