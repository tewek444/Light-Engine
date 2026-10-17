package me.tewek.lightengine.mixin.sodium;

import me.tewek.lightengine.client.SodiumTintHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sodium terrain hue tint (client only, optional).
 *
 * <p>Sodium 0.6+/0.8+ never calls {@code ModelBlockRenderer.putQuadData} —
 * chunk meshes are built by Sodium's own {@code BlockRenderer}, so the
 * vanilla tint hook never fires and every surface falls back to default
 * white light when Sodium is enabled. This hook runs at the head of Sodium's
 * {@code bufferQuad} (after Sodium's biome tint + light sampling, before the
 * vertex is packed) and applies the same profile-color multiply as the
 * vanilla path.</p>
 *
 * <p>Pseudo-mixin with zero shadows and zero foreign types in signatures:
 * everything (renderer fields, quad colors) resolves via reflection in
 * {@link SodiumTintHook}. This is deliberate — field shadows against a
 * non-vanilla target do not survive NeoForge's production reobfuscation
 * without a refmap and crash the game at world load, while this shape can
 * only ever skip silently (see {@code require = 0}).</p>
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer",
        remap = false)
public abstract class SodiumBlockRendererMixin {
    static {
        try {
            me.tewek.lightengine.LightEngine.LOGGER.info(
                    "LightEngine SodiumBlockRendererMixin active (Sodium terrain hue tint)");
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "bufferQuad*", at = @At("HEAD"), remap = false, require = 0)
    private void lightengine$sodiumTint(@Coerce Object quad, float[] brightnesses, @Coerce Object material,
                                        CallbackInfo ci) {
        // `this` is Sodium's BlockRenderer at runtime; passed opaque so the
        // production reobfuscator has no foreign signature to rewrite.
        // Unused target args are required for the descriptor check — Mixin
        // only accepts zero or ALL target args (plus CallbackInfo).
        SodiumTintHook.tintSodiumQuad(this, quad);
    }
}
