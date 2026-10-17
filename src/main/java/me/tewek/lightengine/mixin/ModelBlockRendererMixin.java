package me.tewek.lightengine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.QuadInstance;
import me.tewek.lightengine.client.QuadTinter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Terrain colored light for the vanilla mesher (client only).
 *
 * <p>Every baked quad funnels through {@code putQuadWithTint ->
 * BlockQuadOutput.put} with a {@link QuadInstance} that already holds the
 * per-vertex light and color. The wrap computes per-vertex light coordinates
 * and color multipliers ({@link QuadTinter}) and writes them into that
 * instance; the next quad re-prepares it. Any failure falls back to the
 * untouched vanilla call: this hook can only cost color, never a crash.</p>
 */
@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {
    @WrapOperation(
            method = "putQuadWithTint",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/BlockQuadOutput;put(FFFLnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;)V"))
    private void lightengine$coloredLight(BlockQuadOutput output, float x, float y, float z, BakedQuad quad,
                                          QuadInstance instance, Operation<Void> original,
                                          @Local(argsOnly = true) BlockAndTintGetter level,
                                          @Local(argsOnly = true) BlockPos pos) {
        try {
            lightengine$tint(level, pos, quad, instance);
        } catch (Throwable t) {
            lightengine$warnOnce(t);
        }
        original.call(output, x, y, z, quad, instance);
    }

    @Unique
    private static final ThreadLocal<float[][]> LIGHTENGINE_POS = ThreadLocal.withInitial(() -> new float[3][4]);
    @Unique
    private static final ThreadLocal<int[]> LIGHTENGINE_LIGHT = ThreadLocal.withInitial(() -> new int[4]);

    @Unique
    private static volatile boolean lightengine$warned;

    @Unique
    private static void lightengine$tint(BlockAndTintGetter level, BlockPos pos, BakedQuad quad, QuadInstance instance) {
        if (level == null || pos == null || quad == null || instance == null) {
            return;
        }
        float[][] p = LIGHTENGINE_POS.get();
        int[] light = LIGHTENGINE_LIGHT.get();
        for (int i = 0; i < 4; i++) {
            org.joml.Vector3fc v = quad.position(i);
            p[0][i] = v.x();
            p[1][i] = v.y();
            p[2][i] = v.z();
            light[i] = instance.getLightCoords(i);
        }
        QuadTinter.Result res = QuadTinter.compute(level, pos, quad.direction(), p[0], p[1], p[2], light, 4);
        if (res == null) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            instance.setLightCoords(i, res.light[i]);
            instance.setColor(i, QuadTinter.scaleArgb(instance.getColor(i), res.mult, i * 3));
        }
    }

    @Unique
    private static void lightengine$warnOnce(Throwable t) {
        if (!lightengine$warned) {
            lightengine$warned = true;
            me.tewek.lightengine.LightEngine.LOGGER.warn("Colored light failed for a quad; using vanilla light", t);
        }
    }
}
