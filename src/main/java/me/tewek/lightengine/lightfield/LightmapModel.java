package me.tewek.lightengine.lightfield;

/**
 * CPU replica of the vanilla lightmap plus the colored-light solve (Alpha5).
 *
 * <p>Why a replica: terrain color is {@code texture * vertexColor * lightmap(block, sky)},
 * and the lightmap mixes block and sky light before the shader ever sees
 * them. A plain vertex multiply therefore tints moonlight/daylight too
 * (hard edges at the halo rim) and can never add a channel the warm vanilla
 * ramp lacks (white stayed yellow). Instead, per vertex, we compute the color
 * the lightmap WOULD return if the block-light part carried the requested
 * hue ({@link #sampleColored}), then pick a block-light coordinate high
 * enough to cover that target and darken the surplus channels with a vertex
 * multiplier {@code <= 1} ({@link #solve}). Sky light, daylight and the
 * vanilla brightness curve stay exactly vanilla.</p>
 *
 * <p>Texels follow 26.x {@code lightmap.fsh} (block light tinted by the
 * {@code visual/block_light_tint} attribute, sky light colored by the
 * timeline); sampling follows {@code sample_lightmap}: linear filtering
 * at {@code clamp(uv / 256, 0.5/16, 15.5/16)}, i.e. a half-texel shifted
 * bilinear read. Sodium samples the same way. Not modeled (second order,
 * brightness only): torch flicker noise (mean 1.4 is used), end flash,
 * darkness effect, night vision, boss-fog darkening.</p>
 *
 * <p>Pure math, no Minecraft imports. Thread-safe: {@link Env} is immutable.</p>
 */
public final class LightmapModel {
    private LightmapModel() {
    }

    /** Raw lightmap coordinate of level 15 ({@code level * 16}). */
    public static final int MAX_RAW = 240;

    /** Mean of {@code blockLightFlicker + 1.4} (the flicker noise averages to 0). */
    private static final float BLOCK_FACTOR = 1.4F;
    /** Overworld defaults of the visual environment attributes (26.x). */
    private static final float[] DEFAULT_BLOCK_TINT = {1.0F, 216 / 255.0F, 140 / 255.0F};
    private static final float[] DEFAULT_AMBIENT = {10 / 255.0F, 10 / 255.0F, 10 / 255.0F};
    private static final float[] WHITE = {1.0F, 1.0F, 1.0F};

    /** Immutable snapshot of everything the lightmap depends on. */
    public static final class Env {
        final float[] blockTint;
        final float[] skyColor;
        final float skyFactor;
        final float[] ambient;
        final float brightness;
        /** Vanilla texels [sky][block][rgb]. */
        final float[][][] texels = new float[16][16][3];

        Env(float[] blockTint, float[] skyColor, float skyFactor, float[] ambient, float brightness) {
            this.blockTint = blockTint.clone();
            this.skyColor = skyColor.clone();
            this.skyFactor = skyFactor;
            this.ambient = ambient.clone();
            this.brightness = brightness;
            for (int s = 0; s < 16; s++) {
                for (int b = 0; b < 16; b++) {
                    texel(this, b, s, this.texels[s][b]);
                }
            }
        }

        public boolean sameAs(float[] blockTint, float[] skyColor, float skyFactor, float[] ambient, float brightness) {
            return this.skyFactor == skyFactor && this.brightness == brightness
                    && java.util.Arrays.equals(this.blockTint, blockTint)
                    && java.util.Arrays.equals(this.skyColor, skyColor)
                    && java.util.Arrays.equals(this.ambient, ambient);
        }
    }

    /** Full 26.x lightmap inputs (the {@code LightmapInfo} uniform, effects off). */
    public static Env env(float[] blockTint, float[] skyColor, float skyFactor, float[] ambient, float brightness) {
        return new Env(blockTint, skyColor, skyFactor, ambient, brightness);
    }

    /** Overworld defaults with a given sky light factor (editor preview). */
    public static Env env(float skyFactor, float ambientUnused, float gamma, boolean forceBrightUnused) {
        return new Env(DEFAULT_BLOCK_TINT, WHITE, skyFactor, DEFAULT_AMBIENT, gamma);
    }

    /** {@code get_brightness} in {@code lightmap.fsh}. */
    static float brightness(int level) {
        float f = level / 15.0F;
        return f / (4.0F - 3.0F * f);
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : Math.min(v, 1.0F);
    }

    /** One lightmap texel, line by line from 26.x {@code lightmap.fsh}. */
    private static void texel(Env env, int block, int sky, float[] out) {
        float blockLevel = block / 15.0F;
        float blockBright = brightness(block) * BLOCK_FACTOR;
        float skyBright = brightness(sky) * env.skyFactor;
        float p = 2.0F * blockLevel - 1.0F;
        float mixW = 0.9F * p * p;
        float max = 0.0F;
        for (int c = 0; c < 3; c++) {
            float blockColor = env.blockTint[c] + (1.0F - env.blockTint[c]) * mixW;
            float v = env.ambient[c] + env.skyColor[c] * skyBright + blockColor * blockBright;
            out[c] = clamp01(v);
            max = Math.max(max, out[c]);
        }
        if (max > 0.0F) {
            float inv = 1.0F - max;
            float scaled = (1.0F - inv * inv * inv * inv) / max;
            for (int c = 0; c < 3; c++) {
                out[c] += (out[c] * scaled - out[c]) * env.brightness;
            }
        }
    }

