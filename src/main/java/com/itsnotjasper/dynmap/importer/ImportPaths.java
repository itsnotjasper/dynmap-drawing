package com.itsnotjasper.dynmap.importer;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class ImportPaths {
    private ImportPaths() {
    }

    public static Path importsDir() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve("dynmap-draw")
                .resolve("imports");
    }

    public static List<Path> listImportFiles() {
        Path dir = importsDir();
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
            return List.of();
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(ImportPaths::isSupportedImportFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(files::add);
        } catch (IOException ignored) {
        }
        return files;
    }

    public static boolean isSupportedImportFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".ann") || name.endsWith(".json");
    }
}
