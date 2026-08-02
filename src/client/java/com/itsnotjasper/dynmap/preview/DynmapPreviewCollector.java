package com.itsnotjasper.dynmap.preview;

import com.itsnotjasper.dynmap.model.Corner;
import com.itsnotjasper.dynmap.model.DynmapLine;
import com.itsnotjasper.dynmap.model.LineDraft;
import com.itsnotjasper.dynmap.session.CornerSession;
import com.itsnotjasper.dynmap.storage.DraftManager;

import java.util.ArrayList;
import java.util.List;

public final class DynmapPreviewCollector {
    private DynmapPreviewCollector() {
    }

    public static List<LineDraft> collect(String author, CornerSession session, DraftManager drafts) {
        List<LineDraft> preview = new ArrayList<>();

        if (session.cornerCount() >= 2) {
            List<Corner> corners = new ArrayList<>(session.corners());
            DynmapLine line = DynmapLine.fromCorners("session-preview", "roads.a", "Session preview", corners);
            LineDraft sessionDraft = LineDraft.create(author, line);
            sessionDraft.uuid = "session-preview";
            preview.add(sessionDraft);
        }

        for (LineDraft draft : drafts.drafts()) {
            if (!draft.visible || draft.line == null || draft.line.corners == null || draft.line.corners.size() < 2) {
                continue;
            }
            preview.add(draft);
        }

        return preview;
    }
}
