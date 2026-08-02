package com.itsnotjasper.dynmap.ui;

import com.itsnotjasper.dynmap.DynmapServices;
import com.itsnotjasper.dynmap.model.ActiveTool;
import com.itsnotjasper.dynmap.model.Corner;
import com.itsnotjasper.dynmap.session.CornerSession;
import com.itsnotjasper.dynmapdraw.config.DynmapDrawConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DynmapDrawHudOverlay {
    private static final int LINE_HEIGHT = 10;
    private static final int PADDING = 4;
    private static final int PANEL_X = 8;
    private static final int PANEL_Y = 72;

    private DynmapDrawHudOverlay() {
    }

    public static void render(GuiGraphics graphics) {
        if (!DynmapDrawConfig.get().enableDynmapDrawHud || DynmapServices.holder() == null) {
            return;
        }

        CornerSession session = DynmapServices.holder().session();
        if (!session.shouldShowOverlay()) {
            return;
        }

        List<String> lines = buildLines(session);
        if (lines.isEmpty()) {
            return;
        }

        var font = Minecraft.getInstance().font;
        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, font.width(line));
        }

        int boxWidth = maxWidth + PADDING * 2;
        int boxHeight = lines.size() * LINE_HEIGHT + PADDING * 2;
        int x = PANEL_X;
        int y = PANEL_Y;

        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0x90000000);
        int textY = y + PADDING;
        for (String line : lines) {
            graphics.drawString(font, line, x + PADDING, textY, 0xFFFFFF55, true);
            textY += LINE_HEIGHT;
        }
    }

    private static List<String> buildLines(CornerSession session) {
        List<String> lines = new ArrayList<>();
        lines.add("Dynmap: " + (session.activeTool() == ActiveTool.LINE ? "LINE tool" : "idle"));
        if (session.activeTool() == ActiveTool.LINE) {
            var bound = session.boundLineToolItemId();
            if (bound != null) {
                lines.add("Bound item: " + bound);
            }
        }
        lines.add(DynmapDrawConfig.get().dynmapCornerFromCrosshair
                ? Component.translatable("hud.dynmap-draw.dynmap_corner_source.crosshair").getString()
                : Component.translatable("hud.dynmap-draw.dynmap_corner_source.player").getString());
        lines.add("Corners: " + session.cornerCount());
        Corner last = session.lastCorner();
        if (last != null) {
            lines.add(String.format(Locale.ROOT, "Last: %s", last.format()));
        }
        return lines;
    }
}
