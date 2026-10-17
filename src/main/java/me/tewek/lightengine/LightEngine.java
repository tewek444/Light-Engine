package me.tewek.lightengine;

import com.mojang.logging.LogUtils;
import me.tewek.lightengine.lightfield.ChromaTint;
import me.tewek.lightengine.lightfield.LightField;
import me.tewek.lightengine.lightfield.LightFieldStore;
import me.tewek.lightengine.network.FieldSync;
import me.tewek.lightengine.network.ModNetwork;
import me.tewek.lightengine.network.ProfileSyncPayload;
import me.tewek.lightengine.platform.Platform;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/** Forge 1.20.1 entrypoint: config, network, server/world events, commands, IMC. */
@Mod(LightEngine.MODID)
public class LightEngine {
    public static final String MODID = "lightengine";
    public static final String LOADER = "forge";
    public static final Logger LOGGER = LogUtils.getLogger();
    /** Installed version, read from the mod metadata. */
    public static volatile String VERSION = "unknown";

    public LightEngine() {
        VERSION = ModLoadingContext.get().getActiveContainer().getModInfo().getVersion().toString();
        ModNetwork.register();
        LightEngineConfig.init();
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::processImc);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            LightEngineClient.init();
        }
        MinecraftForge.EVENT_BUS.register(this);
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
                LightFieldStore.forLevel(level).evictAfterSave(level, event.getChunk().getPos().x, event.getChunk().getPos().z);
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
                Platform.sendToPlayer(player, ProfileSyncPayload.of(LightProfileRegistry.snapshot()));
            } catch (Exception ex) {
                LOGGER.warn("Could not send light profiles to {}", player.getGameProfile().getName(), ex);
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
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
