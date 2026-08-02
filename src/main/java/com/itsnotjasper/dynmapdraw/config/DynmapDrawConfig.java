package com.itsnotjasper.dynmapdraw.config;

public final class DynmapDrawConfig {
    public boolean enableDynmapDrawHud = true;
    public boolean dynmapCornerFromCrosshair = false;
    public String dynmapExportPath = "";
    public String dynmapPreviewUrl = "https://dynmap.minecartrapidtransit.net/main/";

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
    }
}
