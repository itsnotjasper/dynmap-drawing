package com.itsnotjasper.dynmapdraw.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.itsnotjasper.dynmapdraw.config.DynmapDrawConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DynmapDrawConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("dynmap-draw.json");

    private DynmapDrawConfigManager() {
    }

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            return;
        }

        try {
            String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            DynmapDrawConfig loaded = GSON.fromJson(json, DynmapDrawConfig.class);
            if (loaded != null) {
                loaded.clamp();
                DynmapDrawConfig.set(loaded);
            }
        } catch (IOException | JsonSyntaxException ignored) {
        }
    }

    public static void save() {
        DynmapDrawConfig config = DynmapDrawConfig.get();
        config.clamp();
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(config), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }
}
