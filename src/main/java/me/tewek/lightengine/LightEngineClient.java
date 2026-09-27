package me.tewek.lightengine;

import me.tewek.lightengine.client.ClientOverlayHooks;
import me.tewek.lightengine.client.gui.LightEditorScreen;
import me.tewek.lightengine.lightfield.LightField;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * Client-only init, called from {@link LightEngine} behind a dist check.
 * Forge 1.20.1 has no {@code @Mod(dist=...)} split, so this is a plain class:
 * it is never touched on a dedicated server (class resolution is lazy, and
 * {@code init()} is only invoked on the client).
 */
public class LightEngineClient {
    /**
     * Last client level seen on the render thread. Dropped on disconnect.
     * Scoped to the client level on purpose: the static stores are shared
     * with the integrated server in the same JVM, so a blanket drop-all on
     * disconnect would wipe SERVER data before it is saved (stale-light
     * ghost). Server levels are cleaned by their own unload event instead.
     */
    private static volatile Level rememberedLevel;

    private LightEngineClient() {
    }

    public static void init() {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> new LightEditorScreen(parent)));
        ClientOverlayHooks.register();
        MinecraftForge.EVENT_BUS.register(LightEngineClient.class);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            rememberedLevel = level;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        Level level = rememberedLevel;
        rememberedLevel = null;
        if (level != null) {
            LightField.drop(level);
        }
    }
}
