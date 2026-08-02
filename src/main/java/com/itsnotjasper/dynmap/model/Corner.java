package com.itsnotjasper.dynmap.model;

import java.util.Locale;

public record Corner(double x, double y, double z) {
    public static Corner fromPlayer(double x, double y, double z) {
        return of(x, y, z);
    }

    public static Corner of(double x, double y, double z) {
        return new Corner(roundToHalf(x), roundToHalf(y), roundToHalf(z));
    }

    public String format() {
        return "{" + formatCoordinate(x) + "," + formatCoordinate(y) + "," + formatCoordinate(z) + "}";
    }

    public static double roundToHalf(double value) {
        return Math.round(value * 2.0) / 2.0;
    }

    private static String formatCoordinate(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
