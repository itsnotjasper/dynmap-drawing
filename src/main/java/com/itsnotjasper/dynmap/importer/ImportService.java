package com.itsnotjasper.dynmap.importer;

import com.itsnotjasper.dynmap.model.LineDraft;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class ImportService {
    private ImportService() {
    }

    public static ImportResult importFile(Path path, ImportOptions options, Set<String> existingLineIds) {
        Optional<LineImporter> importer = ImportRegistry.findImporter(path);
        if (importer.isEmpty()) {
            ImportResult result = new ImportResult();
            result.setErrorMessage("Unsupported file type: " + path.getFileName());
            return result;
        }

        ImportResult result = importer.get().importFile(path, options);
        if (result.hasError()) {
            return result;
        }

        Set<String> reservedIds = new HashSet<>(existingLineIds);
        for (LineDraft draft : result.drafts()) {
            if (draft.line == null || draft.line.lineId == null) {
                continue;
            }
            String uniqueId = uniqueLineId(draft.line.lineId, reservedIds);
            draft.line.lineId = uniqueId;
            reservedIds.add(uniqueId);
        }
        return result;
    }

    static String uniqueLineId(String baseId, Set<String> reservedIds) {
        String candidate = sanitizeLineId(baseId);
        if (candidate.isEmpty()) {
            candidate = "imported_line";
        }
        if (!reservedIds.contains(candidate)) {
            return candidate;
        }
        int suffix = 2;
        while (reservedIds.contains(candidate + "_" + suffix)) {
            suffix++;
        }
        return candidate + "_" + suffix;
    }

    static String sanitizeLineId(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String lower = raw.toLowerCase(Locale.ROOT).trim();
        StringBuilder builder = new StringBuilder(Math.min(lower.length(), 64));
        for (int i = 0; i < lower.length() && builder.length() < 64; i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                builder.append(c);
            } else if (c == ' ' || c == '-' || c == '_') {
                if (!builder.isEmpty() && builder.charAt(builder.length() - 1) != '_') {
                    builder.append('_');
                }
            }
        }
        while (!builder.isEmpty() && builder.charAt(builder.length() - 1) == '_') {
            builder.deleteCharAt(builder.length() - 1);
        }
        return builder.toString();
    }
}
