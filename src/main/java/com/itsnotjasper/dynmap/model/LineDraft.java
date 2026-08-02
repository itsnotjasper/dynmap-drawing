package com.itsnotjasper.dynmap.model;

import java.time.Instant;
import java.util.UUID;

public final class LineDraft {
    public String uuid;
    public String author;
    public String createdAt;
    public String updatedAt;
    public boolean visible = true;
    public DynmapLine line;

    public LineDraft() {
    }

    public static LineDraft create(String author, DynmapLine line) {
        LineDraft draft = new LineDraft();
        draft.uuid = UUID.randomUUID().toString();
        draft.author = author;
        draft.line = line;
        String now = Instant.now().toString();
        draft.createdAt = now;
        draft.updatedAt = now;
        return draft;
    }

    public void touch() {
        updatedAt = Instant.now().toString();
    }
}
