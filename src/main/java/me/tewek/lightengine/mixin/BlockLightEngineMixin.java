package me.tewek.lightengine.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.tewek.lightengine.LightProfileRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.BlockLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla-parity opacity for extended light: fully opaque blocks
 * ({@code opacity == 15}) stop it exactly like vanilla stops normal light
 * (the propagating level is returned as the attenuation, so the result is
 * zero), everything else keeps vanilla attenuation. Vanilla levels and the
 * disabled state keep vanilla opacity untouched.
 *
 * <p>The extended check uses both the propagating level and the queue entry
 * level as a fallback: either one above 15 means an extended wave that a
 * fully opaque block must stop. This keeps sealed boxes dark and preserves
 * vanilla attenuation for everything else.
 *
 * <p>SRG targets ({@code propagateIncrease}/{@code getOpacity}/
 * {@code getEmission}, see gen-refmap.py). Pinned with {@code remap = false}.
 * The {@code lambda$...} redirect needs no mapping (synthetic name), and the
 * {@code BlockState.getLightEmission} call inside it is a Forge interface
 * default (never obfuscated), so it stays official.
 */
@Mixin(BlockLightEngine.class)
public abstract class BlockLightEngineMixin {
    @ModifyExpressionValue(
            method = "m_284316_",
            remap = false,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/lighting/BlockLightEngine;m_284404_(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I"))
    private int lightengine$clampOpacityForExtended(int original, long packedPos, long queueEntry, int lightLevel) {
        if (!LightProfileRegistry.isEnabled()) {
            return original;
        }
        if (original != 15) {
            return original;
        }
        int entryLevel;
        try {
            entryLevel = net.minecraft.world.level.lighting.LightEngine.QueueEntry.getFromLevel(queueEntry);
        } catch (Exception ignored) {
            entryLevel = 0;
        }
        int propagating = Math.max(lightLevel, entryLevel);
        if (propagating > 15) {
            return propagating;
        }
        return original;
    }

    /**
     * Engine-path emission: {@code checkBlock} / decrease / increase read the
     * source level through the private {@code getEmission}, so the profiled
     * radius must be visible at its return.
     */
    @Inject(method = "m_284436_", at = @At("RETURN"), cancellable = true, remap = false)
    private void lightengine$profiledEmissionGet(long packedPos, BlockState state,
                                                 CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(LightProfileRegistry.emissionForState(state, cir.getReturnValue()));
    }

    /**
     * Engine-path emission for the source scan: {@code propagateLightSources}
     * queries blocks directly (inside its lambda body), bypassing
     * {@code getEmission}. In Forge 1.20.1 production even the synthetic
     * lambda has an SRG name ({@code m_284140_}, verified against the
     * shipped server jar), so it is targeted directly. The handler's own
     * call below is never redirected (redirects only apply inside the
     * target class), so there is no recursion.
     */
    @Redirect(method = "m_284140_",
            remap = false,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getLightEmission(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)I"))
    private int lightengine$profiledEmissionScan(BlockState state, BlockGetter level, BlockPos pos) {
        return LightProfileRegistry.emissionForState(state, state.getLightEmission(level, pos));
    }
}
