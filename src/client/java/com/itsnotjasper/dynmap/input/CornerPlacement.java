package com.itsnotjasper.dynmap.input;

import com.itsnotjasper.dynmapdraw.config.DynmapDrawConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class CornerPlacement {
    public record Coordinates(double x, double y, double z) {
    }

    private CornerPlacement() {
    }

    public static Coordinates resolve(Minecraft client, Player player) {
        if (DynmapDrawConfig.get().dynmapCornerFromCrosshair) {
            Coordinates crosshair = resolveCrosshair(client);
            if (crosshair != null) {
                return crosshair;
            }
        }
        return playerPosition(player);
    }

    private static Coordinates resolveCrosshair(Minecraft client) {
        HitResult hit = client.hitResult;
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            return null;
        }
        Vec3 location = hit.getLocation();
        return new Coordinates(location.x, location.y, location.z);
    }

    private static Coordinates playerPosition(Player player) {
        return new Coordinates(player.getX(), player.getY(), player.getZ());
    }
}
