package me.tewek.lightengine.lightfield;

import me.tewek.lightengine.client.LightFieldClientHandler;
import me.tewek.lightengine.lightfield.SectionBlob;
import me.tewek.lightengine.network.LightFieldPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import sun.misc.Unsafe;

public final class LightFieldStoreRegressionTest {
    private LightFieldStoreRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        staleChunkWriteDoesNotResurrectLight();
        chunkReadEvictsSectionsMissingFromNbt();
        radius20PlaceSaveReloadBreak();
        radius20PayloadRoundTripPreservesExtendedLevels();
        radius20ClientCacheIsClearedAfterServerBreak();
    }

    private static void staleChunkWriteDoesNotResurrectLight() throws ReflectiveOperationException {
        LightFieldStore store = newStore();
        long sectionPos = SectionPos.asLong(1, 2, 3);
        store.setCell(sectionPos, 0, 0, 0, 16);

        CompoundTag tag = new CompoundTag();
        store.writeTag(tag, 1, 3, -4, 4);
        require(tag.contains(LightFieldStore.NBT_KEY, Tag.TAG_LIST),
                "expected initial light field tag");

        store.setCell(sectionPos, 0, 0, 0, 0);
        store.writeTag(tag, 1, 3, -4, 4);
        require(!tag.contains(LightFieldStore.NBT_KEY),
                "empty chunk write must remove the light field tag");

        store.readTag(tag, 1, 3);
        require(store.getCell(sectionPos, 0, 0, 0) == 0,
                "reading the rewritten chunk must not resurrect stale light");
    }

    private static void chunkReadEvictsSectionsMissingFromNbt() throws ReflectiveOperationException {
        LightFieldStore store = newStore();
        long kept = SectionPos.asLong(1, 2, 3);
        long removed = SectionPos.asLong(1, 3, 3);
        long otherChunk = SectionPos.asLong(2, 2, 3);
        store.setCell(kept, 0, 0, 0, 16);
        store.setCell(removed, 0, 0, 0, 20);
        store.setCell(otherChunk, 0, 0, 0, 18);

        CompoundTag tag = new CompoundTag();
        store.writeTag(tag, 1, 3, -4, 4);
        var sections = tag.getList(LightFieldStore.NBT_KEY, Tag.TAG_COMPOUND);
        require(sections.size() == 2, "expected two persisted sections");
        for (int i = 0; i < sections.size(); i++) {
            CompoundTag section = (CompoundTag) sections.get(i);
            if (section.getInt("s") == SectionPos.y(removed)) {
                sections.remove(i);
                break;
            }
        }
        require(sections.size() == 1, "test fixture must remove exactly one section");

        store.readTag(tag, 1, 3);
        require(store.getCell(kept, 0, 0, 0) == 16,
                "NBT merge must retain a section present in the chunk tag");
        require(store.getCell(removed, 0, 0, 0) == 0,
                "reading a chunk must evict sections omitted by its NBT");
        require(store.getCell(otherChunk, 0, 0, 0) == 18,
                "chunk eviction must not clear another chunk");
    }

    private static void radius20PayloadRoundTripPreservesExtendedLevels() throws ReflectiveOperationException {
        final int radius = 20;
        final int centerX = 0;
        final int centerY = 0;
        final int centerZ = 0;
        final int minSection = Math.floorDiv(centerY - radius, 16);
        final int maxSection = Math.floorDiv(centerY + radius, 16);

        LightFieldStore source = newStore();
        Set<Long> chunksWithData = populateRadius(source, centerX, centerY, centerZ, radius);
        require(chunksWithData.size() > 1, "radius-20 fixture must span multiple chunks");

        LightFieldStore target = newStore();
        int minChunkX = Math.floorDiv(centerX - radius, 16);
        int maxChunkX = Math.floorDiv(centerX + radius, 16);
        int minChunkZ = Math.floorDiv(centerZ - radius, 16);
        int maxChunkZ = Math.floorDiv(centerZ + radius, 16);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                long key = chunkKey(chunkX, chunkZ);
                List<SectionBlob> blobs = source.encodeChunk(chunkX, chunkZ, minSection, maxSection);
                if (chunksWithData.contains(key)) {
                    require(blobs != null, "expected radius-20 payload data at " + chunkX + "," + chunkZ);
                } else {
                    require(blobs == null, "unexpected radius-20 payload data at " + chunkX + "," + chunkZ);
                    continue;
                }
                LightFieldPayload encoded = LightFieldPayload.of(chunkX, chunkZ, blobs);
                require(encoded != null, "radius-20 payload construction failed at " + chunkX + "," + chunkZ);
                LightFieldPayload decoded = roundTripPayload(encoded);
                require(decoded.chunkX() == chunkX && decoded.chunkZ() == chunkZ,
                        "payload chunk coordinates changed during codec round-trip");
                target.decodeChunk(chunkX, chunkZ, decoded.sections());
            }
        }
        assertRadiusField(target, centerX, centerY, centerZ, radius);
    }

    private static void radius20ClientCacheIsClearedAfterServerBreak() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ClientLevel level = newClientLevel();
        Minecraft minecraft = newMinecraft();
        Field instanceField = Minecraft.class.getDeclaredField("instance");
        Field levelField = Minecraft.class.getDeclaredField("level");
        instanceField.setAccessible(true);
        levelField.setAccessible(true);
        Object previousInstance = instanceField.get(null);
        Object previousLevel = levelField.get(minecraft);
        try {
            instanceField.set(null, minecraft);
            levelField.set(minecraft, level);

            LightFieldStore server = newStore();
            LightFieldStore client = LightFieldStore.forLevel(level);
            populateRadius(server, 0, 0, 0, 20);
            LightFieldPayload payload = payloadForChunk(server, 0, 0, -2, 1);
            LightFieldClientHandler.handle(roundTripPayload(payload));
            long centerSection = SectionPos.asLong(0, 0, 0);
            long neighboringSection = SectionPos.asLong(1, 0, 0);
            require(client.getCell(centerSection, 0, 0, 0) == 20,
                    "client cache must receive the radius-20 center cell");
            LightFieldPayload neighboringPayload = payloadForChunk(server, 1, 0, -2, 1);
            LightFieldClientHandler.handle(roundTripPayload(neighboringPayload));
            require(client.getCell(neighboringSection, 0, 0, 0) > 0,
                    "client cache must receive neighboring chunk data");

            server.evictChunk(0, 0);
            require(server.encodeChunk(0, 0, -2, 1) == null,
                    "server break fixture must produce no chunk payload");
            LightFieldPayload emptyPayload = LightFieldPayload.empty(0, 0);
            require(emptyPayload.sections().isEmpty(), "explicit empty payload must remain a chunk tombstone");
            LightFieldClientHandler.handle(roundTripPayload(emptyPayload));
            require(client.getCell(centerSection, 0, 0, 0) == 0,
                    "empty server payload must clear the stale client chunk cache");
            require(client.getCell(neighboringSection, 0, 0, 0) > 0,
                    "empty payload eviction must remain scoped to its chunk");
        } finally {
            levelField.set(minecraft, previousLevel);
            instanceField.set(null, previousInstance);
            LightFieldStore.remove(level);
        }
    }

    private static LightFieldPayload payloadForChunk(LightFieldStore store,
                                                      int chunkX,
                                                      int chunkZ,
                                                      int minSection,
                                                      int maxSection) {
        List<SectionBlob> blobs = store.encodeChunk(chunkX, chunkZ, minSection, maxSection);
        require(blobs != null, "expected payload data at " + chunkX + "," + chunkZ);
        LightFieldPayload payload = LightFieldPayload.of(chunkX, chunkZ, blobs);
        require(payload != null, "payload construction failed at " + chunkX + "," + chunkZ);
        return payload;
    }

    private static LightFieldPayload roundTripPayload(LightFieldPayload payload) {
        var buffer = Unpooled.buffer();
        var buf = new RegistryFriendlyByteBuf(buffer, RegistryAccess.EMPTY);
        try {
            LightFieldPayload.STREAM_CODEC.encode(buf, payload);
            buf.readerIndex(0);
            return LightFieldPayload.STREAM_CODEC.decode(buf);
        } finally {
            buf.release();
        }
    }

    private static ClientLevel newClientLevel() throws Exception {
        return (ClientLevel) unsafe().allocateInstance(ClientLevel.class);
    }

    private static Minecraft newMinecraft() throws Exception {
        return (Minecraft) unsafe().allocateInstance(Minecraft.class);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void radius20PlaceSaveReloadBreak() throws ReflectiveOperationException {
        final int radius = 20;
        final int centerX = 0;
        final int centerY = 0;
        final int centerZ = 0;
        final int minSection = Math.floorDiv(centerY - radius, 16);
        final int maxSection = Math.floorDiv(centerY + radius, 16);

        LightFieldStore placed = newStore();
        Set<Long> chunksWithData = populateRadius(placed, centerX, centerY, centerZ, radius);
        require(chunksWithData.size() > 1, "radius-20 fixture must span multiple chunks");

        Map<Long, CompoundTag> saved = new HashMap<>();
        int minChunkX = Math.floorDiv(centerX - radius, 16);
        int maxChunkX = Math.floorDiv(centerX + radius, 16);
        int minChunkZ = Math.floorDiv(centerZ - radius, 16);
        int maxChunkZ = Math.floorDiv(centerZ + radius, 16);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                long chunkKey = chunkKey(chunkX, chunkZ);
                CompoundTag tag = new CompoundTag();
                placed.writeTag(tag, chunkX, chunkZ, minSection, maxSection);
                saved.put(chunkKey, tag);
                require(tag.contains(LightFieldStore.NBT_KEY, Tag.TAG_LIST) == chunksWithData.contains(chunkKey),
                        "radius-20 chunk persistence mismatch at " + chunkX + "," + chunkZ);
            }
        }

        LightFieldStore reloaded = newStore();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                CompoundTag tag = saved.get(chunkKey(chunkX, chunkZ));
                require(tag != null, "missing saved chunk tag at " + chunkX + "," + chunkZ);
                reloaded.readTag(tag, chunkX, chunkZ);
            }
        }
        assertRadiusField(reloaded, centerX, centerY, centerZ, radius);

        clearRadius(reloaded, centerX, centerY, centerZ, radius);
        require(reloaded.isEmpty(), "break must remove every extended-light section");
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                CompoundTag cleared = new CompoundTag();
                reloaded.writeTag(cleared, chunkX, chunkZ, minSection, maxSection);
                require(!cleared.contains(LightFieldStore.NBT_KEY),
                        "break must remove persisted light at " + chunkX + "," + chunkZ);
            }
        }
    }

    private static Set<Long> populateRadius(LightFieldStore store,
                                             int centerX,
                                             int centerY,
                                             int centerZ,
                                             int radius) {
        Set<Long> chunksWithData = new HashSet<>();
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int y = centerY - radius; y <= centerY + radius; y++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    int dx = x - centerX;
                    int dy = y - centerY;
                    int dz = z - centerZ;
                    int level = levelAtDistance(dx * dx + dy * dy + dz * dz, radius);
                    if (level <= 0) {
                        continue;
                    }
                    long sectionPos = SectionPos.asLong(Math.floorDiv(x, 16),
                            Math.floorDiv(y, 16), Math.floorDiv(z, 16));
                    store.setCell(sectionPos, SectionPos.sectionRelative(x),
                            SectionPos.sectionRelative(y), SectionPos.sectionRelative(z), level);
                    chunksWithData.add(chunkKey(Math.floorDiv(x, 16), Math.floorDiv(z, 16)));
                }
            }
        }
        return chunksWithData;
    }

    private static void assertRadiusField(LightFieldStore store,
                                           int centerX,
                                           int centerY,
                                           int centerZ,
                                           int radius) {
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int y = centerY - radius; y <= centerY + radius; y++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    int dx = x - centerX;
                    int dy = y - centerY;
                    int dz = z - centerZ;
                    int expected = levelAtDistance(dx * dx + dy * dy + dz * dz, radius);
                    long sectionPos = SectionPos.asLong(Math.floorDiv(x, 16),
                            Math.floorDiv(y, 16), Math.floorDiv(z, 16));
                    int actual = store.getCell(sectionPos, SectionPos.sectionRelative(x),
                            SectionPos.sectionRelative(y), SectionPos.sectionRelative(z));
                    require(actual == expected, "radius-20 value mismatch at " + x + "," + y + "," + z
                            + ": expected " + expected + ", got " + actual);
                }
            }
        }
    }

    private static void clearRadius(LightFieldStore store,
                                     int centerX,
                                     int centerY,
                                     int centerZ,
                                     int radius) {
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int y = centerY - radius; y <= centerY + radius; y++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    int dx = x - centerX;
                    int dy = y - centerY;
                    int dz = z - centerZ;
                    if (levelAtDistance(dx * dx + dy * dy + dz * dz, radius) <= 0) {
                        continue;
                    }
                    long sectionPos = SectionPos.asLong(Math.floorDiv(x, 16),
                            Math.floorDiv(y, 16), Math.floorDiv(z, 16));
                    store.setCell(sectionPos, SectionPos.sectionRelative(x),
                            SectionPos.sectionRelative(y), SectionPos.sectionRelative(z), 0);
                }
            }
        }
    }

    private static int levelAtDistance(int distanceSquared, int radius) {
        if (distanceSquared > radius * radius) {
            return 0;
        }
        int distance = (int) Math.ceil(Math.sqrt(distanceSquared));
        return Math.min(radius, Math.max(1, radius - distance + 1));
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    private static LightFieldStore newStore() throws ReflectiveOperationException {
        Constructor<LightFieldStore> constructor = LightFieldStore.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
