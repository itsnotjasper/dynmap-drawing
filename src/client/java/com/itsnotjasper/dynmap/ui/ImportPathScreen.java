package com.itsnotjasper.dynmap.ui;

import com.itsnotjasper.dynmap.importer.ImportPaths;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ImportPathScreen extends Screen {
    private final ReviewScreen parent;
    private final String author;
    private EditBox pathField;

    public ImportPathScreen(ReviewScreen parent, String author) {
        super(Component.literal("Import annotation file"));
        this.parent = parent;
        this.author = author;
    }

    @Override
    protected void init() {
        Path importsDir = ImportPaths.importsDir();
        pathField = new EditBox(font, 12, 36, width - 24, 20, Component.literal("Path"));
        pathField.setMaxLength(2048);
        pathField.setValue(importsDir.toString());
        addRenderableWidget(pathField);

        int y = 64;
        List<Path> files = ImportPaths.listImportFiles();
        if (files.isEmpty()) {
            y = 72;
        } else {
            for (Path file : files) {
                if (y > height - 72) {
                    break;
                }
                String label = file.getFileName().toString();
                addRenderableWidget(Button.builder(Component.literal(label), button -> pathField.setValue(file.toString()))
                        .bounds(12, y, width - 24, 20)
                        .build());
                y += 24;
            }
        }

        addRenderableWidget(Button.builder(Component.literal("Import"), button -> submitImport())
                .bounds(12, height - 28, 80, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(width - 92, height - 28, 80, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        graphics.drawString(
                font,
                "Enter a full path, or place .ann files in:",
                12,
                52,
                0xAAAAAA,
                false
        );
        graphics.drawString(font, ImportPaths.importsDir().toString(), 12, yHint(), 0x888888, false);
    }

    private int yHint() {
        return Math.min(64 + ImportPaths.listImportFiles().size() * 24 + 8, height - 52);
    }

    private void submitImport() {
        String rawPath = pathField.getValue().trim();
        if (rawPath.isEmpty()) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.literal("Enter a file path to import"), false);
            }
            return;
        }

        Path path = Path.of(rawPath);
        if (!Files.isRegularFile(path)) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.literal("File not found: " + path), false);
            }
            return;
        }
        if (!ImportPaths.isSupportedImportFile(path)) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.literal("Supported types: .ann, .json"), false);
            }
            return;
        }

        parent.finishImport(path, author);
        minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
