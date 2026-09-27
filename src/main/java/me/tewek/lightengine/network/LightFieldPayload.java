package me.tewek.lightengine.network;

import io.netty.handler.codec.DecoderException;
import me.tewek.lightengine.lightfield.SectionBlob;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -&gt; client chunk payload, v2 format ({@code lightengine:light_field_data}).
 * Hand-encoded section blobs (section Y + zlib bytes); no NBT, no GZIP.
 * An empty section list is an explicit chunk tombstone. {@link #of(int, int, List)}
 * still returns {@code null} when there is no payload to send.
 */
public record LightFieldPayload(int chunkX, int chunkZ, List<SectionBlob> sections) implements CustomPacketPayload {
    public static final String PROTOCOL_VERSION = "2";

    private static final int MAX_SECTIONS = 128;
    private static final int MAX_BLOB = 1 << 16;

    public static final Type<LightFieldPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("lightengine", "light_field_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LightFieldPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeVarInt(payload.chunkX());
                        buf.writeVarInt(payload.chunkZ());
                        buf.writeVarInt(payload.sections().size());
                        for (SectionBlob s : payload.sections()) {
                            buf.writeVarInt(s.y());
                            buf.writeVarInt(s.blob().length);
                            buf.writeBytes(s.blob());
                        }
                    },
                    buf -> {
                        int cx = buf.readVarInt();
                        int cz = buf.readVarInt();
                        int n = buf.readVarInt();
                        if (n < 0 || n > MAX_SECTIONS) {
                            throw new DecoderException("bad section count " + n);
                        }
                        List<SectionBlob> sections = new ArrayList<>(n);
                        for (int i = 0; i < n; i++) {
                            int y = buf.readVarInt();
                            int len = buf.readVarInt();
                            if (len <= 0 || len > MAX_BLOB) {
                                throw new DecoderException("bad blob length " + len);
                            }
                            byte[] blob = new byte[len];
                            buf.readBytes(blob);
                            sections.add(new SectionBlob(y, blob));
                        }
                        return new LightFieldPayload(cx, cz, List.copyOf(sections));
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Builds a chunk-scoped tombstone used to clear a client cache entry. */
    public static LightFieldPayload empty(int chunkX, int chunkZ) {
        return new LightFieldPayload(chunkX, chunkZ, List.of());
    }

    /** Builds the payload; {@code null} when there is nothing (or too much) to send. */
    public static LightFieldPayload of(int chunkX, int chunkZ, List<SectionBlob> sections) {
        if (sections == null || sections.isEmpty() || sections.size() > MAX_SECTIONS) {
            return null;
        }
        for (SectionBlob s : sections) {
            if (s == null || s.blob() == null || s.blob().length == 0 || s.blob().length > MAX_BLOB) {
                return null;
            }
        }
        return new LightFieldPayload(chunkX, chunkZ, List.copyOf(sections));
    }
}
