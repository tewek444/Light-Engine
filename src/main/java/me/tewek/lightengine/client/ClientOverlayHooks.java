package me.tewek.lightengine.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.tewek.lightengine.LightEngine;
import me.tewek.lightengine.client.gui.UpdateChecker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Client-only wiring for the light overlay: {@code /lightengine light_level}
 * toggles it, {@link LightOverlayRenderer} draws it after translucent blocks.
 * Registered from {@code LightEngineClient}, never loaded on a server.
 */
public final class ClientOverlayHooks {
    private ClientOverlayHooks() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(ClientOverlayHooks.class);
    }

    @SubscribeEvent
    public static void onClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("lightengine")
                .then(Commands.literal("light_level").executes(ctx -> {
                    LightOverlayRenderer.announce(LightOverlayRenderer.toggle());
                    return 1;
                }))
                .then(Commands.literal("update_check")
                        .executes(ctx -> {
                            UpdateChecker.checkCurrent(LightEngine.LOADER);
                            tell("LightEngine: checking for updates...");
                            return 1;
                        })
                        .then(Commands.argument("version", StringArgumentType.word())
                                .executes(ctx -> {
                                    String fake = StringArgumentType.getString(ctx, "version");
                                    UpdateChecker.check(fake, LightEngine.LOADER);
                                    tell("LightEngine: checking for updates as " + fake + "...");
                                    return 1;
                                }))));
        dispatcher.register(Commands.literal("leclient").executes(ctx -> LeClientDump.run()));
    }

    private static void tell(String message) {
        LightEngine.LOGGER.info(message);
        try {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
            }
        } catch (Exception ignored) {
        }
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (!LightOverlayRenderer.isEnabled()) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        LightOverlayRenderer.render(event.getPoseStack(), cam.x, cam.y, cam.z,
                net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource());
    }
}
