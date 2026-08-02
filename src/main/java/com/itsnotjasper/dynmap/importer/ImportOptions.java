package com.itsnotjasper.dynmap.importer;

import com.itsnotjasper.dynmapdraw.config.DynmapDrawConfig;

public final class ImportOptions {
    public final double defaultY;
    public final String targetSet;
    public final String author;
    public final boolean skipHidden;

    public ImportOptions(double defaultY, String targetSet, String author, boolean skipHidden) {
        this.defaultY = defaultY;
        this.targetSet = targetSet;
        this.author = author;
        this.skipHidden = skipHidden;
    }

    public static ImportOptions fromConfig(String author) {
        DynmapDrawConfig config = DynmapDrawConfig.get();
        return new ImportOptions(
                config.importDefaultY,
                config.importDefaultSet,
                author,
                true
        );
    }
}
