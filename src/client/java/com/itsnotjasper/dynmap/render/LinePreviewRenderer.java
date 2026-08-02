package com.itsnotjasper.dynmap.render;

import com.itsnotjasper.dynmap.DynmapServices;
import com.itsnotjasper.dynmap.model.Corner;
import com.itsnotjasper.dynmap.model.LineDraft;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class LinePreviewRenderer {
    private static final int ACTIVE_COLOR = 0xFF00FF00;
    private static final int DRAFT_COLOR = 0xAA00AAFF;
    private static final float LINE_WIDTH = 2.0f;

    private LinePreviewRenderer() {
    }

    public static void register() {
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(context -> render());
    }

    private static void render() {
        if (DynmapServices.holder() == null) {
            return;
        }

        var session = DynmapServices.holder().session();
        List<Corner> active = session.corners();

        if (active.size() >= 2) {
            drawPolyline(active, ACTIVE_COLOR);
        }

        for (LineDraft draft : DynmapServices.holder().drafts().drafts()) {
            if (!draft.visible || draft.line == null || draft.line.corners == null || draft.line.corners.size() < 2) {
                continue;
            }
            int color = parseColor(draft.line.color, DRAFT_COLOR);
            drawPolyline(draft.line.corners, color);
        }
    }

    private static void drawPolyline(List<Corner> corners, int color) {
        for (int i = 0; i < corners.size() - 1; i++) {
            Corner start = corners.get(i);
            Corner end = corners.get(i + 1);
            Gizmos.line(toVec3(start), toVec3(end), color, LINE_WIDTH);
        }
    }

    private static Vec3 toVec3(Corner corner) {
        return new Vec3(corner.x(), corner.y(), corner.z());
    }

    private static int parseColor(String hex, int fallback) {
        if (hex == null || !hex.startsWith("#") || hex.length() != 7) {
            return fallback;
        }
        try {
            int rgb = Integer.parseInt(hex.substring(1), 16);
            return 0xFF000000 | rgb;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
