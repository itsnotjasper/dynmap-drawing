package com.itsnotjasper.dynmap.input;

import com.itsnotjasper.dynmap.DynmapServices;
import com.itsnotjasper.dynmap.session.CornerSession;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class LineToolItem {
    private LineToolItem() {
    }

    public static boolean isHoldingLineTool(Player player) {
        CornerSession session = session();
        if (session == null || !session.isLineToolActive()) {
            return false;
        }

        Identifier bound = session.boundLineToolItemId();
        if (bound == null) {
            return false;
        }

        ItemStack held = player.getMainHandItem();
        if (bound.equals(BuiltInRegistries.ITEM.getKey(Items.AIR))) {
            return held.isEmpty();
        }

        Item item = BuiltInRegistries.ITEM.getValue(bound);
        if (item == null || item == Items.AIR) {
            return held.isEmpty();
        }
        return held.is(item);
    }

    public static Identifier boundItemId() {
        CornerSession session = session();
        return session == null ? null : session.boundLineToolItemId();
    }

    public static Identifier itemIdInMainHand(Player player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            return BuiltInRegistries.ITEM.getKey(Items.AIR);
        }
        return BuiltInRegistries.ITEM.getKey(held.getItem());
    }

    private static CornerSession session() {
        var holder = DynmapServices.holder();
        return holder == null ? null : holder.session();
    }
}
