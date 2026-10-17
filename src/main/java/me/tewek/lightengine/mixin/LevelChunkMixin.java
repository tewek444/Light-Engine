package me.tewek.lightengine.mixin;

import me.tewek.lightengine.lightfield.ChromaTint;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Funnel for every block change: O(1) emitter-map maintenance, both sides.
 * No field resync here: the client recomputes block light itself from the
 * same blocks and profiles (like vanilla nibbles). A server snapshot sent
 * mid-update raced that local computation and left stale light behind.
 * Worldgen never reaches here with a real {@code Level} (it uses
 * {@code WorldGenRegion}, which is not a {@code Level}), so fresh terrain is
 * covered by the lazy chunk scan instead. Read-only against the level
 * (no recursion into setBlock).
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
    @Shadow
    @Final
    Level level;

    @Inject(method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN"))
    private void lightengine$onSetBlock(BlockPos pos, BlockState newState, boolean isMoving,
                                        CallbackInfoReturnable<BlockState> cir) {
        try {
            ChromaTint.onBlockChanged(level, pos, cir.getReturnValue(), newState);
        } catch (Exception ignored) {
        }
    }
}
