package me.tewek.lightengine.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.tewek.lightengine.LightProfileRegistry;
import net.minecraft.world.level.lighting.BlockLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

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
 * <p>Note: signal gating lives in {@link LightEngineMixin} (enqueue gate)
 * and the state-aware emission hook — this mixin must not shadow inherited
 * engine fields.
 */
@Mixin(BlockLightEngine.class)
public abstract class BlockLightEngineMixin {
    @ModifyExpressionValue(
            method = "propagateIncrease",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/lighting/BlockLightEngine;getOpacity(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I"))
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
}
