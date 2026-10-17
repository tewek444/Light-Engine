package me.tewek.lightengine.mixin;

import me.tewek.lightengine.LightCheckStamp;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stamps every queued block-light check so the pre-save drain can wait out
 * mailbox transit (see {@link LightCheckStamp}). No behavior change.
 */
@Mixin(ThreadedLevelLightEngine.class)
public abstract class ThreadedLightCheckMixin {
    @Inject(method = "checkBlock(Lnet/minecraft/core/BlockPos;)V", at = @At("HEAD"))
    private void lightengine$stampCheck(BlockPos pos, CallbackInfo ci) {
        LightCheckStamp.touch();
    }
}
