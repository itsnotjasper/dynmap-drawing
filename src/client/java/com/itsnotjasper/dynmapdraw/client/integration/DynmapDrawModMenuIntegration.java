package com.itsnotjasper.dynmapdraw.client.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.itsnotjasper.dynmapdraw.client.config.DynmapDrawConfigScreenFactory;

public final class DynmapDrawModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return DynmapDrawConfigScreenFactory::create;
    }
}
