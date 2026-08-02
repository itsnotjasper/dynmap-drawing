package com.itsnotjasper.dynmap.importer;

import com.itsnotjasper.dynmap.model.Corner;

/**
 * Converts MRT-Map Leaflet CRS.Simple coordinates to Minecraft world coordinates.
 * Mirrors {@code worldcoord([lat, lng])} from MRT-Map coord.js via GeoJSON [lng, lat]:
 * worldX = lng * 64, worldZ = (lat + 0.5) * -64
 */
public final class MrtMapCoordinates {
    private MrtMapCoordinates() {
    }

    /**
     * @param mapLng GeoJSON / Leaflet longitude (first coordinate)
     * @param mapLat GeoJSON / Leaflet latitude (second coordinate)
     * @param worldY   Minecraft Y level (default 64 when not present in .ann files)
     */
    public static Corner toCorner(double mapLng, double mapLat, double worldY) {
        double worldX = mapLng * 64.0;
        double worldZ = (mapLat + 0.5) * -64.0;
        return Corner.of(worldX, worldY, worldZ);
    }
}
