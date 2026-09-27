package me.tewek.lightengine.mixin;

import me.tewek.lightengine.LightProfileRegistry;
import me.tewek.lightengine.lightfield.LightField;
import me.tewek.lightengine.lightfield.LightSectionRange;
import me.tewek.lightengine.lightfield.SectionBlob;
import me.tewek.lightengine.network.LightFieldPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Server-only: after a chunk is dispatched, send its extended data as a
 * v2 light-field payload. Sends nothing when the chunk carries no data.
 */
@Mixin(PlayerChunkSender.class)
public abstract class PlayerChunkSenderMixin {
    @Inject(method = "sendChunk(Lnet/minecraft/server/network/ServerGamePacketListenerImpl;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;)V",
            at = @At("RETURN"))
    private static void lightengine$afterSendChunk(ServerGamePacketListenerImpl packetListener,
                                                   ServerLevel level,
                                                   LevelChunk chunk,
                                                   CallbackInfo ci) {
        if (!LightProfileRegistry.isEnabled()) {
            return;
        }
        if (packetListener == null || level == null || chunk == null) {
            return;
        }
        try {
            int chunkX = chunk.getPos().x;
            int chunkZ = chunk.getPos().z;
            List<SectionBlob> sections = LightField.encodeChunk(
                    level, chunkX, chunkZ,
                    LightSectionRange.minSection(level), LightSectionRange.maxSection(level));
            if (sections == null) {
                return;
            }
            LightFieldPayload payload = LightFieldPayload.of(chunkX, chunkZ, sections);
            if (payload == null) {
                return;
            }
            PacketDistributor.sendToPlayer(packetListener.getPlayer(), payload);
        } catch (Exception ignored) {
        }
    }
}
