package me.tewek.lightengine.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.tewek.lightengine.LightEngine;
import me.tewek.lightengine.client.gui.UpdateChecker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-only wiring for the light overlay: {@code /lightengine light_level}
 * toggles it, {@link LightOverlayRenderer} draws it pinned to block faces
 * after the vanilla HUD. Registered from {@code LightEngineClient}, never
 * loaded on a server.
 */
public final class ClientOverlayHooks {
    private ClientOverlayHooks() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.register(ClientOverlayHooks.class);
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
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!LightOverlayRenderer.isEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.gui.screen() != null) {
            return;
        }
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        LightOverlayRenderer.render(graphics, cam.x, cam.y, cam.z,
                graphics.guiWidth(), graphics.guiHeight());
    }
}
