package com.itsnotjasper.dynmapdraw.client.config;

import com.itsnotjasper.dynmapdraw.config.DynmapDrawConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class DynmapDrawConfigScreenFactory {
    private DynmapDrawConfigScreenFactory() {
    }

    public static Screen create(Screen parent) {
        DynmapDrawConfig config = DynmapDrawConfig.get();
        ConfigEntryBuilder entryBuilder = ConfigEntryBuilder.create();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("title.dynmap-draw.config"))
                .setSavingRunnable(DynmapDrawConfigManager::save);

        ConfigCategory dynmap = builder.getOrCreateCategory(
                Component.translatable("category.dynmap-draw.drawing")
        );

        dynmap.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("option.dynmap-draw.enable_dynmap_draw_hud"),
                        config.enableDynmapDrawHud
                )
                .setDefaultValue(true)
                .setTooltip(Component.translatable("option.dynmap-draw.enable_dynmap_draw_hud.tooltip"))
                .setSaveConsumer(value -> config.enableDynmapDrawHud = value)
                .build());

        dynmap.addEntry(entryBuilder.startStrField(
                        Component.translatable("option.dynmap-draw.dynmap_export_path"),
                        config.dynmapExportPath
                )
                .setDefaultValue("")
                .setTooltip(Component.translatable("option.dynmap-draw.dynmap_export_path.tooltip"))
                .setSaveConsumer(value -> config.dynmapExportPath = value == null ? "" : value.trim())
                .build());

        dynmap.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("option.dynmap-draw.dynmap_corner_from_crosshair"),
                        config.dynmapCornerFromCrosshair
                )
                .setDefaultValue(false)
                .setTooltip(Component.translatable("option.dynmap-draw.dynmap_corner_from_crosshair.tooltip"))
                .setSaveConsumer(value -> config.dynmapCornerFromCrosshair = value)
                .build());

        dynmap.addEntry(entryBuilder.startStrField(
                        Component.translatable("option.dynmap-draw.dynmap_preview_url"),
                        config.dynmapPreviewUrl
                )
                .setDefaultValue("https://dynmap.minecartrapidtransit.net/main/")
                .setTooltip(Component.translatable("option.dynmap-draw.dynmap_preview_url.tooltip"))
                .setSaveConsumer(value -> config.dynmapPreviewUrl = value == null || value.isBlank()
                        ? "https://dynmap.minecartrapidtransit.net/main/"
                        : value.trim())
                .build());

        return builder.build();
    }
}
