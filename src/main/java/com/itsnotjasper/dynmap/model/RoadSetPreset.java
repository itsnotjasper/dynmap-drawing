package com.itsnotjasper.dynmap.model;

import java.util.Map;

public enum RoadSetPreset {
    ROADS_A("roads.a", "#0EA2A1"),
    ROADS_B("roads.b", "#00DC6E");

    public static final double DEFAULT_WEIGHT = 3.0;
    public static final double DEFAULT_OPACITY = 0.80;

    private static final Map<String, RoadSetPreset> BY_ID = Map.of(
            ROADS_A.setId, ROADS_A,
            ROADS_B.setId, ROADS_B
    );

    private final String setId;
    private final String color;

    RoadSetPreset(String setId, String color) {
        this.setId = setId;
        this.color = color;
    }

    public String setId() {
        return setId;
    }

    public String color() {
        return color;
    }

    public static RoadSetPreset fromSetId(String setId) {
        return BY_ID.get(setId);
    }

    public static boolean isSupported(String setId) {
        return BY_ID.containsKey(setId);
    }
}
