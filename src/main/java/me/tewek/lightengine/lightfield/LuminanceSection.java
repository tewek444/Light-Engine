package me.tewek.lightengine.lightfield;

import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Dense 16x16x16 luminance block, v2 layout.
 *
 * <p>Cells are addressed in Morton (Z-order) sequence instead of the legacy
 * row-major order, so neither the in-memory order nor the persisted bytes
 * match the v1 scheme. Values are 0-255; zero means "no extended light".</p>
 */
public final class LuminanceSection {
    public static final int VOLUME = 16 * 16 * 16;
    public static final int MAX_LEVEL = 255;

    private final byte[] cells = new byte[VOLUME];

    /**
     * Interleaves three 4-bit coordinates into a 12-bit Morton code.
     * Every input bit lands on its own output bit, so this is a bijection
     * over 0-15 inputs (a permutation of 0-4095).
     */
    public static int morton(int x, int y, int z) {
        int code = 0;
        for (int b = 0; b < 4; b++) {
            code |= ((x >> b) & 1) << (3 * b)
                    | ((y >> b) & 1) << (3 * b + 1)
                    | ((z >> b) & 1) << (3 * b + 2);
        }
        return code;
    }

    public int get(int x, int y, int z) {
        return cells[morton(x & 15, y & 15, z & 15)] & 0xFF;
    }

    public void set(int x, int y, int z, int level) {
        cells[morton(x & 15, y & 15, z & 15)] = (byte) Math.clamp(level, 0, MAX_LEVEL);
    }

    public boolean isEmpty() {
        for (byte b : cells) {
            if (b != 0) {
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
            byte[] buf = new byte[VOLUME + 64];
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
    public static LuminanceSection inflate(byte[] blob) {
        if (blob == null || blob.length == 0 || blob.length > VOLUME + 65536) {
            return null;
        }
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(blob);
            byte[] raw = new byte[VOLUME];
            int n;
            try {
                n = inflater.inflate(raw);
            } catch (DataFormatException ex) {
                return null;
            }
            if (n != VOLUME || !inflater.finished()) {
                return null;
            }
            LuminanceSection section = new LuminanceSection();
            System.arraycopy(raw, 0, section.cells, 0, VOLUME);
            return section.isEmpty() ? null : section;
        } finally {
            inflater.end();
        }
    }
}
