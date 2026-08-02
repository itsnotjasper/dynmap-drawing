package com.itsnotjasper.dynmap.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class DynmapHudActions {
    private DynmapHudActions() {
    }

    public static void openReviewScreen() {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> client.execute(() -> client.setScreen(new ReviewScreen(null))));
    }

    public static void notify(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.displayClientMessage(message, false);
        }
    }
}
