package com.itsnotjasper.dynmap.input;

import com.itsnotjasper.dynmap.DynmapServices;
import com.itsnotjasper.dynmap.model.ActiveTool;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public final class LineToolClickHandler {
    private static boolean attackKeyWasDown;
    private static boolean useKeyWasDown;

    private LineToolClickHandler() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.options == null) {
                return;
            }
            if (!client.options.keyAttack.isDown()) {
                attackKeyWasDown = false;
            }
            if (!client.options.keyUse.isDown()) {
                useKeyWasDown = false;
            }
        });
    }

    public static boolean isLineToolActive() {
        var holder = DynmapServices.holder();
        return holder != null && holder.session().activeTool() == ActiveTool.LINE;
    }

    public static boolean shouldHandleInteraction(Minecraft client) {
        if (!isLineToolActive()) {
            return false;
        }
        Player player = client.player;
        return player != null && LineToolItem.isHoldingLineTool(player);
    }

    public static boolean onAttackClick() {
        Minecraft client = Minecraft.getInstance();
        if (!shouldHandleInteraction(client)) {
            return false;
        }

        Player player = client.player;
        if (attackKeyWasDown) {
            return true;
        }

        attackKeyWasDown = true;
        addCorner(player);
        return true;
    }

    public static boolean onUseClick() {
        Minecraft client = Minecraft.getInstance();
        if (!shouldHandleInteraction(client)) {
            return false;
        }

        Player player = client.player;
        if (useKeyWasDown) {
            return true;
        }

        useKeyWasDown = true;
        addCorner(player);
        return true;
    }

    private static void addCorner(Player player) {
        Minecraft client = Minecraft.getInstance();
        CornerPlacement.Coordinates coords = CornerPlacement.resolve(client, player);
        var session = DynmapServices.holder().session();
        String message = session.addCorner(
                coords.x(),
                coords.y(),
                coords.z(),
                player.level().dimension().identifier().toString()
        );
        player.displayClientMessage(Component.literal(message), false);
    }
}
