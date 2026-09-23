package me.tewek.lightengine.client;

import me.tewek.lightengine.lightfield.LightField;
import me.tewek.lightengine.network.LightFieldPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.multiplayer.ClientLevel;
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
                dispatcher.register(ClientCommandManager.literal("lightengine")
                        .then(ClientCommandManager.literal("light_level").executes(ctx -> {
                            LightOverlayRenderer.announce(LightOverlayRenderer.toggle());
                            return 1;
                        }))));
        // Client store dump (chat + clipboard).
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("leclient").executes(ctx -> LeClientDump.run())));
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if (!LightOverlayRenderer.isEnabled()) {
                return;
            }
            Vec3 cam = context.camera().getPosition();
            LightOverlayRenderer.render(context.matrixStack(), cam.x, cam.y, cam.z, context.consumers());
        });
    }
}
