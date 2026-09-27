package me.tewek.lightengine;

import me.tewek.lightengine.lightfield.LightField;
import me.tewek.lightengine.network.LightFieldPayload;
import me.tewek.lightengine.network.ProfileUpdateHandler;
import me.tewek.lightengine.network.ProfileUpdatePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LightEngine implements ModInitializer {
    public static final String MOD_ID = "lightengine";
    public static final String VERSION = "1.0.0-alpha3";
    public static final String LOADER = "fabric";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LightEngineConfig.init();
        PayloadTypeRegistry.clientboundPlay().register(LightFieldPayload.TYPE, LightFieldPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ProfileUpdatePayload.TYPE, ProfileUpdatePayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ProfileUpdatePayload.TYPE,
                (payload, context) -> context.server().execute(
                        () -> ProfileUpdateHandler.handle(context.player(), payload.id(), payload.radius(), payload.ignoreConditions())));
        ServerLevelEvents.UNLOAD.register((server, world) -> LightField.drop(world));
        // Diagnostic commands: server store dump + orphan repair.
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> LeDumpCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> LeRepairCommand.register(dispatcher));
    }
}
