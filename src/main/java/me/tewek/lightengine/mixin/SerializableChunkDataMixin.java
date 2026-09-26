package me.tewek.lightengine.mixin;

import me.tewek.lightengine.LightProfileRegistry;
import me.tewek.lightengine.lightfield.LightField;
import me.tewek.lightengine.lightfield.LightFieldStore;
import me.tewek.lightengine.lightfield.LightSectionRange;
import me.tewek.lightengine.lightfield.SectionBlob;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists the active light field (see {@link LightField}) inside the chunk
 * tag. Chunks without data read exactly like vanilla.
 *
 * <p>26.x note: vanilla {@code ChunkSerializer} was replaced by
 * {@code SerializableChunkData}. Serialization is split: {@code copyOf}
 * captures the data on the server thread, while {@code write} produces the
 * tag later on a background thread — so the encoded blobs cross the split
 * via an identity map keyed on the record instance (never a thread-local).
 * The read path hooks {@code parse}, which already sees the level, the tag
 * and the chunk position at once.
 */
@Mixin(SerializableChunkData.class)
public abstract class SerializableChunkDataMixin {
    /** Legacy v1 on-disk key, removed on write (gradual cleanup, no migration). */
    private static final String LEGACY_NBT_KEY = "lightengine:extended_light";

    private static final Map<SerializableChunkData, List<SectionBlob>> LE_PENDING =
            Collections.synchronizedMap(new IdentityHashMap<>());

    @Inject(method = "copyOf(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;)Lnet/minecraft/world/level/chunk/storage/SerializableChunkData;",
            at = @At("RETURN"))
    private static void lightengine$captureWrite(ServerLevel level, ChunkAccess chunk,
                                                 CallbackInfoReturnable<SerializableChunkData> cir) {
        if (!LightProfileRegistry.isEnabled()) {
            return;
        }
        SerializableChunkData data = cir.getReturnValue();
        if (level == null || chunk == null || data == null) {
            return;
        }
        try {
            ChunkPos pos = chunk.getPos();
            List<SectionBlob> blobs = LightField.encodeChunk(level, pos.x(), pos.z(),
                    LightSectionRange.minSection(level), LightSectionRange.maxSection(level));
            if (blobs != null) {
                LE_PENDING.put(data, blobs);
            }
        } catch (Exception ignored) {
        }
    }

    @Inject(method = "write()Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
    private void lightengine$onWrite(CallbackInfoReturnable<CompoundTag> cir) {
        List<SectionBlob> blobs = LE_PENDING.remove(this);
        if (blobs == null || blobs.isEmpty()) {
            return;
        }
        CompoundTag tag = cir.getReturnValue();
        if (tag == null) {
            return;
        }
        try {
            ListTag list = new ListTag();
            for (SectionBlob blob : blobs) {
                CompoundTag c = new CompoundTag();
                c.putInt("s", blob.y());
                c.putByteArray("blob", blob.blob());
                list.add(c);
            }
            tag.put(LightFieldStore.NBT_KEY, list);
            tag.remove(LEGACY_NBT_KEY);
        } catch (Exception ignored) {
        }
    }

    @Inject(method = "parse(Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/chunk/PalettedContainerFactory;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/level/chunk/storage/SerializableChunkData;",
            at = @At("RETURN"))
    private static void lightengine$onParse(LevelHeightAccessor height, PalettedContainerFactory factory,
                                            CompoundTag tag,
                                            CallbackInfoReturnable<SerializableChunkData> cir) {
        if (!(height instanceof ServerLevel level) || tag == null) {
            return;
        }
        SerializableChunkData data = cir.getReturnValue();
        if (data == null) {
            return;
        }
        try {
            ChunkPos pos = data.chunkPos();
            LightField.readChunk(level, tag, pos.x(), pos.z());
        } catch (Exception ignored) {
        }
    }
}
