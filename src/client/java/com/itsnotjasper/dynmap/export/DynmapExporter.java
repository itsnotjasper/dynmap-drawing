package com.itsnotjasper.dynmap.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.itsnotjasper.dynmap.model.Corner;
import com.itsnotjasper.dynmap.model.DynmapLine;
import com.itsnotjasper.dynmap.model.LineDraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DynmapExporter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private DynmapExporter() {
    }

    /**
     * Dynmap marker file shape: {@code { "sets": { "<setId>": { "lines": { "<lineId>": { ... } } } } } }}
     * Compatible with MRT {@code tiles/_markers_/marker_<world>.json} line entries.
     */
    public static String toMarkerJson(List<LineDraft> drafts) {
        Map<String, Map<String, Object>> sets = new LinkedHashMap<>();

        for (LineDraft draft : drafts) {
            if (draft.line == null || draft.line.lineId == null || draft.line.targetSet == null) {
                continue;
            }

            Map<String, Object> setNode = sets.computeIfAbsent(draft.line.targetSet, DynmapExporter::newMarkerSet);
            @SuppressWarnings("unchecked")
            Map<String, Object> lines = (Map<String, Object>) setNode.get("lines");
            lines.put(draft.line.lineId, toLineObject(draft.line));
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("sets", sets);
        return GSON.toJson(root);
    }

    private static Map<String, Object> newMarkerSet(String ignoredSetId) {
        Map<String, Object> setNode = new LinkedHashMap<>();
        setNode.put("hide", false);
        setNode.put("circles", new LinkedHashMap<>());
        setNode.put("areas", new LinkedHashMap<>());
        setNode.put("markers", new LinkedHashMap<>());
        setNode.put("lines", new LinkedHashMap<String, Object>());
        return setNode;
    }

    private static Map<String, Object> toLineObject(DynmapLine line) {
        List<Double> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        List<Double> zs = new ArrayList<>();
        for (Corner corner : line.corners) {
            xs.add(round2(corner.x()));
            ys.add(round2(corner.y()));
            zs.add(round2(corner.z()));
        }

        Map<String, Object> node = new LinkedHashMap<>();
        node.put("color", line.color);
        node.put("markup", line.markup);
        node.put("x", xs);
        node.put("y", ys);
        node.put("weight", line.weight);
        node.put("z", zs);
        node.put("label", line.label);
        node.put("opacity", line.opacity);
        return node;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
