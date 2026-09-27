package me.tewek.lightengine.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-only clamp: the packed block component is folded back to the
 * vanilla maximum so extended levels never overexpose the frame.
 * Sky is passed through untouched.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"), cancellable = true)
    private static void lightengine$clampBlockLight(BlockAndTintGetter level, BlockState state, BlockPos pos,
                                                   CallbackInfoReturnable<Integer> cir) {
        int packed = cir.getReturnValue();
        int block = LightTexture.block(packed);
        if (block > 15) {
            cir.setReturnValue(LightTexture.pack(15, LightTexture.sky(packed)));
        }
    }
}
