package me.tewek.lightengine;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import me.tewek.lightengine.client.ClientOverlayHooks;
import me.tewek.lightengine.client.gui.LightEditorScreen;

@Mod(value = LightEngine.MODID, dist = Dist.CLIENT)
public class LightEngineClient {
    public LightEngineClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (modContainer, parent) -> new LightEditorScreen(parent));
        ClientOverlayHooks.register();
    }
}
