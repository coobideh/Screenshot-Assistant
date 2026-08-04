package net.pouyan.screenshotassistant;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.pouyan.screenshotassistant.gui.RuleManagerScreen;

/**
 * Only loaded if Mod Menu is present (see the "modmenu" entrypoint in
 * fabric.mod.json). Points the "Mods" screen's config button at the
 * rule manager, which itself links to the general settings.
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return RuleManagerScreen::new;
    }
}
