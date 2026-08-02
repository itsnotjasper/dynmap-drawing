package com.itsnotjasper.dynmapdraw.config;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public final class DynmapExportPaths {
    private static final Path DEFAULT_EXPORTS_DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("dynmap-draw")
            .resolve("exports");

    private DynmapExportPaths() {
    }

    public static Path resolveConfiguredExportsDir() {
        String configured = DynmapDrawConfig.get().dynmapExportPath;
        if (configured == null || configured.isBlank()) {
            return DEFAULT_EXPORTS_DIR;
        }

        Path path = Path.of(configured);
        if (!path.isAbsolute()) {
            path = FabricLoader.getInstance().getGameDir().resolve(path);
        }
        return path.normalize();
    }
}
