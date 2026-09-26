package me.tewek.lightengine.mixin;

import me.tewek.lightengine.LightProfileRegistry;
import me.tewek.lightengine.lightfield.LightField;
import me.tewek.lightengine.lightfield.LightSectionRange;
import me.tewek.lightengine.lightfield.SectionBlob;
import me.tewek.lightengine.network.LightFieldPayload;
import me.tewek.lightengine.network.ModNetwork;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Server-only: right after a chunk packet is queued for a player, send its
 * extended data as a v2 light-field payload. Sends nothing when the chunk
 * carries no data.
 *
 * <p>Forge 1.20.1 port: 1.20.1 has no {@code PlayerChunkSender} (added in
 * 1.21), chunk packets go out through {@code ServerGamePacketListenerImpl#send},
 * so this mixin hooks that instead with the same after-chunk timing.
 *
 * <p>SRG target: {@code m_9829_} is {@code send(Packet)} (see gen-refmap.py).
 * Pinned with {@code remap = false}.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ChunkSendMixin {
    @Inject(method = "m_9829_(Lnet/minecraft/network/protocol/Packet;)V", at = @At("RETURN"), remap = false)
    private void lightengine$afterSend(Packet<?> packet, CallbackInfo ci) {
        if (!(packet instanceof ClientboundLevelChunkWithLightPacket chunkPacket)) {
            return;
        }
        if (!LightProfileRegistry.isEnabled()) {
            return;
        }
        ServerPlayer player;
        try {
            player = ((ServerGamePacketListenerImpl) (Object) this).getPlayer();
        } catch (Exception ex) {
            return;
        }
        if (player == null || player.level() == null) {
            return;
        }
        try {
            ServerLevel level = (ServerLevel) player.level();
            int chunkX = chunkPacket.getX();
            int chunkZ = chunkPacket.getZ();
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
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
        } catch (Exception ignored) {
        }
    }
}
