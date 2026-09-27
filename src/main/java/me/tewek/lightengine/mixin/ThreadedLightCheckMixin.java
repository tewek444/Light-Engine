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
 *
 * <p>SRG target: {@code m_7174_} is {@code checkBlock(BlockPos)} (see
 * gen-refmap.py). Pinned with {@code remap = false}.
 */
@Mixin(ThreadedLevelLightEngine.class)
public abstract class ThreadedLightCheckMixin {
    @Inject(method = "m_7174_(Lnet/minecraft/core/BlockPos;)V", at = @At("HEAD"), remap = false)
    private void lightengine$stampCheck(BlockPos pos, CallbackInfo ci) {
        LightCheckStamp.touch();
    }
}
