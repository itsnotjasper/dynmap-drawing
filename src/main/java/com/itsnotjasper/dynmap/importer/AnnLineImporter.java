package com.itsnotjasper.dynmap.importer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.itsnotjasper.dynmap.model.Corner;
import com.itsnotjasper.dynmap.model.DynmapLine;
import com.itsnotjasper.dynmap.model.LineDraft;
import com.itsnotjasper.dynmap.model.RoadSetPreset;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AnnLineImporter implements LineImporter {
    private static final int MAX_LINE_ID_LENGTH = 64;

    @Override
    public boolean supports(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".ann") || fileName.endsWith(".json");
    }

    @Override
    public ImportResult importFile(Path path, ImportOptions options) {
        ImportResult result = new ImportResult();
        if (!RoadSetPreset.isSupported(options.targetSet)) {
            result.setErrorMessage("Invalid target set: " + options.targetSet);
            return result;
        }

        String json;
        try {
            json = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            result.setErrorMessage("Failed to read file: " + e.getMessage());
            return result;
        }

        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            result.setErrorMessage("Invalid JSON: " + e.getMessage());
            return result;
        }

        if (!root.has("features") || !root.get("features").isJsonArray()) {
            result.setErrorMessage("Expected GeoJSON FeatureCollection with a features array");
            return result;
        }

        JsonArray features = root.getAsJsonArray("features");
        int fallbackIndex = 1;
        for (JsonElement featureElement : features) {
            if (!featureElement.isJsonObject()) {
                result.incrementSkippedWrongShape();
                continue;
            }
            JsonObject feature = featureElement.getAsJsonObject();
            JsonObject properties = feature.has("properties") && feature.get("properties").isJsonObject()
                    ? feature.getAsJsonObject("properties")
                    : new JsonObject();

            String shape = stringProperty(properties, "shape");
            if (!"line".equals(shape)) {
                result.incrementSkippedWrongShape();
                continue;
            }
            if (options.skipHidden && properties.has("hidden") && properties.get("hidden").getAsBoolean()) {
                result.incrementSkippedHidden();
                continue;
            }
            if (!feature.has("geometry") || !feature.get("geometry").isJsonObject()) {
                result.incrementSkippedTooFewPoints();
                continue;
            }
            JsonObject geometry = feature.getAsJsonObject("geometry");
            if (!"LineString".equals(stringProperty(geometry, "type"))) {
                result.incrementSkippedTooFewPoints();
                continue;
            }
            if (!geometry.has("coordinates") || !geometry.get("coordinates").isJsonArray()) {
                result.incrementSkippedTooFewPoints();
                continue;
            }

            List<Corner> corners = parseLineString(geometry.getAsJsonArray("coordinates"), options.defaultY);
            if (corners.size() < 2) {
                result.incrementSkippedTooFewPoints();
                continue;
            }

            String label = stringProperty(properties, "annotationLabel");
            if (label == null || label.isBlank()) {
                label = "imported_line_" + fallbackIndex;
            }
            String lineId = ImportService.sanitizeLineId(label);
            if (lineId.isEmpty()) {
                lineId = "imported_line_" + fallbackIndex;
            }
            lineId = truncate(lineId, MAX_LINE_ID_LENGTH);

            DynmapLine line = DynmapLine.fromCorners(lineId, options.targetSet, label, corners);
            applyStyle(properties, line);
            result.addDraft(LineDraft.create(options.author, line));
            fallbackIndex++;
        }
        return result;
    }

    private static List<Corner> parseLineString(JsonArray coordinates, double defaultY) {
        List<Corner> corners = new ArrayList<>();
        for (JsonElement coordinateElement : coordinates) {
            if (!coordinateElement.isJsonArray()) {
                continue;
            }
            JsonArray point = coordinateElement.getAsJsonArray();
            if (point.size() < 2) {
                continue;
            }
            double mapLng = point.get(0).getAsDouble();
            double mapLat = point.get(1).getAsDouble();
            corners.add(MrtMapCoordinates.toCorner(mapLng, mapLat, defaultY));
        }
        return corners;
    }

    private static void applyStyle(JsonObject properties, DynmapLine line) {
        String strokeColor = stringProperty(properties, "strokeColor");
        if (strokeColor != null && !strokeColor.isBlank()) {
            line.color = strokeColor;
        }
        if (properties.has("weight") && properties.get("weight").isJsonPrimitive()) {
            try {
                line.weight = properties.get("weight").getAsDouble();
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private static String stringProperty(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            return null;
        }
        return object.get(key).getAsString();
    }

    private static String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
