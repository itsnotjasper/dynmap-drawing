package com.itsnotjasper.dynmap;

import com.itsnotjasper.dynmap.model.Corner;
import com.itsnotjasper.dynmap.model.LineDraft;

import java.util.List;

public final class DynmapServices {
    private static CornerSessionHolder holder;

    private DynmapServices() {
    }

    public static void init(CornerSessionHolder sessionHolder) {
        holder = sessionHolder;
    }

    public static CornerSessionHolder holder() {
        return holder;
    }

    public record CornerSessionHolder(
            com.itsnotjasper.dynmap.session.CornerSession session,
            com.itsnotjasper.dynmap.storage.DraftManager drafts
    ) {
        public List<Corner> visibleDraftCorners() {
            return drafts.drafts().stream()
                    .filter(d -> d.visible && d.line != null && d.line.corners != null)
                    .flatMap(d -> d.line.corners.stream())
                    .toList();
        }

        public List<LineDraft> visibleDrafts() {
            return drafts.drafts().stream().filter(d -> d.visible).toList();
        }
    }
}
