package me.tewek.lightengine.mixin;

import me.tewek.lightengine.LightSaveDrain;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets pending (extended) light floods finish before chunk serialization,
 * so saves never persist a half-cleared halo whose source is already gone.
 *
 * <p>SRG target: {@code m_129885_} is {@code saveAllChunks(ZZZ)Z} in Forge
 * 1.20.1 production (see gen-refmap.py). Pinned with {@code remap = false}.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "m_129885_(ZZZ)Z", at = @At("HEAD"), remap = false)
    private void lightengine$drainLightBeforeSave(boolean suppressLogs, boolean flush, boolean force,
                                                  CallbackInfoReturnable<Boolean> cir) {
        LightSaveDrain.drainBeforeSave((MinecraftServer) (Object) this);
    }
}
