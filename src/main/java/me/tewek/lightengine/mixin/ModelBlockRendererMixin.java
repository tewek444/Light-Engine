package me.tewek.lightengine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.tewek.lightengine.client.QuadTinter;
import me.tewek.lightengine.client.TintingVertexConsumer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Terrain colored light for the vanilla mesher (client only).
 *
 * <p>Every baked quad of every chunk mesh funnels through
 * {@code putQuadData -> VertexConsumer.putBulkData}. The wrap computes
 * per-vertex light coordinates and color multipliers ({@link QuadTinter}),
 * passes the new light array straight through, and applies the per-vertex
 * colors via {@link TintingVertexConsumer} — vanilla only takes one color per
 * quad, which is what made hue step block by block. Any failure falls back
 * to the untouched vanilla call: this hook can only cost color, never a
 * crash.</p>
 */
@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {
    @WrapOperation(
            method = "putQuadData",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFFF[IIZ)V"))
    private void lightengine$coloredLight(VertexConsumer consumer, PoseStack.Pose pose, BakedQuad quad,
                                          float[] brightness, float red, float green, float blue, float alpha,
                                          int[] lightmap, int overlay, boolean readAlpha,
                                          Operation<Void> original,
                                          @Local(argsOnly = true) BlockAndTintGetter level,
                                          @Local(argsOnly = true) BlockPos pos) {
        QuadTinter.Result res = null;
        try {
            res = lightengine$tint(level, pos, quad, lightmap);
        } catch (Throwable t) {
            lightengine$warnOnce(t);
        }
        if (res == null) {
            original.call(consumer, pose, quad, brightness, red, green, blue, alpha, lightmap, overlay, readAlpha);
            return;
        }
        TintingVertexConsumer wrapped = TintingVertexConsumer.wrap(consumer, res.mult);
        try {
            original.call(wrapped, pose, quad, brightness, red, green, blue, alpha, res.light.clone(), overlay,
                    readAlpha);
        } finally {
            wrapped.release();
        }
    }

    @Unique
    private static final ThreadLocal<float[][]> LIGHTENGINE_POS = ThreadLocal.withInitial(() -> new float[3][4]);

    @Unique
    private static volatile boolean lightengine$warned;

    @Unique
    private static QuadTinter.Result lightengine$tint(BlockAndTintGetter level, BlockPos pos, BakedQuad quad,
                                                     int[] lightmap) {
        if (level == null || pos == null || quad == null || lightmap == null) {
            return null;
        }
        int[] data = quad.getVertices();
        int stride = data.length / 4;
        if (stride < 3 || lightmap.length < 4) {
            return null;
        }
        float[][] p = LIGHTENGINE_POS.get();
        for (int i = 0; i < 4; i++) {
            int base = i * stride;
            p[0][i] = Float.intBitsToFloat(data[base]);
            p[1][i] = Float.intBitsToFloat(data[base + 1]);
            p[2][i] = Float.intBitsToFloat(data[base + 2]);
        }
        return QuadTinter.compute(level, pos, quad.getDirection(), p[0], p[1], p[2], lightmap, 4);
    }

    @Unique
    private static void lightengine$warnOnce(Throwable t) {
        if (!lightengine$warned) {
            lightengine$warned = true;
            me.tewek.lightengine.LightEngine.LOGGER.warn("Colored light failed for a quad; using vanilla light", t);
        }
    }
}
