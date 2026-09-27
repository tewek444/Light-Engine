package me.tewek.lightengine.mixin;

import me.tewek.lightengine.LightProfileRegistry;
import me.tewek.lightengine.lightfield.LightField;
import me.tewek.lightengine.lightfield.LightFieldStore;
import me.tewek.lightengine.lightfield.LightSectionRange;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Persists the active field (v1 store or v2 light field, see {@link LightField})
 * inside the chunk tag. Chunks without data read exactly like vanilla.
 */
@Mixin(ChunkSerializer.class)
public abstract class ChunkSerializerMixin {
    @Inject(method = "write(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;)Lnet/minecraft/nbt/CompoundTag;",
            at = @At("RETURN"))
    private static void lightengine$onWrite(ServerLevel level,
                                            ChunkAccess chunk,
                                            CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag = cir.getReturnValue();
        if (tag == null || level == null || chunk == null) {
            return;
        }
        if (!LightProfileRegistry.isEnabled()) {
            tag.remove(LightFieldStore.NBT_KEY);
            tag.remove("lightengine:extended_light");
            return;
        }
        try {
            ChunkPos pos = chunk.getPos();
            LightField.writeChunk(level, tag, pos.x, pos.z,
                    LightSectionRange.minSection(level), LightSectionRange.maxSection(level));
        } catch (Exception ignored) {
        }
    }

    @Inject(method = "read(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/ai/village/poi/PoiManager;Lnet/minecraft/world/level/chunk/storage/RegionStorageInfo;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/level/chunk/ProtoChunk;",
            at = @At("RETURN"))
    private static void lightengine$onRead(ServerLevel level,
                                           PoiManager poiManager,
                                           RegionStorageInfo regionStorageInfo,
                                           ChunkPos pos,
                                           CompoundTag tag,
                                           CallbackInfoReturnable<ProtoChunk> cir) {
        if (tag == null || level == null || pos == null) {
            return;
        }
        try {
            LightField.readChunk(level, tag, pos.x, pos.z);
        } catch (Exception ignored) {
        }
    }
}
