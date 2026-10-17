package me.tewek.lightengine.mixin;

import me.tewek.lightengine.LightProfileRegistry;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Profile-driven emission: a profiled block reports its configured radius
 * instead of the vanilla value. Original behavior is kept when disabled
 * or when the block has no profile.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {
    @Inject(method = "getLightEmission", at = @At("RETURN"), cancellable = true)
    private void lightengine$overrideEmission(CallbackInfoReturnable<Integer> cir) {
        BlockState self = (BlockState) (Object) this;
        cir.setReturnValue(LightProfileRegistry.emissionForState(self, cir.getReturnValue()));
    }
}
