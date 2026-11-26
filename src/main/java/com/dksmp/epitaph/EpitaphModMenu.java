package com.dksmp.epitaph;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Подключение к Mod Menu: кнопка настроек у мода. */
public class EpitaphModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return EpitaphConfigScreen::new;
    }
}
