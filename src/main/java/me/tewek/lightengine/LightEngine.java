package me.tewek.lightengine;

import com.mojang.logging.LogUtils;
import me.tewek.lightengine.lightfield.ChromaTint;
import me.tewek.lightengine.lightfield.LightField;
import me.tewek.lightengine.lightfield.LightFieldStore;
import me.tewek.lightengine.network.LightFieldClientHandler;
import me.tewek.lightengine.network.FieldSync;
import me.tewek.lightengine.network.LightFieldPayload;
import me.tewek.lightengine.network.ProfileSyncClientHandler;
import me.tewek.lightengine.network.ProfileSyncPayload;
import me.tewek.lightengine.network.ProfileUpdateHandler;
import me.tewek.lightengine.network.ProfileUpdatePayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.InterModProcessEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

@Mod(LightEngine.MODID)
public class LightEngine {
    public static final String MODID = "lightengine";
    /** Installed version, read from the mod metadata (single source: gradle.properties). */
    public static volatile String VERSION = "unknown";
    public static final String LOADER = "neoforge";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LightEngine(IEventBus modEventBus, ModContainer modContainer) {
        VERSION = modContainer.getModInfo().getVersion().toString();
        modContainer.registerConfig(ModConfig.Type.COMMON, LightEngineSpec.INSTANCE);
        modEventBus.addListener(LightEngineSpec::onLoad);
        modEventBus.addListener(LightEngineSpec::onReload);
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::processImc);
        NeoForge.EVENT_BUS.register(this);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        var fieldRegistrar = event.registrar(LightFieldPayload.PROTOCOL_VERSION);
        fieldRegistrar.playToClient(
                LightFieldPayload.TYPE,
                LightFieldPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> LightFieldClientHandler.handle(payload)));
        fieldRegistrar.playToServer(
                ProfileUpdatePayload.TYPE,
                ProfileUpdatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        ProfileUpdateHandler.handle(player, payload.id(), payload.radius(),
                                payload.ignoreConditions(), payload.color(), payload.tintEnabled());
                    }
                }));
        var syncRegistrar = event.registrar("1");
        syncRegistrar.playToClient(
                ProfileSyncPayload.TYPE,
                ProfileSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ProfileSyncClientHandler.handle(payload)));
    }

    private void processImc(InterModProcessEvent event) {
        InterModComms.getMessages(MODID).forEach(msg -> {
            boolean ok;
            try {
                ok = LightEngineApi.handleImc(msg.method(), msg.messageSupplier().get());
            } catch (RuntimeException ex) {
                ok = false;
                LOGGER.warn("Bad IMC '{}' from {}", msg.method(), msg.senderModId(), ex);
            }
            if (!ok) {
                LOGGER.warn("Ignored IMC '{}' from {} (see LightEngineApi docs for the format)",
                        msg.method(), msg.senderModId());
            }
        });
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            LightField.drop(level);
            ChromaTint.drop(level);
        }
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            try {
                LightFieldStore.forLevel(level).evictChunk(
                        event.getChunk().getPos().x, event.getChunk().getPos().z);
                ChromaTint.onChunkUnload(level, event.getChunk().getPos());
            } catch (Exception ex) {
                LOGGER.debug("Chunk unload cleanup failed at {}", event.getChunk().getPos(), ex);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            try {
                PacketDistributor.sendToPlayer(player,
                        ProfileSyncPayload.of(LightProfileRegistry.snapshot()));
            } catch (Exception ex) {
                LOGGER.warn("Could not send light profiles to {}", player.getGameProfile().name(), ex);
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        try {
            FieldSync.tick();
        } catch (Exception ex) {
            LOGGER.debug("Light field sync tick failed", ex);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LeDumpCommand.register(event.getDispatcher());
        LeRepairCommand.register(event.getDispatcher());
    }
}
