package dev.nbtpeek.menu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Adds the "Configure" button for NBT Peek in Mod Menu. Only loaded when Mod Menu is installed. */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new PeekConfigScreen(parent);
    }
}
