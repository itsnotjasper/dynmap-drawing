package com.itsnotjasper.client;

import com.itsnotjasper.DynmapDraw;
import com.itsnotjasper.dynmapdraw.client.config.DynmapDrawConfigManager;
import com.itsnotjasper.dynmap.DynmapServices;
import com.itsnotjasper.dynmap.command.DynmapClientCommands;
import com.itsnotjasper.dynmap.input.LineToolClickHandler;
import com.itsnotjasper.dynmap.render.LinePreviewRenderer;
import com.itsnotjasper.dynmap.session.CornerSession;
import com.itsnotjasper.dynmap.storage.DraftManager;
import com.itsnotjasper.dynmap.ui.DynmapDrawHudOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

public final class DynmapDrawClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        DynmapDrawConfigManager.load();

        DraftManager draftManager = new DraftManager();
        draftManager.load();
        CornerSession cornerSession = new CornerSession();
        DynmapServices.init(new DynmapServices.CornerSessionHolder(cornerSession, draftManager));
        DynmapClientCommands.register();
        LineToolClickHandler.register();
        LinePreviewRenderer.register();

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(DynmapDraw.MOD_ID, "dynmap_draw_hud"),
                (graphics, deltaTracker) -> DynmapDrawHudOverlay.render(graphics)
        );
    }
}
