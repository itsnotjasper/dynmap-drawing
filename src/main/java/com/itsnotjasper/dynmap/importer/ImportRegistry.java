package com.itsnotjasper.dynmap.importer;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class ImportRegistry {
    private static final List<LineImporter> IMPORTERS = List.of(new AnnLineImporter());

    private ImportRegistry() {
    }

    public static Optional<LineImporter> findImporter(Path path) {
        for (LineImporter importer : IMPORTERS) {
            if (importer.supports(path)) {
                return Optional.of(importer);
            }
        }
        return Optional.empty();
    }
}
