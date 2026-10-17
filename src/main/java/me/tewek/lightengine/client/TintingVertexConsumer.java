package me.tewek.lightengine.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Thin per-thread wrapper that multiplies each vertex color of one quad by
 * its own RGB factor (vanilla {@code putBulkData} only takes one color per
 * quad). 1.20.1 vertex API: {@code vertex/color/uv/overlayCoords/uv2/normal/
 * endVertex} plus the bulk {@code vertex(...)} default.
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

    public static TintingVertexConsumer wrap(VertexConsumer delegate, float[] mult) {
        TintingVertexConsumer c = INSTANCE.get();
        c.delegate = delegate;
        c.mult = mult;
        c.next = 0;
        c.current = 0;
        return c;
    }

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
    public void vertex(float x, float y, float z, float red, float green, float blue, float alpha,
                       float u, float v, int overlay, int light, float nx, float ny, float nz) {
        int o = advance();
        this.delegate.vertex(x, y, z, red * this.mult[o], green * this.mult[o + 1], blue * this.mult[o + 2], alpha,
                u, v, overlay, light, nx, ny, nz);
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        advance();
        this.delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        int o = this.current * 3;
        this.delegate.color(
                Math.min(255, Math.round(red * this.mult[o])),
                Math.min(255, Math.round(green * this.mult[o + 1])),
                Math.min(255, Math.round(blue * this.mult[o + 2])),
                alpha);
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        this.delegate.uv(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        this.delegate.overlayCoords(u, v);
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        this.delegate.uv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        this.delegate.normal(x, y, z);
        return this;
    }

    @Override
    public void endVertex() {
        this.delegate.endVertex();
    }

    @Override
    public void defaultColor(int red, int green, int blue, int alpha) {
        this.delegate.defaultColor(red, green, blue, alpha);
    }

    @Override
    public void unsetDefaultColor() {
        this.delegate.unsetDefaultColor();
    }
}
