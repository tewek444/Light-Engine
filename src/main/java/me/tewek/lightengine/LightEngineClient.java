package me.tewek.lightengine;

import com.mojang.brigadier.arguments.StringArgumentType;
import me.tewek.lightengine.client.ColorLightEnv;
import me.tewek.lightengine.client.LeClientDump;
import me.tewek.lightengine.client.LightOverlayRenderer;
import me.tewek.lightengine.client.gui.UpdateChecker;
import me.tewek.lightengine.lightfield.ChromaTint;
import me.tewek.lightengine.lightfield.LightField;
import me.tewek.lightengine.lightfield.LightFieldStore;
import me.tewek.lightengine.network.LightFieldClientHandler;
import me.tewek.lightengine.network.LightFieldPayload;
import me.tewek.lightengine.network.ProfileSyncClientHandler;
import me.tewek.lightengine.network.ProfileSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** Fabric client entrypoint: payload receivers, client events, client commands, overlay. */
public class LightEngineClient implements ClientModInitializer {
    /** Server-side /le subcommands the client tree must hand over. */
    private static final String[] SERVER_SUBCOMMANDS = {"dump", "repair"};
    private static final com.mojang.brigadier.exceptions.SimpleCommandExceptionType FORWARD_TO_SERVER =
            com.mojang.brigadier.exceptions.CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand();

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(LightFieldPayload.TYPE, (payload, context) ->
                context.client().execute(() -> LightFieldClientHandler.handle(payload)));
        ClientPlayNetworking.registerGlobalReceiver(ProfileSyncPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ProfileSyncClientHandler.handle(payload)));

        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            try {
                LightFieldStore.forLevel(level).evictChunk(chunk.getPos().x, chunk.getPos().z);
                ChromaTint.onChunkUnload(level, chunk.getPos());
            } catch (Exception ex) {
                LightEngine.LOGGER.debug("Client chunk unload cleanup failed", ex);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (client.level != null) {
                LightField.drop(client.level);
                ChromaTint.drop(client.level);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                ColorLightEnv.tick();
            } catch (Exception ex) {
                LightEngine.LOGGER.debug("Colored light env tick failed", ex);
            }
        });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if (!LightOverlayRenderer.isEnabled() || context.matrixStack() == null || context.consumers() == null) {
                return;
            }
            Vec3 cam = context.camera().getPosition();
            LightOverlayRenderer.render(context.matrixStack(), cam.x, cam.y, cam.z,
                    Minecraft.getInstance().renderBuffers().bufferSource());
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            for (String root : new String[]{"le", "light_engine"}) {
                dispatcher.register(ClientCommandManager.literal(root)
                        .then(ClientCommandManager.literal("light_level").executes(ctx -> {
                            LightOverlayRenderer.announce(LightOverlayRenderer.toggle());
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("update_check")
                                .executes(ctx -> {
                                    UpdateChecker.checkCurrent(LightEngine.LOADER);
                                    tell("LightEngine: checking for updates...");
                                    return 1;
                                })
                                .then(ClientCommandManager.argument("version", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String fake = StringArgumentType.getString(ctx, "version");
                                            UpdateChecker.check(fake, LightEngine.LOADER);
                                            tell("LightEngine: checking for updates as " + fake + "...");
                                            return 1;
                                        }))));
            }
            for (String root : new String[]{"le", "light_engine"}) {
                dispatcher.register(ClientCommandManager.literal(root).then(ClientCommandManager.literal("dump_client").executes(ctx -> LeClientDump.run())));
            }
            // Server subcommands under the shared root: matched here and rethrown as
            // "unknown command" so Fabric API forwards them to the server.
            for (String root : new String[]{"le", "light_engine"}) {
                for (String server : SERVER_SUBCOMMANDS) {
                    dispatcher.register(ClientCommandManager.literal(root).then(ClientCommandManager.literal(server)
                            .executes(ctx -> {
                                throw FORWARD_TO_SERVER.create();
                            })
                            .then(ClientCommandManager.argument("args", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        throw FORWARD_TO_SERVER.create();
                                    }))));
                }
            }
        });
    }

    private static void tell(String message) {
        LightEngine.LOGGER.info(message);
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendSystemMessage(Component.literal(message));
        }
    }
}