    /** Texel-space coordinate of a raw lightmap value (half-texel shifted, clamped). */
    private static float texCoord(int raw) {
        float t = raw / 16.0F - 0.5F;
        return t < 0.0F ? 0.0F : Math.min(t, 15.0F);
    }

    /** Vanilla lightmap read at raw coordinates (what the shader returns). */
    public static void sample(Env env, int uRaw, int vRaw, float[] out) {
        float x = texCoord(uRaw);
        float y = texCoord(vRaw);
        int x0 = (int) x;
        int y0 = (int) y;
        int x1 = Math.min(x0 + 1, 15);
        int y1 = Math.min(y0 + 1, 15);
        float fx = x - x0;
        float fy = y - y0;
        for (int c = 0; c < 3; c++) {
            float a = env.texels[y0][x0][c] + (env.texels[y0][x1][c] - env.texels[y0][x0][c]) * fx;
            float b = env.texels[y1][x0][c] + (env.texels[y1][x1][c] - env.texels[y1][x0][c]) * fx;
            out[c] = a + (b - a) * fy;
        }
    }

    /** One channel of {@link #sample}, for the monotone search. */
    private static float sampleChannel(Env env, int uRaw, int vRaw, int c) {
        float x = texCoord(uRaw);
        float y = texCoord(vRaw);
        int x0 = (int) x;
        int y0 = (int) y;
        int x1 = Math.min(x0 + 1, 15);
        int y1 = Math.min(y0 + 1, 15);
        float fx = x - x0;
        float fy = y - y0;
        float a = env.texels[y0][x0][c] + (env.texels[y0][x1][c] - env.texels[y0][x0][c]) * fx;
        float b = env.texels[y1][x0][c] + (env.texels[y1][x1][c] - env.texels[y1][x0][c]) * fx;
        return a + (b - a) * fy;
    }

    /**
     * The lightmap read the vertex SHOULD produce. Works in the final
     * (post-gamma) space: {@code S} is the read with no block light (sky
     * only), {@code V} the vanilla read, so {@code V - S} is what block light
     * adds. That warm increment is replaced by the hue at the same peak
     * strength: {@code T = S + (1-frac)(V-S) + frac * max(V-S) * hue}.
     * Doing this after the vanilla curve keeps the hue saturated (the curve
     * and gamma would otherwise wash it toward white at high levels) and
     * fades it out exactly where block light stops mattering (halo rim,
     * daylight).
     *
     * @param hr   hue, display space, max channel 1
     * @param frac 0 = vanilla, 1 = fully colored block light
     */
    public static void sampleColored(Env env, int uRaw, int vRaw, float hr, float hg, float hb,
                                     float frac, float[] out) {
        float[] v = new float[3];
        float[] s = new float[3];
        sample(env, uRaw, vRaw, v);
        sample(env, 0, vRaw, s);
        float dr = Math.max(0.0F, v[0] - s[0]);
        float dg = Math.max(0.0F, v[1] - s[1]);
        float db = Math.max(0.0F, v[2] - s[2]);
        float peak = Math.max(dr, Math.max(dg, db));
        float keep = 1.0F - frac;
        out[0] = clamp01(s[0] + dr * keep + peak * hr * frac);
        out[1] = clamp01(s[1] + dg * keep + peak * hg * frac);
        out[2] = clamp01(s[2] + db * keep + peak * hb * frac);
    }

    /**
     * Finds the smallest block coordinate {@code >= uRaw} whose vanilla read
     * covers {@code target} in every channel, and the per-channel vertex
     * multipliers ({@code <= 1}) that bring that read down to the target.
     * Channels the lightmap cannot reach even at level 15 keep multiplier 1
     * (brightness shortfall, hue kept as close as possible).
     *
     * @return the new raw block coordinate (0..240)
     */
    public static int solve(Env env, int uRaw, int vRaw, float[] target, float[] multOut, int multOffset) {
        int u0 = Math.max(0, Math.min(uRaw, MAX_RAW));
        int best = u0;
        for (int c = 0; c < 3; c++) {
            float want = target[c];
            if (sampleChannel(env, best, vRaw, c) >= want) {
                continue;
            }
            int lo = best;
            int hi = MAX_RAW;
            if (sampleChannel(env, hi, vRaw, c) < want) {
                best = hi;
                continue;
            }
            while (hi - lo > 1) {
                int mid = (lo + hi) >>> 1;
                if (sampleChannel(env, mid, vRaw, c) >= want) {
                    hi = mid;
                } else {
                    lo = mid;
                }
            }
            best = hi;
        }
        for (int c = 0; c < 3; c++) {
            float have = sampleChannel(env, best, vRaw, c);
            float m = have > 1e-4F ? target[c] / have : 1.0F;
            multOut[multOffset + c] = m < 0.0F ? 0.0F : Math.min(m, 1.0F);
        }
        return best;
    }
}
