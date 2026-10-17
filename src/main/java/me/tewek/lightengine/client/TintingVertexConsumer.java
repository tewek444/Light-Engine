package me.tewek.lightengine.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Thin per-thread wrapper that multiplies each vertex color of one quad by
 * its own RGB factor (vanilla {@code putBulkData} only takes one color per
 * quad). Everything else is forwarded untouched; the bulk 11-argument
 * {@code addVertex} stays on the delegate's fast path.
 */
public final class TintingVertexConsumer implements VertexConsumer {
    private static final ThreadLocal<TintingVertexConsumer> INSTANCE =
            ThreadLocal.withInitial(TintingVertexConsumer::new);

    private VertexConsumer delegate;
    private float[] mult;
    private int next;
    private int current;

    private TintingVertexConsumer() {
    }

    /** Arms the thread's wrapper for one quad; {@code mult} is {@code [i*3 + c]}. */
    public static TintingVertexConsumer wrap(VertexConsumer delegate, float[] mult) {
        TintingVertexConsumer c = INSTANCE.get();
        c.delegate = delegate;
        c.mult = mult;
        c.next = 0;
        c.current = 0;
        return c;
    }

    /** Drops the delegate reference so the wrapper never pins a buffer. */
    public void release() {
        this.delegate = null;
        this.mult = null;
    }

    private int advance() {
        int i = this.next;
        this.next = (i + 1) & 3;
        this.current = i;
        return i * 3;
    }

    @Override
    public void addVertex(float x, float y, float z, int color, float u, float v, int packedOverlay,
                          int packedLight, float normalX, float normalY, float normalZ) {
        int o = advance();
        this.delegate.addVertex(x, y, z, QuadTinter.scaleArgb(color, this.mult, o), u, v, packedOverlay,
                packedLight, normalX, normalY, normalZ);
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        advance();
        this.delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        int o = this.current * 3;
        this.delegate.setColor(
                Math.min(255, Math.round(red * this.mult[o])),
                Math.min(255, Math.round(green * this.mult[o + 1])),
                Math.min(255, Math.round(blue * this.mult[o + 2])),
                alpha);
        return this;
    }

    @Override
    public VertexConsumer setColor(int argb) {
        this.delegate.setColor(QuadTinter.scaleArgb(argb, this.mult, this.current * 3));
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        this.delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        this.delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        this.delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        this.delegate.setLineWidth(width);
        return this;
    }

    @Override
    public VertexConsumer setUv3(float u, float v) {
        this.delegate.setUv3(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
        this.delegate.setNormal(normalX, normalY, normalZ);
        return this;
    }
}
