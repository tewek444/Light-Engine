package me.tewek.lightengine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.tewek.lightengine.client.QuadTinter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Colored light on fluids (vanilla mesher, client only). Each fluid vertex
 * goes through the private {@code vertex(...)} helper with section-relative
 * coordinates; the wrap tints them one at a time with the same solve as
 * terrain quads. {@code vertex} is called from {@code addFace}, which no
 * longer sees the level, so {@code tesselate} parks it on the renderer (one
 * renderer per section compile, so per thread).
 */
@Mixin(FluidRenderer.class)
public abstract class LiquidBlockRendererMixin {
    @Unique
    private static final ThreadLocal<float[][]> LIGHTENGINE_SCRATCH =
            ThreadLocal.withInitial(() -> new float[3][1]);
    @Unique
    private static final ThreadLocal<int[]> LIGHTENGINE_LIGHT = ThreadLocal.withInitial(() -> new int[1]);

    @Unique
    private BlockAndTintGetter lightengine$level;
    @Unique
    private BlockPos lightengine$pos;

    @Inject(method = "tesselate", at = @At("HEAD"))
    private void lightengine$begin(BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output,
                                   BlockState blockState, FluidState fluidState, CallbackInfo ci) {
        this.lightengine$level = level;
        this.lightengine$pos = pos;
    }

    @Inject(method = "tesselate", at = @At("RETURN"))
    private void lightengine$end(BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output,
                                 BlockState blockState, FluidState fluidState, CallbackInfo ci) {
        this.lightengine$level = null;
        this.lightengine$pos = null;
    }

    @WrapOperation(
            method = "addFace",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/FluidRenderer;vertex(Lcom/mojang/blaze3d/vertex/VertexConsumer;FFFIFFI)V"),
            require = 0)
    private void lightengine$coloredFluid(FluidRenderer self, VertexConsumer buffer,
                                          float x, float y, float z, int color, float u, float v, int packedLight,
                                          Operation<Void> original) {
        BlockAndTintGetter level = this.lightengine$level;
        BlockPos pos = this.lightengine$pos;
        if (level != null && pos != null) {
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
                    original.call(self, buffer, x, y, z, QuadTinter.scaleArgb(color, res.mult, 0), u, v, res.light[0]);
                    return;
                }
            } catch (Throwable ignored) {
                // Fall through to vanilla: fluids must never fail to render.
            }
        }
        original.call(self, buffer, x, y, z, color, u, v, packedLight);
    }
}
