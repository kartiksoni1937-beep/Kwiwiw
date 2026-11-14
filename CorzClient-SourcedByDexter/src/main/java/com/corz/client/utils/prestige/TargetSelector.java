package com.corz.client.utils.prestige;

import com.corz.client.config.FriendNameStore;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

public class TargetSelector implements MinecraftAccess {
    public static boolean isPlayer(Entity entity) {
        if (entity instanceof PlayerEntity) {
            return true;
        } else if (entity instanceof LivingEntity && mc.getNetworkHandler() != null) {
            String entityName = entity.getName().getString();

            for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                if (PositionUtil.getProfileName(entry.getProfile()).equals(entityName)) {
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    public static PlayerEntity getClosestEnemy() {
        PlayerEntity closest = null;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player.isAlive()
                && !player.equals(mc.player)
                && !FriendNameStore.isFriend(player.getName().getString())
                && (closest == null || mc.player.squaredDistanceTo(player) < mc.player.squaredDistanceTo(closest))) {
                closest = player;
            }
        }

        return closest;
    }

    public static PlayerEntity getClosestEnemyInFov(float maxAngle) {
        PlayerEntity closest = null;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player.isAlive()
                && !player.equals(mc.player)
                && !FriendNameStore.isFriend(player.getName().getString())
                && (closest == null || mc.player.squaredDistanceTo(player) < mc.player.squaredDistanceTo(closest))
                && !(RotationUtil.getAngleFromPlayerRotation(RotationUtil.getRotationTo(player.getEyePos())) > maxAngle)) {
                closest = player;
            }
        }

        return closest;
    }

    public static Entity getClosestEntity() {
        Entity closest = null;

        for (Entity entity : mc.world.getEntities()) {
            if (entity.isAlive()
                && !entity.equals(mc.player)
                && !FriendNameStore.isFriend(entity.getName().getString())
                && entity.isAttackable()
                && (closest == null || mc.player.squaredDistanceTo(entity) < mc.player.squaredDistanceTo(closest))) {
                closest = entity;
            }
        }

        return closest;
    }

    public static boolean isBlockingWithShield(PlayerEntity player) {
        return (
                player.getMainHandStack().getItem() == Items.SHIELD
                    || player.getOffHandStack().getItem() == Items.SHIELD && player.getMainHandStack().get(DataComponentTypes.FOOD) == null
            )
            && player.isUsingItem()
            && !isFacingAway(player);
    }

    private static boolean isFacingAway(PlayerEntity player) {
        if (mc.player != null && player != null) {
            Vec3d toSelf = PositionUtil.getPlayerPos().subtract(PositionUtil.getEntityPos(player)).normalize();
            float yaw = player.getYaw();
            float pitch = player.getPitch();
            return new Vec3d(
                    -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)),
                    -Math.sin(Math.toRadians(pitch)),
                    Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch))
                )
                .normalize().dotProduct(toSelf) < 0.0;
        } else {
            return false;
        }
    }
}
