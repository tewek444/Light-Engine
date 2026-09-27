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
 *
 * <p>Forge 1.20.1 production runs vanilla members in SRG names, so the
 * target below is the SRG name of {@code getLightEmission} (see
 * gen-refmap.py). {@code remap = false} pins it: no refmap involved.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {
    @Inject(method = "m_60791_", at = @At("RETURN"), cancellable = true, remap = false)
    private void lightengine$overrideEmission(CallbackInfoReturnable<Integer> cir) {
        BlockState self = (BlockState) (Object) this;
        cir.setReturnValue(LightProfileRegistry.emissionForState(self, cir.getReturnValue()));
    }
}
