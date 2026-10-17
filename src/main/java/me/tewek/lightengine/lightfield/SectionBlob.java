package me.tewek.lightengine.lightfield;

/**
 * One v3 section on the wire or inside chunk NBT: the section Y plus a
 * zlib-compressed 12288-byte RGB block (4096 cells x 3) in Morton order.
 */
public record SectionBlob(int y, byte[] blob) {
    public SectionBlob {
        if (blob == null) {
            throw new IllegalArgumentException("blob");
        }
    }
}
