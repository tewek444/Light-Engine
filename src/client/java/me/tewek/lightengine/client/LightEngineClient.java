package me.tewek.lightengine.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import me.tewek.lightengine.LightEngine;
import me.tewek.lightengine.client.gui.UpdateChecker;
import me.tewek.lightengine.lightfield.LightField;
import me.tewek.lightengine.network.LightFieldPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public class LightEngineClient implements ClientModInitializer {
    /**
     * Last client level seen on the render thread. Dropped on disconnect.
     * Scoped to the client level on purpose: the static stores are shared
     * with the integrated server in the same JVM, so a blanket drop-all on
     * disconnect would wipe SERVER data before it is saved (stale-light
     * ghost). Server levels are cleaned by their own unload event instead.
     */
    private static volatile ClientLevel rememberedLevel;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(LightFieldPayload.TYPE,
                (payload, context) -> context.client().execute(() -> LightFieldClientHandler.handle(payload)));
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
            ClientLevel level = rememberedLevel;
            rememberedLevel = null;
            if (level != null) {
                LightField.drop(level);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != null) {
                rememberedLevel = client.level;
            }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("lightengine")
                        .then(ClientCommands.literal("light_level").executes(ctx -> {
                            LightOverlayRenderer.announce(LightOverlayRenderer.toggle());
                            return 1;
                        }))
                        .then(ClientCommands.literal("update_check")
                                .executes(ctx -> {
                                    UpdateChecker.checkCurrent(LightEngine.LOADER);
                                    ctx.getSource().sendFeedback(Component.literal("LightEngine: checking for updates..."));
                                    return 1;
                                })
                                .then(ClientCommands.argument("version", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String fake = StringArgumentType.getString(ctx, "version");
                                            UpdateChecker.check(fake, LightEngine.LOADER);
                                            ctx.getSource().sendFeedback(Component.literal(
                                                    "LightEngine: checking for updates as " + fake + "..."));
                                            return 1;
                                        })))));
        // Client store dump (chat + clipboard).
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("leclient").executes(ctx -> LeClientDump.run())));
        // Screen-space overlay as the last HUD element (26.x has no immediate
        // world-space text and no HudRenderCallback; see LightOverlayRenderer).
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("lightengine", "light_overlay"),
                (graphics, delta) -> {
                    if (!LightOverlayRenderer.isEnabled()) {
                        return;
                    }
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.level == null || mc.gui.screen() != null) {
                        return;
                    }
                    Vec3 cam = mc.gameRenderer.mainCamera().position();
                    LightOverlayRenderer.render(graphics, cam.x, cam.y, cam.z,
                            graphics.guiWidth(), graphics.guiHeight());
                });
    }
}
