package me.tewek.lightengine;

import com.mojang.logging.LogUtils;
import me.tewek.lightengine.lightfield.ChromaTint;
import me.tewek.lightengine.lightfield.LightField;
import me.tewek.lightengine.lightfield.LightFieldStore;
import me.tewek.lightengine.network.FieldSync;
import me.tewek.lightengine.network.LightFieldPayload;
import me.tewek.lightengine.network.ProfileSyncPayload;
import me.tewek.lightengine.network.ProfileUpdateHandler;
import me.tewek.lightengine.network.ProfileUpdatePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

/**
 * Fabric entrypoint: config, payloads, server/world events, commands, and the
 * {@code "lightengine"} entrypoint other mods use to register lights (the
 * Fabric counterpart of NeoForge IMC; see {@link LightEngineEntrypoint}).
 */
public class LightEngine implements ModInitializer {
    public static final String MODID = "lightengine";
    public static final String LOADER = "fabric";
    public static final Logger LOGGER = LogUtils.getLogger();
    /** Installed version, read from fabric.mod.json. */
    public static volatile String VERSION = "unknown";

    @Override
    public void onInitialize() {
        VERSION = FabricLoader.getInstance().getModContainer(MODID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
        LightEngineConfig.init();

        PayloadTypeRegistry.clientboundPlay().register(LightFieldPayload.TYPE, LightFieldPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ProfileSyncPayload.TYPE, ProfileSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ProfileUpdatePayload.TYPE, ProfileUpdatePayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ProfileUpdatePayload.TYPE, (payload, context) ->
                context.server().execute(() -> ProfileUpdateHandler.handle(context.player(), payload.id(),
                        payload.radius(), payload.ignoreConditions(), payload.color(), payload.tintEnabled())));

        ServerLevelEvents.UNLOAD.register((server, level) -> {
            LightField.drop(level);
            ChromaTint.drop(level);
        });
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            try {
                LightFieldStore.forLevel(level).evictAfterSave(level, chunk.getPos().x(), chunk.getPos().z());
                ChromaTint.onChunkUnload(level, chunk.getPos());
            } catch (Exception ex) {
                LOGGER.debug("Chunk unload cleanup failed at {}", chunk.getPos(), ex);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            try {
                ServerPlayNetworking.send(handler.getPlayer(), ProfileSyncPayload.of(LightProfileRegistry.snapshot()));
            } catch (Exception ex) {
                LOGGER.warn("Could not send light profiles to {}", handler.getPlayer().getGameProfile().name(), ex);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                FieldSync.tick();
            } catch (Exception ex) {
                LOGGER.debug("Light field sync tick failed", ex);
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LeDumpCommand.register(dispatcher);
            LeRepairCommand.register(dispatcher);
        });

        for (LightEngineEntrypoint ep : FabricLoader.getInstance()
                .getEntrypoints(MODID, LightEngineEntrypoint.class)) {
            try {
                ep.registerLights();
            } catch (RuntimeException ex) {
                LOGGER.warn("A mod's lightengine entrypoint failed", ex);
            }
        }
    }
}
