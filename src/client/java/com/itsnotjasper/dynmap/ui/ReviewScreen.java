package com.itsnotjasper.dynmap.ui;

import com.itsnotjasper.dynmap.DynmapServices;
import com.itsnotjasper.dynmap.export.DynmapExporter;
import com.itsnotjasper.dynmap.importer.ImportOptions;
import com.itsnotjasper.dynmap.importer.ImportResult;
import com.itsnotjasper.dynmap.importer.ImportService;
import com.itsnotjasper.dynmap.model.LineDraft;
import com.itsnotjasper.dynmap.preview.DynmapBrowserPreview;
import com.itsnotjasper.dynmap.preview.DynmapPreviewCollector;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ReviewScreen extends Screen {
    private final Screen parent;
    private final List<LineDraft> drafts = new ArrayList<>();
    private int selectedIndex = -1;

    public ReviewScreen(Screen parent) {
        super(Component.literal("Dynmap Line Drafts"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        drafts.clear();
        drafts.addAll(DynmapServices.holder().drafts().drafts());

        int y = 28;
        for (int i = 0; i < drafts.size(); i++) {
            LineDraft draft = drafts.get(i);
            String summary = summarize(draft);
            int index = i;
            addRenderableWidget(Button.builder(Component.literal(summary), button -> selectedIndex = index)
                    .bounds(12, y, width - 24, 20)
                    .build());
            y += 24;
        }

        int buttonY = height - 28;
        addRenderableWidget(Button.builder(Component.literal("Toggle visibility"), button -> toggleSelected())
                .bounds(12, buttonY, 100, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Load corners"), button -> loadSelected())
                .bounds(118, buttonY, 100, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Delete"), button -> deleteSelected())
                .bounds(224, buttonY, 60, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Import"), button -> importDrafts())
                .bounds(290, buttonY, 60, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Export all"), button -> exportAll())
                .bounds(356, buttonY, 85, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Preview"), button -> previewDrafts())
                .bounds(445, buttonY, 70, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(width - 82, buttonY, 70, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        if (drafts.isEmpty()) {
            graphics.drawCenteredString(font, "No saved drafts yet. Use /dl addline or Import.", width / 2, 48, 0xAAAAAA);
        } else if (selectedIndex >= 0 && selectedIndex < drafts.size()) {
            LineDraft draft = drafts.get(selectedIndex);
            graphics.drawString(font, detailLine(draft), 12, height - 52, 0xCCCCCC, false);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private void toggleSelected() {
        LineDraft draft = selectedDraft();
        if (draft == null) {
            return;
        }
        draft.visible = !draft.visible;
        DynmapServices.holder().drafts().updateDraft(draft);
        rebuild();
    }

    private void loadSelected() {
        LineDraft draft = selectedDraft();
        if (draft == null || draft.line == null || draft.line.corners == null) {
            return;
        }
        var player = minecraft.player;
        if (player == null) {
            return;
        }
        DynmapServices.holder().session().loadCorners(
                draft.line.corners,
                player.level().dimension().identifier().toString()
        );
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal("Loaded " + draft.line.corners.size() + " corners into session"), false);
        }
        onClose();
    }

    private void deleteSelected() {
        LineDraft draft = selectedDraft();
        if (draft == null) {
            return;
        }
        DynmapServices.holder().drafts().deleteDraft(draft.uuid);
        selectedIndex = -1;
        rebuild();
    }

    private void previewDrafts() {
        var player = minecraft.player;
        if (player == null) {
            return;
        }
        List<LineDraft> toPreview;
        LineDraft selected = selectedDraft();
        if (selected != null && selected.line != null && selected.line.corners != null && selected.line.corners.size() >= 2) {
            toPreview = List.of(selected);
        } else {
            toPreview = DynmapPreviewCollector.collect(
                    player.getGameProfile().name(),
                    DynmapServices.holder().session(),
                    DynmapServices.holder().drafts()
            );
        }
        DynmapBrowserPreview.launch(toPreview);
    }

    private void importDrafts() {
        var player = minecraft.player;
        if (player == null) {
            return;
        }
        minecraft.setScreen(new ImportPathScreen(this, player.getGameProfile().name()));
    }

    void finishImport(Path path, String author) {
        if (path == null) {
            return;
        }
        var player = minecraft.player;
        if (player == null) {
            return;
        }

        Set<String> existingLineIds = new HashSet<>();
        for (LineDraft draft : DynmapServices.holder().drafts().drafts()) {
            if (draft.line != null && draft.line.lineId != null) {
                existingLineIds.add(draft.line.lineId);
            }
        }

        ImportOptions options = ImportOptions.fromConfig(author);
        ImportResult result = ImportService.importFile(path, options, existingLineIds);
        if (result.hasError()) {
            player.displayClientMessage(Component.literal("Import failed: " + result.errorMessage()), false);
            return;
        }
        if (result.importedCount() == 0) {
            player.displayClientMessage(Component.literal(
                    "No lines imported from " + path.getFileName() + " (skipped " + result.skippedCount() + ")"
            ), false);
            return;
        }

        var draftManager = DynmapServices.holder().drafts();
        for (LineDraft draft : result.drafts()) {
            draftManager.addDraft(draft);
        }
        player.displayClientMessage(Component.literal(
                "Imported " + result.importedCount() + " line(s) from " + path.getFileName()
                        + " (skipped " + result.skippedCount() + ")"
        ), false);
        rebuild();
    }

    private void exportAll() {
        if (drafts.isEmpty()) {
            return;
        }
        var player = minecraft.player;
        if (player == null) {
            return;
        }
        String json = DynmapExporter.toMarkerJson(drafts);
        try {
            var exportsDir = DynmapServices.holder().drafts().exportsDir();
            Files.createDirectories(exportsDir);
            var path = exportsDir.resolve("pending_marker_" + Instant.now().getEpochSecond() + ".json");
            Files.writeString(path, json, StandardCharsets.UTF_8);
            DynmapServices.holder().drafts().hideDrafts(drafts);
            player.displayClientMessage(Component.literal("Exported to " + path + " (world previews hidden)"), false);
            rebuild();
        } catch (IOException e) {
            player.displayClientMessage(Component.literal("Export failed: " + e.getMessage()), false);
        }
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private LineDraft selectedDraft() {
        if (selectedIndex < 0 || selectedIndex >= drafts.size()) {
            return null;
        }
        return drafts.get(selectedIndex);
    }

    private static String summarize(LineDraft draft) {
        if (draft.line == null) {
            return draft.uuid + " (empty)";
        }
        int corners = draft.line.corners == null ? 0 : draft.line.corners.size();
        String vis = draft.visible ? "shown" : "hidden";
        return draft.line.lineId + " · " + draft.line.label + " · " + corners + " pts · " + vis;
    }

    private static String detailLine(LineDraft draft) {
        if (draft.line == null) {
            return draft.uuid;
        }
        return draft.line.targetSet + " · " + draft.line.lineId + " · " + draft.line.label;
    }
}
