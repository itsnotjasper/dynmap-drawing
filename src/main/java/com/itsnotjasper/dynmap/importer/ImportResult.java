package com.itsnotjasper.dynmap.importer;

import com.itsnotjasper.dynmap.model.LineDraft;

import java.util.ArrayList;
import java.util.List;

public final class ImportResult {
    private final List<LineDraft> drafts = new ArrayList<>();
    private int skippedWrongShape;
    private int skippedHidden;
    private int skippedTooFewPoints;
    private String errorMessage;

    public List<LineDraft> drafts() {
        return List.copyOf(drafts);
    }

    public void addDraft(LineDraft draft) {
        drafts.add(draft);
    }

    public int importedCount() {
        return drafts.size();
    }

    public int skippedCount() {
        return skippedWrongShape + skippedHidden + skippedTooFewPoints;
    }

    public int skippedWrongShape() {
        return skippedWrongShape;
    }

    public void incrementSkippedWrongShape() {
        skippedWrongShape++;
    }

    public int skippedHidden() {
        return skippedHidden;
    }

    public void incrementSkippedHidden() {
        skippedHidden++;
    }

    public int skippedTooFewPoints() {
        return skippedTooFewPoints;
    }

    public void incrementSkippedTooFewPoints() {
        skippedTooFewPoints++;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public boolean hasError() {
        return errorMessage != null;
    }
}
