package me.tewek.lightengine.mixin;

import me.tewek.lightengine.LightProfileRegistry;
import me.tewek.lightengine.lightfield.LightField;
import me.tewek.lightengine.lightfield.LightSectionRange;
import me.tewek.lightengine.lightfield.SectionBlob;
import me.tewek.lightengine.network.LightFieldPayload;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Server-only: right after a chunk packet goes to a player, send its extended
 * light data. 1.20.1 has no {@code PlayerChunkSender} (added in 1.20.2);
 * chunk packets go out through {@code ServerGamePacketListenerImpl#send}.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ChunkSendMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("RETURN"))
    private void lightengine$afterSend(Packet<?> packet, CallbackInfo ci) {
        if (!(packet instanceof ClientboundLevelChunkWithLightPacket chunkPacket) || !LightProfileRegistry.isEnabled()) {
            return;
        }
        ServerPlayer player = ((ServerGamePacketListenerImpl) (Object) this).player;
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        try {
            int chunkX = chunkPacket.getX();
            int chunkZ = chunkPacket.getZ();
            List<SectionBlob> sections = LightField.encodeChunk(level, chunkX, chunkZ,
                    LightSectionRange.minSection(level), LightSectionRange.maxSection(level));
            LightFieldPayload payload = sections == null ? null : LightFieldPayload.of(chunkX, chunkZ, sections);
            if (payload != null) {
                me.tewek.lightengine.platform.Platform.sendToPlayer(player, payload);
            }
        } catch (Exception ignored) {
            // the next field sync resends it
        }
    }
}
