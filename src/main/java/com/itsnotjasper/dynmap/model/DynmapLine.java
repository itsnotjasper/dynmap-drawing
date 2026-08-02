package com.itsnotjasper.dynmap.model;

import java.util.ArrayList;
import java.util.List;

public final class DynmapLine {
    public String lineId;
    public String label;
    public String targetSet;
    public String color;
    public double weight = RoadSetPreset.DEFAULT_WEIGHT;
    public double opacity = RoadSetPreset.DEFAULT_OPACITY;
    public boolean markup;
    public List<Corner> corners = new ArrayList<>();

    public DynmapLine() {
    }

    public static DynmapLine fromCorners(String lineId, String targetSet, String label, List<Corner> corners) {
        DynmapLine line = new DynmapLine();
        line.lineId = lineId;
        line.label = label;
        line.targetSet = targetSet;
        line.corners = new ArrayList<>(corners);
        RoadSetPreset preset = RoadSetPreset.fromSetId(targetSet);
        if (preset != null) {
            line.color = preset.color();
        }
        return line;
    }
}
