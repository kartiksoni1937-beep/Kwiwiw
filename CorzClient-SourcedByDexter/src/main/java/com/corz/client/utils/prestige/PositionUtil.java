package com.corz.client.utils.prestige;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public class PositionUtil implements MinecraftAccess {
    public static Vec3d getEntityPos(Entity entity) {
        return entity.getEntityPos();
    }

    public static Vec3d getPlayerPos() {
        return getEntityPos(mc.player);
    }

    public static String getProfileName(GameProfile profile) {
        return profile.name();
    }

    public static Vec3d getLastPos(Entity entity) {
        return new Vec3d(entity.lastX, entity.lastY, entity.lastZ);
    }

    public static boolean isPlayerGliding() {
        return isGliding(mc.player);
    }

    public static boolean isGliding(PlayerEntity player) {
        return player.isGliding();
    }

    public static double getHeightAboveGround(PlayerEntity player) {
        Vec3d pos = getEntityPos(player);
        Vec3d below = pos.add(0.0, -256.0, 0.0);
        BlockHitResult hitResult = mc.world.raycast(new RaycastContext(pos, below, ShapeType.OUTLINE, FluidHandling.NONE, player));
        return hitResult.getType() == Type.BLOCK ? player.getY() - hitResult.getPos().y : 256.0;
    }
}
