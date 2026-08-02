package com.itsnotjasper.dynmap.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.itsnotjasper.dynmapdraw.config.DynmapExportPaths;
import com.itsnotjasper.dynmap.model.LineDraft;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class DraftManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DRAFTS_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("dynmap-draw")
            .resolve("dynmap-drafts.json");

    private final List<LineDraft> drafts = new ArrayList<>();

    public void load() {
        drafts.clear();
        if (!Files.exists(DRAFTS_PATH)) {
            return;
        }
        try {
            String json = Files.readString(DRAFTS_PATH, StandardCharsets.UTF_8);
            DraftStore store = GSON.fromJson(json, DraftStore.class);
            if (store != null && store.drafts != null) {
                drafts.addAll(store.drafts);
            }
        } catch (IOException | JsonSyntaxException ignored) {
        }
    }

    public void save() {
        try {
            Files.createDirectories(DRAFTS_PATH.getParent());
            DraftStore store = new DraftStore();
            store.drafts = new ArrayList<>(drafts);
            Files.writeString(DRAFTS_PATH, GSON.toJson(store), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public List<LineDraft> drafts() {
        return List.copyOf(drafts);
    }

    public Optional<LineDraft> findById(String id) {
        return drafts.stream().filter(d -> d.uuid.equals(id)).findFirst();
    }

    public Optional<LineDraft> findByLineId(String lineId) {
        return drafts.stream()
                .filter(d -> d.line != null && lineId.equals(d.line.lineId))
                .findFirst();
    }

    public LineDraft addDraft(LineDraft draft) {
        drafts.add(draft);
        save();
        return draft;
    }

    public void deleteDraft(String uuid) {
        drafts.removeIf(d -> d.uuid.equals(uuid));
        save();
    }

    public void updateDraft(LineDraft draft) {
        draft.touch();
        save();
    }

    public void hideDrafts(Iterable<LineDraft> toHide) {
        for (LineDraft draft : toHide) {
            draft.visible = false;
            draft.touch();
        }
        save();
    }

    public Path exportsDir() {
        return DynmapExportPaths.resolveConfiguredExportsDir();
    }

    private static final class DraftStore {
        private List<LineDraft> drafts = new ArrayList<>();
    }
}
