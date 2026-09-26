package me.tewek.lightengine;

import com.mojang.logging.LogUtils;
import me.tewek.lightengine.lightfield.LightField;
import me.tewek.lightengine.network.LightFieldClientHandler;
import me.tewek.lightengine.network.LightFieldPayload;
import me.tewek.lightengine.network.ProfileUpdateHandler;
import me.tewek.lightengine.network.ProfileUpdatePayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

@Mod(LightEngine.MODID)
public class LightEngine {
    public static final String MODID = "lightengine";
    public static final String VERSION = "1.0.0-alpha3";
    public static final String LOADER = "neoforge";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LightEngine(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, LightEngineSpec.INSTANCE);
        modEventBus.addListener(Config::onLoad);
        modEventBus.addListener(Config::onReload);
        modEventBus.addListener(this::registerPayloads);
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
                        ProfileUpdateHandler.handle(player, payload.id(), payload.radius(), payload.ignoreConditions());
                    }
                }));
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            LightField.drop(level);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LeDumpCommand.register(event.getDispatcher());
        LeRepairCommand.register(event.getDispatcher());
    }
}
