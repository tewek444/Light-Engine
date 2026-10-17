package me.tewek.lightengine.client.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.tewek.lightengine.client.gui.LightEditorScreen;

/** Mod Menu "Configure" button opens the light editor (optional dependency). */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return LightEditorScreen::new;
    }
}
