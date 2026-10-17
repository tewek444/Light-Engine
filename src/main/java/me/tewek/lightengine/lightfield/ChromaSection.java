package me.tewek.lightengine.lightfield;

import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Dense 16x16x16 chroma block, v3 layout (Alpha4).
 *
 * <p>Cells are addressed in Morton (Z-order) sequence, same permutation as the
 * retired v2 {@code LuminanceSection}. Each cell stores packed RGB as three
 * bytes (R, G, B 0-255); zero (black) means "no extended light". The logical
 * level of a cell is {@code max(R, G, B)}, so vanilla logic (mob spawning,
 * {@code getBrightness}) keeps working by reading the max channel.</p>
 *
 * <p>Wire/disk form is zlib-compressed {@code 4096 * 3} bytes in Morton order.
 * Alpha4 ships grayscale (R=G=B=level) until the true RGB flood lands; the
 * format already carries color soPEP configs, network and NBT don't churn again.</p>
 */
public final class ChromaSection {
    public static final int VOLUME = 16 * 16 * 16;
    public static final int MAX_CHANNEL = 255;
    public static final int RAW_SIZE = VOLUME * 3;

    private final byte[] cells = new byte[RAW_SIZE];

    public static int morton(int x, int y, int z) {
        int code = 0;
        for (int b = 0; b < 4; b++) {
            code |= ((x >> b) & 1) << (3 * b)
                    | ((y >> b) & 1) << (3 * b + 1)
                    | ((z >> b) & 1) << (3 * b + 2);
        }
        return code;
    }

    private static int base(int x, int y, int z) {
        return morton(x & 15, y & 15, z & 15) * 3;
    }

    public int getR(int x, int y, int z) {
        return cells[base(x, y, z)] & 0xFF;
    }

    public int getG(int x, int y, int z) {
        return cells[base(x, y, z) + 1] & 0xFF;
    }

    public int getB(int x, int y, int z) {
        return cells[base(x, y, z) + 2] & 0xFF;
    }

    public int getPacked(int x, int y, int z) {
        int b = base(x, y, z);
        return ((cells[b] & 0xFF) << 16) | ((cells[b + 1] & 0xFF) << 8) | (cells[b + 2] & 0xFF);
    }

    /** Logical level: max channel, 0-255. */
    public int getLevel(int x, int y, int z) {
        int b = base(x, y, z);
        int r = cells[b] & 0xFF;
        int g = cells[b + 1] & 0xFF;
        int bl = cells[b + 2] & 0xFF;
        return Math.max(r, Math.max(g, bl));
    }

    public void setRGB(int x, int y, int z, int r, int g, int b) {
        int base = base(x, y, z);
        cells[base] = (byte) net.minecraft.util.Mth.clamp(r, 0, MAX_CHANNEL);
        cells[base + 1] = (byte) net.minecraft.util.Mth.clamp(g, 0, MAX_CHANNEL);
        cells[base + 2] = (byte) net.minecraft.util.Mth.clamp(b, 0, MAX_CHANNEL);
    }

    public void setLevel(int x, int y, int z, int level) {
        int clamped = net.minecraft.util.Mth.clamp(level, 0, MAX_CHANNEL);
        setRGB(x, y, z, clamped, clamped, clamped);
    }

    public boolean isEmpty() {
        for (byte v : cells) {
            if (v != 0) {
                return false;
            }
        }
        return true;
    }

    /** Zlib-compressed snapshot in Morton order. */
    public byte[] deflate() {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
        try {
            deflater.setInput(cells);
            deflater.finish();
            byte[] buf = new byte[RAW_SIZE + 64];
            int n = deflater.deflate(buf);
            while (!deflater.finished()) {
                byte[] grown = new byte[buf.length * 2];
                System.arraycopy(buf, 0, grown, 0, n);
                n += deflater.deflate(grown, n, grown.length - n);
                buf = grown;
            }
            return Arrays.copyOf(buf, n);
        } finally {
            deflater.end();
        }
    }

    /** Inverse of {@link #deflate}; {@code null} on corrupt input. */
    public static ChromaSection inflate(byte[] blob) {
        if (blob == null || blob.length == 0 || blob.length > RAW_SIZE + 65536) {
            return null;
        }
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(blob);
            byte[] raw = new byte[RAW_SIZE];
            int n;
            try {
                n = inflater.inflate(raw);
            } catch (DataFormatException ex) {
                return null;
            }
            if (n != RAW_SIZE || !inflater.finished()) {
                return null;
            }
            ChromaSection section = new ChromaSection();
            System.arraycopy(raw, 0, section.cells, 0, RAW_SIZE);
            return section.isEmpty() ? null : section;
        } finally {
            inflater.end();
        }
    }
}
