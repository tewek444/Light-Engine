package me.tewek.lightengine.network;

import io.netty.handler.codec.DecoderException;
import me.tewek.lightengine.lightfield.SectionBlob;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Server -> client chunk light-field data (v3 section blobs). */
public record LightFieldPayload(int chunkX, int chunkZ, List<SectionBlob> sections) implements LePayload {
    public static final String PROTOCOL_VERSION = "3";
    private static final int MAX_SECTIONS = 128;
    private static final int MAX_BLOB = 1 << 16;
    public static final ResourceLocation TYPE = new ResourceLocation("lightengine", "light_field_data");

    @Override
    public ResourceLocation id() {
        return TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(chunkX);
        buf.writeVarInt(chunkZ);
        buf.writeVarInt(sections.size());
        for (SectionBlob s : sections) {
            buf.writeVarInt(s.y());
            buf.writeVarInt(s.blob().length);
            buf.writeBytes(s.blob());
        }
    }

    public static LightFieldPayload read(FriendlyByteBuf buf) {
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

    /** Clear signal: client must evict the chunk even though there is no data. */
    public static LightFieldPayload clear(int chunkX, int chunkZ) {
        return new LightFieldPayload(chunkX, chunkZ, List.of());
    }
}
