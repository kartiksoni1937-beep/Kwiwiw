package com.corz.client.utils.prestige;

import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public class RotationUtil implements MinecraftAccess {
    public static boolean smoothRotate(Rotation target, float smoothing, float offset, float duration) {
        float currentYaw = mc.player.getYaw();
        float angle = target.getYaw() + offset;
        float amount = PrestigeSupport.frameDeltaTimer.getAnimationDelta() * (1.1F - smoothing) * 7.0F / duration;
        amount *= RandomUtil.randomFloat(0.5F, 1.0F);
        float newYaw = interpolate(currentYaw, angle, amount);
        angle = mc.player.getPitch();
        amount = target.getPitch() + offset / 2.0F;
        float pitchStep = PrestigeSupport.frameDeltaTimer.getAnimationDelta() * (1.1F - smoothing) * 7.0F / duration;
        pitchStep *= RandomUtil.randomFloat(0.5F, 1.0F);
        applyRotation(new Rotation(newYaw, interpolate(angle, amount, pitchStep)));
        return mc.player.getYaw() - 5.0F < target.getYaw()
            && mc.player.getYaw() + 5.0F > target.getYaw()
            && mc.player.getPitch() - 5.0F < target.getPitch()
            && mc.player.getPitch() + 5.0F > target.getPitch();
    }

    public static boolean smoothRotatePitch(Rotation target, float smoothing, float offset, float duration) {
        float currentPitch = mc.player.getPitch();
        float targetPitch = target.getPitch() + offset / 2.0F;
        float step = PrestigeSupport.frameDeltaTimer.getAnimationDelta() * (1.1F - smoothing) * 7.0F / duration;
        step *= RandomUtil.randomFloat(0.5F, 1.0F);
        float newPitch = interpolate(currentPitch, targetPitch, step);
        applyRotation(new Rotation(mc.player.getYaw(), newPitch));
        return mc.player.getPitch() - 1.0F < target.getPitch() && mc.player.getPitch() + 1.0F > target.getPitch();
    }

    public static boolean isWithinAngle(Rotation from, Rotation to, float maxAngle) {
        float yawDiff = Math.abs(Math.abs(to.getYaw()) - Math.abs(from.getYaw()));
        float pitchDiff = Math.abs(to.getPitch() - Math.abs(from.getPitch()));
        return ((float)Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff)) <= maxAngle;
    }

    public static float getAngleFromPlayerRotation(Rotation rotation) {
        float yawDiff = Math.abs(Math.abs(rotation.getYaw()) - Math.abs(mc.player.getYaw()));
        float pitchDiff = Math.abs(rotation.getPitch() - Math.abs(mc.player.getPitch()));
        return (float)Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
    }

    public static HitResult raycastRotation(Rotation rotation) {
        return mc.world
            .raycast(
                new RaycastContext(
                    mc.player.getEyePos(),
                    getDirectionVector(rotation).multiply(6.0).add(mc.player.getEyePos()),
                    ShapeType.OUTLINE,
                    FluidHandling.NONE,
                    mc.player
                )
            );
    }

    public static HitResult raycastRotationWithRange(Rotation rotation, float range) {
        return mc.world
            .raycast(
                new RaycastContext(
                    mc.player.getEyePos(),
                    getDirectionVector(rotation).multiply(range).add(mc.player.getEyePos()),
                    ShapeType.OUTLINE,
                    FluidHandling.NONE,
                    mc.player
                )
            );
    }

    public static Vec3d getDirectionVector(Rotation rotation) {
        float degToRad = (float) (Math.PI / 180.0);
        float pi = (float) Math.PI;
        float yawCos = MathHelper.cos(-rotation.getYaw() * degToRad - pi);
        float yawSin = MathHelper.sin(-rotation.getYaw() * degToRad - pi);
        float horizontalScale = -MathHelper.cos(-rotation.getPitch() * degToRad);
        return new Vec3d(yawSin * horizontalScale, MathHelper.sin(-rotation.getPitch() * degToRad), yawCos * horizontalScale).normalize();
    }

    public static void applyRotation(Rotation rotation) {
        mc.player.setYaw(rotation.getYaw());
        mc.player.setPitch(rotation.getPitch());
    }

    public static Rotation getRotationTo(Vec3d target) {
        return getRotationToPoint((float)target.x, (float)target.y, (float)target.z);
    }

    public static Rotation getRotationToPoint(float x, float y, float z) {
        Vec3d eyePos = mc.player.getEyePos();
        double diffX = x - eyePos.x;
        double diffY = y - eyePos.y;
        double diffZ = z - eyePos.z;
        double horizontalDistance = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float targetYaw = (float)Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
        float targetPitch = (float)(-Math.toDegrees(Math.atan2(diffY, horizontalDistance)));
        float[] current = new float[]{mc.player.getYaw(), mc.player.getPitch()};
        float newYaw = current[0];
        newYaw += wrapDegrees(targetYaw - current[0]);
        return new Rotation(newYaw, current[1] + wrapDegrees(targetPitch - current[1]));
    }

    public static Rotation getLimitedRotationTo(Rotation from, Vec3d target, float maxYawStep) {
        return getLimitedRotationToPoint(from.getYaw(), from.getPitch(), (float)target.x, (float)target.y, (float)target.z, maxYawStep);
    }

    public static Rotation addRandomJitter(Rotation rotation, Vec3d target) {
        float maxJitter = (float)MathHelper.clamp(0.18 / Math.max(mc.player.getEyePos().distanceTo(target), 1.0), 0.005, 0.15);
        float yawJitter = RandomUtil.randomFloat(-maxJitter, maxJitter);
        float pitchJitter = RandomUtil.randomFloat(-maxJitter, maxJitter);
        float newPitch = MathHelper.clamp(rotation.getPitch() + pitchJitter, -89.9F, 89.9F);
        Rotation jittered = new Rotation(rotation.getYaw() + yawJitter, newPitch);
        jittered.setClamped(rotation.isClamped());
        return jittered;
    }

    public static Rotation getLimitedRotationToPoint(float fromYaw, float fromPitch, float x, float y, float z, float maxYawStep) {
        Vec3d eyePos = mc.player.getEyePos();
        double diffX = x - eyePos.x;
        double diffY = y - eyePos.y;
        double diffZ = z - eyePos.z;
        double horizontalDistance = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float targetYaw = (float)Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
        float targetPitch = (float)(-Math.toDegrees(Math.atan2(diffY, horizontalDistance)));
        float yawDelta = wrapDegrees(targetYaw - fromYaw);
        boolean limited = Math.abs(yawDelta) > maxYawStep;
        yawDelta = MathHelper.clamp(yawDelta, -maxYawStep, maxYawStep);
        Rotation result = new Rotation(fromYaw + yawDelta, fromPitch + wrapDegrees(targetPitch - fromPitch));
        result.setClamped(limited);
        return result;
    }

    public static Rotation getEntityRotationTo(Entity entity, Vec3d target) {
        return getEntityRotationToPoint(entity, (float)target.x, (float)target.y, (float)target.z);
    }

    public static Rotation getEntityRotationToPoint(Entity entity, float x, float y, float z) {
        Vec3d eyePos = entity.getEyePos();
        double diffX = x - eyePos.x;
        double diffY = y - eyePos.y;
        double diffZ = z - eyePos.z;
        double horizontalDistance = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float targetYaw = (float)Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
        float targetPitch = (float)(-Math.toDegrees(Math.atan2(diffY, horizontalDistance)));
        float[] current = new float[]{entity.getYaw(), entity.getPitch()};
        float newYaw = current[0];
        newYaw += wrapDegrees(targetYaw - current[0]);
        return new Rotation(newYaw, current[1] + wrapDegrees(targetPitch - current[1]));
    }

    public static float wrapDegrees(float angle) {
        float wrapped = angle % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }

        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }

        return wrapped;
    }

    public static float interpolate(float from, float to, float delta) {
        return from - (from - to) * clamp(delta, 0.0F, 1.0F);
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }
}
