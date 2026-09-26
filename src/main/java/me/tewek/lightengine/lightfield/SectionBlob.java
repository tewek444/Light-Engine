package me.tewek.lightengine.lightfield;

/**
 * One v2 section on the wire or inside chunk NBT: the section Y plus a
 * zlib-compressed 4096-cell luminance block in Morton order.
 */
public record SectionBlob(int y, byte[] blob) {
    public SectionBlob {
        if (blob == null) {
            throw new IllegalArgumentException("blob");
        }
    }
}
