package com.itsnotjasper.dynmap.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.itsnotjasper.dynmap.input.CornerPlacement;
import com.itsnotjasper.dynmap.input.LineToolItem;
import com.itsnotjasper.dynmap.DynmapServices;
import com.itsnotjasper.dynmap.export.DynmapExporter;
import com.itsnotjasper.dynmap.model.ActiveTool;
import com.itsnotjasper.dynmap.model.Corner;
import com.itsnotjasper.dynmap.model.DynmapLine;
import com.itsnotjasper.dynmap.model.LineDraft;
import com.itsnotjasper.dynmap.model.RoadSetPreset;
import com.itsnotjasper.dynmap.ui.DynmapHudActions;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class DynmapClientCommands {
    private DynmapClientCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(DynmapClientCommands::registerCommands);
    }

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext registryAccess) {
        LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommandManager.literal("dl")
                .then(ClientCommandManager.literal("tool")
                        .then(ClientCommandManager.literal("line").executes(ctx -> setTool(ctx, ActiveTool.LINE)))
                        .then(ClientCommandManager.literal("none").executes(ctx -> setTool(ctx, ActiveTool.NONE))))
                .then(ClientCommandManager.literal("addcorner")
                        .executes(DynmapClientCommands::addCornerAtPlayer)
                        .then(ClientCommandManager.argument("x", DoubleArgumentType.doubleArg())
                                .then(ClientCommandManager.argument("y", DoubleArgumentType.doubleArg())
                                        .then(ClientCommandManager.argument("z", DoubleArgumentType.doubleArg())
                                                .executes(DynmapClientCommands::addCornerExplicit)))))
                .then(ClientCommandManager.literal("clearcorners").executes(DynmapClientCommands::clearCorners))
                .then(ClientCommandManager.literal("addline")
                        .then(ClientCommandManager.argument("id", StringArgumentType.string())
                                .then(ClientCommandManager.argument("set", StringArgumentType.string())
                                        .suggests(DynmapClientCommands::suggestSets)
                                        .then(ClientCommandManager.argument("label", StringArgumentType.greedyString())
                                                .executes(DynmapClientCommands::addLine)))))
                .then(ClientCommandManager.literal("review").executes(DynmapClientCommands::openReview))
                .then(ClientCommandManager.literal("export")
                        .executes(ctx -> exportDrafts(ctx, null))
                        .then(ClientCommandManager.argument("draftId", StringArgumentType.string())
                                .suggests((ctx, builder) -> suggestDraftIds(ctx, builder))
                                .executes(ctx -> exportDrafts(ctx, StringArgumentType.getString(ctx, "draftId")))));

        LiteralCommandNode<FabricClientCommandSource> dlNode = dispatcher.register(root);
        dispatcher.register(ClientCommandManager.literal("dynmapline").redirect(dlNode));
    }

    private static int setTool(CommandContext<FabricClientCommandSource> ctx, ActiveTool tool) {
        var player = ctx.getSource().getPlayer();
        var session = DynmapServices.holder().session();
        if (tool == ActiveTool.LINE) {
            Identifier boundItem = LineToolItem.itemIdInMainHand(player);
            session.setTool(ActiveTool.LINE, boundItem);
            feedback(ctx, "Dynmap line tool bound to "
                    + boundItem
                    + " — left/right click while holding it to add corners");
        } else {
            session.setTool(ActiveTool.NONE, null);
            feedback(ctx, "Dynmap line tool cleared");
        }
        return 1;
    }

    private static int addCornerAtPlayer(CommandContext<FabricClientCommandSource> ctx) {
        var player = ctx.getSource().getPlayer();
        CornerPlacement.Coordinates coords = CornerPlacement.resolve(Minecraft.getInstance(), player);
        String message = DynmapServices.holder().session().addCorner(
                coords.x(),
                coords.y(),
                coords.z(),
                player.level().dimension().identifier().toString()
        );
        feedback(ctx, message);
        return 1;
    }

    private static int addCornerExplicit(CommandContext<FabricClientCommandSource> ctx) {
        var player = ctx.getSource().getPlayer();
        double x = DoubleArgumentType.getDouble(ctx, "x");
        double y = DoubleArgumentType.getDouble(ctx, "y");
        double z = DoubleArgumentType.getDouble(ctx, "z");
        String message = DynmapServices.holder().session().addCorner(
                x,
                y,
                z,
                player.level().dimension().identifier().toString()
        );
        feedback(ctx, message);
        return 1;
    }

    private static int clearCorners(CommandContext<FabricClientCommandSource> ctx) {
        DynmapServices.holder().session().clearCorners();
        feedback(ctx, "Cleared corner list");
        return 1;
    }

    private static int addLine(CommandContext<FabricClientCommandSource> ctx) {
        var session = DynmapServices.holder().session();
        if (session.cornerCount() < 2) {
            feedback(ctx, "At least two corners must be added with /dl addcorner before a line can be added");
            return 0;
        }

        String lineId = StringArgumentType.getString(ctx, "id");
        String setId = StringArgumentType.getString(ctx, "set");
        String label = StringArgumentType.getString(ctx, "label");

        if (!RoadSetPreset.isSupported(setId)) {
            feedback(ctx, "Invalid set - use roads.a or roads.b");
            return 0;
        }

        var player = ctx.getSource().getPlayer();
        List<Corner> corners = new ArrayList<>(session.corners());
        DynmapLine line = DynmapLine.fromCorners(lineId, setId, label, corners);
        LineDraft draft = LineDraft.create(player.getGameProfile().name(), line);
        DynmapServices.holder().drafts().addDraft(draft);
        session.clearCorners();

        feedback(ctx, "Added line id:'" + lineId + "' (" + label + ") to set '" + setId + "' (saved locally as draft " + draft.uuid + ")");
        return 1;
    }

    private static int openReview(CommandContext<FabricClientCommandSource> ctx) {
        DynmapHudActions.openReviewScreen();
        feedback(ctx, "Opening draft review...");
        return 1;
    }

    private static int exportDrafts(CommandContext<FabricClientCommandSource> ctx, String draftId) {
        var drafts = DynmapServices.holder().drafts();
        List<LineDraft> toExport = new ArrayList<>();
        if (draftId == null) {
            toExport.addAll(drafts.drafts());
        } else {
            var draft = drafts.findById(draftId)
                    .or(() -> drafts.findByLineId(draftId));
            if (draft.isEmpty()) {
                feedback(ctx, "Draft not found: " + draftId);
                return 0;
            }
            toExport.add(draft.get());
        }

        if (toExport.isEmpty()) {
            feedback(ctx, "No drafts to export");
            return 0;
        }

        String json = DynmapExporter.toMarkerJson(toExport);
        try {
            Files.createDirectories(drafts.exportsDir());
            var path = drafts.exportsDir().resolve("pending_marker_" + Instant.now().getEpochSecond() + ".json");
            Files.writeString(path, json, StandardCharsets.UTF_8);
            drafts.hideDrafts(toExport);
            feedback(ctx, "Exported " + toExport.size() + " line(s) to " + path + " (world previews hidden)");
        } catch (IOException e) {
            feedback(ctx, "Export failed: " + e.getMessage());
            return 0;
        }
        return 1;
    }

    private static void feedback(CommandContext<FabricClientCommandSource> ctx, String message) {
        ctx.getSource().sendFeedback(Component.literal(message));
    }

    private static java.util.concurrent.CompletableFuture<Suggestions> suggestSets(
            CommandContext<FabricClientCommandSource> ctx,
            SuggestionsBuilder builder
    ) {
        builder.suggest("roads.a");
        builder.suggest("roads.b");
        return builder.buildFuture();
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestDraftIds(
            CommandContext<FabricClientCommandSource> ctx,
            SuggestionsBuilder builder
    ) {
        for (LineDraft draft : DynmapServices.holder().drafts().drafts()) {
            builder.suggest(draft.uuid);
            if (draft.line != null && draft.line.lineId != null) {
                builder.suggest(draft.line.lineId);
            }
        }
        return builder.buildFuture();
    }
}
