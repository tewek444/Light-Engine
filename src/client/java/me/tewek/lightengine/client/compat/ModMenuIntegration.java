package me.tewek.lightengine.client.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.tewek.lightengine.client.gui.LightEditorScreen;

/**
 * Optional ModMenu integration. Opens the light editor directly.
 * Only loaded when ModMenu is installed.
 */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return LightEditorScreen::new;
    }
}
