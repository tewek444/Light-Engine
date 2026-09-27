package me.tewek.lightengine.mixin;

import me.tewek.lightengine.LightProfileRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.extensions.IBlockStateExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Covers the engine path: {@code BlockLightEngine} reads emission through
 * {@code getLightEmission(BlockGetter, BlockPos)} (source scan in
 * {@code propagateLightSources} and {@code getEmission} in check/decrease),
 * so the profiled radius must be visible here as well. The no-arg cached
 * variant is handled by {@link BlockStateBaseMixin}.
 */
@Mixin(IBlockStateExtension.class)
public interface BlockStateExtensionMixin {
    @Inject(method = "getLightEmission(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"), cancellable = true, remap = false)
    default void lightengine$overrideEmission(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        BlockState self = (BlockState) this;
        cir.setReturnValue(LightProfileRegistry.emissionForState(self, cir.getReturnValue()));
    }
}
