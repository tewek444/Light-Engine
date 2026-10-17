package me.tewek.lightengine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.tewek.lightengine.client.QuadTinter;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Colored light on fluids (vanilla mesher, client only). Fluids bypass
 * {@code putBulkData}: each vertex goes through the private
 * {@code vertex(...)} helper with section-relative coordinates, so the wrap
 * tints them one at a time with the same solve as terrain quads.
 */
@Mixin(LiquidBlockRenderer.class)
public abstract class LiquidBlockRendererMixin {
    @Unique
    private static final ThreadLocal<float[][]> LIGHTENGINE_SCRATCH =
            ThreadLocal.withInitial(() -> new float[3][1]);
    @Unique
    private static final ThreadLocal<int[]> LIGHTENGINE_LIGHT = ThreadLocal.withInitial(() -> new int[1]);

    @WrapOperation(
            method = "tesselate",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/LiquidBlockRenderer;vertex(Lcom/mojang/blaze3d/vertex/VertexConsumer;FFFFFFFFFI)V"),
            require = 0)
    private void lightengine$coloredFluid(LiquidBlockRenderer self, VertexConsumer buffer,
                                          float x, float y, float z, float red, float green, float blue,
                                          float alpha, float u, float v, int packedLight,
                                          Operation<Void> original,
                                          @Local(argsOnly = true) BlockAndTintGetter level,
                                          @Local(argsOnly = true) BlockPos pos) {
        try {
            float[][] p = LIGHTENGINE_SCRATCH.get();
            // Vertex coordinates are section-relative; make them block-relative.
            p[0][0] = x - (pos.getX() & 15);
            p[1][0] = y - (pos.getY() & 15);
            p[2][0] = z - (pos.getZ() & 15);
            int[] light = LIGHTENGINE_LIGHT.get();
            light[0] = packedLight;
            QuadTinter.Result res = QuadTinter.compute(level, pos, null, p[0], p[1], p[2], light, 1);
            if (res != null) {
                original.call(self, buffer, x, y, z,
                        red * res.mult[0], green * res.mult[1], blue * res.mult[2],
                        alpha, u, v, res.light[0]);
                return;
            }
        } catch (Throwable ignored) {
            // Fall through to vanilla: fluids must never fail to render.
        }
        original.call(self, buffer, x, y, z, red, green, blue, alpha, u, v, packedLight);
    }

    /** Vanilla signature (no alpha): used on Fabric; NeoForge patches in alpha (above). */
    @WrapOperation(
            method = "tesselate",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/LiquidBlockRenderer;vertex(Lcom/mojang/blaze3d/vertex/VertexConsumer;FFFFFFFFI)V"),
            require = 0)
    private void lightengine$coloredFluidVanilla(LiquidBlockRenderer self, VertexConsumer buffer,
                                                 float x, float y, float z, float red, float green, float blue,
                                                 float u, float v, int packedLight,
                                                 Operation<Void> original,
                                                 @Local(argsOnly = true) BlockAndTintGetter level,
                                                 @Local(argsOnly = true) BlockPos pos) {
        try {
            float[][] p = LIGHTENGINE_SCRATCH.get();
            p[0][0] = x - (pos.getX() & 15);
            p[1][0] = y - (pos.getY() & 15);
            p[2][0] = z - (pos.getZ() & 15);
            int[] light = LIGHTENGINE_LIGHT.get();
            light[0] = packedLight;
            QuadTinter.Result res = QuadTinter.compute(level, pos, null, p[0], p[1], p[2], light, 1);
            if (res != null) {
                original.call(self, buffer, x, y, z,
                        red * res.mult[0], green * res.mult[1], blue * res.mult[2], u, v, res.light[0]);
                return;
            }
        } catch (Throwable ignored) {
            // Fall through to vanilla: fluids must never fail to render.
        }
        original.call(self, buffer, x, y, z, red, green, blue, u, v, packedLight);
    }
}
