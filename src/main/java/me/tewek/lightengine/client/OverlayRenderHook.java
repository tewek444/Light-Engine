package me.tewek.lightengine.client;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the light-level overlay through Fabric API's
 * {@code rendering.v1.world.WorldRenderEvents}. That package only exists in
 * the Fabric API builds for 1.21.10; the last build for 1.21.9 (0.134.1)
 * lacks it, so this class is kept apart and registered behind a
 * {@code LinkageError} guard: on such a Fabric API the mod still loads, only
 * without the debug overlay.
 */
public final class OverlayRenderHook {
    private OverlayRenderHook() {
    }

    public static void register() {
        WorldRenderEvents.END_MAIN.register(context -> {
            if (!LightOverlayRenderer.isEnabled() || context.matrices() == null) {
                return;
            }
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            LightOverlayRenderer.render(context.matrices(), cam.x, cam.y, cam.z,
                    Minecraft.getInstance().renderBuffers().bufferSource());
        });
    }
}
