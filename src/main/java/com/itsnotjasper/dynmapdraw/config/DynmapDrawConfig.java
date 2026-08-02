package com.itsnotjasper.dynmapdraw.config;

import com.itsnotjasper.dynmap.model.RoadSetPreset;

public final class DynmapDrawConfig {
    public boolean enableDynmapDrawHud = true;
    public boolean dynmapCornerFromCrosshair = false;
    public String dynmapExportPath = "";
    public String dynmapPreviewUrl = "https://dynmap.minecartrapidtransit.net/main/";
    public double importDefaultY = 64.0;
    public String importDefaultSet = "roads.a";

    private static DynmapDrawConfig instance = new DynmapDrawConfig();

    public static DynmapDrawConfig get() {
        return instance;
    }

    public static void set(DynmapDrawConfig config) {
        instance = config;
    }

    public void clamp() {
        if (dynmapExportPath == null) {
            dynmapExportPath = "";
        } else {
            dynmapExportPath = dynmapExportPath.trim();
        }
        if (dynmapPreviewUrl == null || dynmapPreviewUrl.isBlank()) {
            dynmapPreviewUrl = "https://dynmap.minecartrapidtransit.net/main/";
        } else {
            dynmapPreviewUrl = dynmapPreviewUrl.trim();
        }
        if (importDefaultSet == null || importDefaultSet.isBlank()) {
            importDefaultSet = "roads.a";
        } else {
            importDefaultSet = importDefaultSet.trim();
        }
        if (!RoadSetPreset.isSupported(importDefaultSet)) {
            importDefaultSet = "roads.a";
        }
        if (Double.isNaN(importDefaultY) || Double.isInfinite(importDefaultY)) {
            importDefaultY = 64.0;
        }
    }
}
