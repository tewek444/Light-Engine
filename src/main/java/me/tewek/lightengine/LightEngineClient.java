package me.tewek.lightengine;

import com.mojang.brigadier.arguments.StringArgumentType;
import me.tewek.lightengine.client.ColorLightEnv;
import me.tewek.lightengine.client.LeClientDump;
import me.tewek.lightengine.client.LightOverlayRenderer;
import me.tewek.lightengine.client.gui.LightEditorScreen;
import me.tewek.lightengine.client.gui.UpdateChecker;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;

/** Forge 1.20.1 client wiring. Only called on the physical client. */
public final class LightEngineClient {
    private LightEngineClient() {
    }

    public static void init() {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new LightEditorScreen(parent)));
        MinecraftForge.EVENT_BUS.register(LightEngineClient.class);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        try {
            ColorLightEnv.tick();
        } catch (Exception ex) {
            LightEngine.LOGGER.debug("Colored light env tick failed", ex);
        }
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !LightOverlayRenderer.isEnabled()) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        LightOverlayRenderer.render(event.getPoseStack(), cam.x, cam.y, cam.z,
                Minecraft.getInstance().renderBuffers().bufferSource());
    }

    @SubscribeEvent
    public static void onClientCommands(RegisterClientCommandsEvent event) {
        for (String root : new String[]{"le", "light_engine"}) {
            event.getDispatcher().register(Commands.literal(root)
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
        }
        for (String root : new String[]{"le", "light_engine"}) {
            event.getDispatcher().register(Commands.literal(root).then(Commands.literal("dump_client").executes(ctx -> LeClientDump.run())));
        }
    }

    private static void tell(String message) {
        LightEngine.LOGGER.info(message);
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendSystemMessage(Component.literal(message));
        }
    }
}
