package me.tewek.lightengine.mixin;

import me.tewek.lightengine.LightProfileRegistry;
import me.tewek.lightengine.lightfield.LightField;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mirrors block-light levels above vanilla range into the active field
 * (v1 store or v2 light field, see {@link LightField}).
 * Vanilla storage always keeps a 0-15 clamp; reads return the extended value
 * when our cell is non-zero. Sky layer and the disabled state are untouched.
 *
 * <p>SRG targets ({@code getStoredLevel}/{@code setStoredLevel}/
 * {@code DataLayer.set}/{@code layer}/{@code chunkSource}, see gen-refmap.py).
 * Pinned with {@code remap = false}.
 */
@Mixin(LayerLightSectionStorage.class)
public abstract class LayerLightSectionStorageMixin {
    @Shadow(remap = false)
    @Final
    private LightLayer f_75738_;

    @Shadow(remap = false)
    @Final
    protected LightChunkGetter f_75739_;

    private static final int LE_VANILLA_MAX = 15;

    private boolean lightengine$active() {
        return f_75738_ == LightLayer.BLOCK && LightProfileRegistry.isEnabled();
    }

    private Level lightengine$levelOrNull() {
        try {
            LightChunkGetter getter = f_75739_;
            if (getter == null) {
                return null;
            }
            BlockGetter bg = getter.getLevel();
            return bg instanceof Level level ? level : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static long lightengine$sectionOf(long levelPos) {
        return SectionPos.blockToSection(levelPos);
    }

    @Inject(method = "m_75795_(J)I", at = @At("HEAD"), cancellable = true, remap = false)
    private void lightengine$readExtended(long levelPos, CallbackInfoReturnable<Integer> cir) {
        if (!lightengine$active()) {
            return;
        }
        Level level = lightengine$levelOrNull();
        if (level == null) {
            return;
        }
        int x = BlockPos.getX(levelPos);
        int y = BlockPos.getY(levelPos);
        int z = BlockPos.getZ(levelPos);
        long sectionPos = lightengine$sectionOf(levelPos);
        int value = LightField.readCell(level, sectionPos,
                SectionPos.sectionRelative(x), SectionPos.sectionRelative(y), SectionPos.sectionRelative(z));
        if (value > 0) {
            cir.setReturnValue(value);
        }
    }

    @Inject(method = "m_75772_(JI)V", at = @At("HEAD"), remap = false)
    private void lightengine$writeExtended(long levelPos, int lightLevel, CallbackInfo ci) {
        if (!lightengine$active()) {
            return;
        }
        Level level = lightengine$levelOrNull();
        if (level == null) {
            return;
        }
        int x = BlockPos.getX(levelPos);
        int y = BlockPos.getY(levelPos);
        int z = BlockPos.getZ(levelPos);
        long sectionPos = lightengine$sectionOf(levelPos);
        int lx = SectionPos.sectionRelative(x);
        int ly = SectionPos.sectionRelative(y);
        int lz = SectionPos.sectionRelative(z);
        // NOTE: deliberately no per-cell chunk bookkeeping or network sends
        // here (unlike an earlier revision): setStoredLevel runs on the light
        // thread for every propagated cell, so even cheap-looking scans/sends
        // stretch multi-second floods, interleave them with fresh edits and
        // strand orphaned halos behind opaque walls (the sealed-box leak).
        // Client cache hygiene is covered by chunk-send payloads instead.
        LightField.writeCell(level, sectionPos, lx, ly, lz,
                lightLevel > LE_VANILLA_MAX ? lightLevel : 0);
    }

    @Redirect(method = "m_75772_(JI)V",
            remap = false,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/DataLayer;m_62564_(IIII)V"))
    private void lightengine$clampVanilla(DataLayer instance, int x, int y, int z, int value) {
        int clamped = value < 0 ? 0 : Math.min(value, LE_VANILLA_MAX);
        instance.set(x, y, z, clamped);
    }
}
