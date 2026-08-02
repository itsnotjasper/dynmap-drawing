package com.itsnotjasper.dynmap.importer;

import java.nio.file.Path;

public interface LineImporter {
    boolean supports(Path path);

    ImportResult importFile(Path path, ImportOptions options);
}
