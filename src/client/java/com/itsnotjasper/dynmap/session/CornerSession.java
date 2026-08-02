package com.itsnotjasper.dynmap.session;

import com.itsnotjasper.dynmap.model.ActiveTool;
import com.itsnotjasper.dynmap.model.Corner;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CornerSession {
    private ActiveTool activeTool = ActiveTool.NONE;
    private Identifier boundLineToolItemId;
    private String worldId;
    private final List<Corner> corners = new ArrayList<>();

    public ActiveTool activeTool() {
        return activeTool;
    }

    public void setTool(ActiveTool tool, Identifier boundItemId) {
        this.activeTool = tool;
        this.boundLineToolItemId = tool == ActiveTool.LINE ? boundItemId : null;
    }

    public Identifier boundLineToolItemId() {
        return boundLineToolItemId;
    }

    public List<Corner> corners() {
        return Collections.unmodifiableList(corners);
    }

    public boolean hasCorners() {
        return !corners.isEmpty();
    }

    public int cornerCount() {
        return corners.size();
    }

    public Corner lastCorner() {
        return corners.isEmpty() ? null : corners.get(corners.size() - 1);
    }

    public String addCorner(double x, double y, double z, String dimensionId) {
        if (worldId == null) {
            worldId = dimensionId;
        } else if (!worldId.equals(dimensionId)) {
            corners.clear();
            worldId = dimensionId;
        }

        Corner corner = Corner.of(x, y, z);
        corners.add(corner);
        return "Added corner #" + corners.size() + " at " + corner.format();
    }

    public void loadCorners(List<Corner> loaded, String dimensionId) {
        corners.clear();
        corners.addAll(loaded);
        worldId = dimensionId;
    }

    public void clearCorners() {
        corners.clear();
        worldId = null;
    }

    public boolean isLineToolActive() {
        return activeTool == ActiveTool.LINE;
    }

    public boolean shouldShowOverlay() {
        return activeTool == ActiveTool.LINE || !corners.isEmpty();
    }
}
