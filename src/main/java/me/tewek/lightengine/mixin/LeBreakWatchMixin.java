package me.tewek.lightengine.mixin;

import me.tewek.lightengine.LightEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * TEMPORARY break watcher for the stale-light hunt: logs whenever an
 * extended emitter (hooked emission &gt; 15) is replaced by air (or anything
 * dim), with the thread name (server vs render thread tells the sides
 * apart). No behavior change. DELETE AFTER USE.
 */
@Mixin(LevelChunk.class)
public abstract class LeBreakWatchMixin {
    @Inject(method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"))
    private void lightengine$watchBreak(BlockPos pos, BlockState newState, boolean moved,
                                        CallbackInfoReturnable<BlockState> cir) {
        try {
            LevelChunk self = (LevelChunk) (Object) this;
            BlockState oldState = self.getBlockState(pos);
            if (oldState == null || newState == null) {
                return;
            }
            int oldE = oldState.getLightEmission();
            if (oldE <= 15) {
                return;
            }
            int newE = newState.getLightEmission();
            if (newE > 15) {
                return;
            }
            LightEngine.LOGGER.warn("LEBREAK pos={} oldE={} newE={} thread={}",
                    pos.toShortString(), oldE, newE, Thread.currentThread().getName());
        } catch (Exception ignored) {
        }
    }
}
