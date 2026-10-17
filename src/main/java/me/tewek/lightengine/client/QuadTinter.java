package me.tewek.lightengine.client;

import me.tewek.lightengine.Config;
import me.tewek.lightengine.lightfield.ChromaTint;
import me.tewek.lightengine.lightfield.LightmapModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;

/**
 * Per-vertex colored light for one quad (Alpha5), shared by the vanilla,
 * fluid and Sodium mesh hooks.
 *
 * <p>For each vertex: sample the hue field at the vertex (offset half a block
 * along the face normal, into the lit cell), build the lightmap read the
 * vertex should get ({@link LightmapModel#sampleColored}), then solve for a
 * new block-light coordinate plus a vertex-color multiplier {@code <= 1}
 * ({@link LightmapModel#solve}). Callers multiply their vertex color by
 * {@link Result#mult} and replace the packed light with {@link Result#light}.
 * Sky light and AO stay untouched, vertex colors never overflow.</p>
 *
 * <p>Thread-confined scratch state ({@link #RESULT}): one quad at a time per
 * meshing thread, no allocation on the hot path.</p>
 */
public final class QuadTinter {
    private QuadTinter() {
    }

    public static final class Result {
        /** Vertex-color multipliers {@code [i*3 + c]}, each 0..1. */
        public final float[] mult = new float[12];
        /** Packed light per vertex (block raw | sky raw << 16). */
        public final int[] light = new int[4];

        final float[] px = new float[4];
        final float[] py = new float[4];
        final float[] pz = new float[4];
        final ChromaTint.Sample sample = new ChromaTint.Sample();
        final float[] target = new float[3];
    }

    private static final ThreadLocal<Result> RESULT = ThreadLocal.withInitial(Result::new);

    /**
     * @param ownPos  block being meshed
     * @param face    light face of the quad, or null (sample at the block)
     * @param localX  vertex positions relative to {@code ownPos} (model space)
     * @param packed  per-vertex packed light as the mesher computed it
     * @param count   vertex count (1..4)
     * @return the thread's result, or null to keep the quad vanilla
     */
    public static Result compute(BlockAndTintGetter level, BlockPos ownPos, Direction face,
                                 float[] localX, float[] localY, float[] localZ,
                                 int[] packed, int count) {
        LightmapModel.Env env = ColorLightEnv.current();
        if (env == null || level == null || ownPos == null) {
            return null;
        }
        float strength = Config.colorStrength() / 100.0F;
        if (strength <= 0.0F) {
            return null;
        }
        Result res = RESULT.get();
        float nx = 0.0F;
        float ny = 0.0F;
        float nz = 0.0F;
        BlockPos lightPos = ownPos;
        if (face != null) {
            nx = face.getStepX() * 0.5F;
            ny = face.getStepY() * 0.5F;
            nz = face.getStepZ() * 0.5F;
            lightPos = ownPos.relative(face);
        }
        int bx = ownPos.getX();
        int by = ownPos.getY();
        int bz = ownPos.getZ();
        for (int i = 0; i < count; i++) {
            res.px[i] = bx + localX[i] + nx;
            res.py[i] = by + localY[i] + ny;
            res.pz[i] = bz + localZ[i] + nz;
        }
        ChromaTint.Sample s = res.sample;
        if (!ChromaTint.sampleVertices(level, ownPos, lightPos, res.px, res.py, res.pz, count, s)) {
            return null;
        }
        boolean changed = false;
        for (int i = 0; i < count; i++) {
            int p = packed[i];
            int u = Math.min(p & 0xFFFF, LightmapModel.MAX_RAW);
            int v = Math.min((p >>> 16) & 0xFFFF, LightmapModel.MAX_RAW);
            float frac = s.frac[i] * strength;
            int o = i * 3;
            if (frac <= 0.0F || u == 0) {
                res.mult[o] = 1.0F;
                res.mult[o + 1] = 1.0F;
                res.mult[o + 2] = 1.0F;
                res.light[i] = p;
                continue;
            }
            LightmapModel.sampleColored(env, u, v, s.hue[o], s.hue[o + 1], s.hue[o + 2], frac, res.target);
            int nu = LightmapModel.solve(env, u, v, res.target, res.mult, o);
            res.light[i] = nu | (v << 16);
            changed = true;
        }
        return changed ? res : null;
    }

    /** Multiplies the RGB of a packed ARGB color, keeping alpha. */
    public static int scaleArgb(int argb, float[] mult, int o) {
        int a = argb >>> 24;
        int r = Math.round(((argb >> 16) & 0xFF) * mult[o]);
        int g = Math.round(((argb >> 8) & 0xFF) * mult[o + 1]);
        int b = Math.round((argb & 0xFF) * mult[o + 2]);
        return (a << 24) | (Math.min(r, 255) << 16) | (Math.min(g, 255) << 8) | Math.min(b, 255);
    }
}
