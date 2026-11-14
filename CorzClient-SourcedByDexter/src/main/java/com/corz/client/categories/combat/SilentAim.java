package com.corz.client.categories.combat;

import ai.auth.Module;
import com.corz.client.config.BooleanSetting;
import com.corz.client.config.ColorSetting;
import com.corz.client.config.ConfigSetting;
import com.corz.client.config.DoubleSetting;
import com.corz.client.utils.IconsPngType;
import com.corz.client.utils.prestige.BlockUtil;
import com.corz.client.utils.prestige.InventoryUtil;
import com.corz.client.utils.prestige.MultiSetting;
import com.corz.client.utils.prestige.PlayerInteractions;
import com.corz.client.utils.prestige.PrestigeSupport;
import com.corz.client.utils.prestige.Rotation;
import com.corz.client.utils.prestige.RotationProvider;
import com.corz.client.utils.prestige.RotationUtil;
import com.corz.client.utils.prestige.TargetSelector;
import net.minecraft.entity.Entity;
import net.minecraft.item.AxeItem;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class SilentAim extends Module implements RotationProvider {
    private BooleanSetting playerOnly;
    private BooleanSetting weaponOnly;
    private BooleanSetting continuousTracking;
    private DoubleSetting maxYaw;
    private DoubleSetting maxPitch;
    private MultiSetting aimPoints;
    private BooleanSetting renderDot;
    private DoubleSetting dotSize;
    private ColorSetting dotColor;
    private boolean hitPending = false;
    private boolean sendingAttack = false;
    private Vec3d lastAimPoint = null;
    private Entity pendingTarget = null;
    private static final String[] AIM_POINT_NAMES = new String[]{"Head", "Neck", "Body", "Arms", "Thighs", "Legs", "Feet"};
    private static final double[] AIM_POINT_HEIGHTS = new double[]{0.95, 0.85, 0.7, 0.65, 0.35, 0.2, 0.05};

    public SilentAim() {
        super("Silent Aim", IconsPngType.COMBAT);
        this.playerOnly = new BooleanSetting("Player Only", "Aims at players only.", true);
        this.weaponOnly = new BooleanSetting("Weapon Only", "Runs only while holding a sword or axe.", false);
        this.continuousTracking = new BooleanSetting(
            "Continuous Tracking", "Tracks the closest target every tick instead of only snapping on hit.", false
        );
        this.maxYaw = new DoubleSetting(
            "Max Yaw", "Caps silent aim at this yaw offset from your view (degrees).", 180.0, 0.0, 180.0, 0.1
        );
        this.maxPitch = new DoubleSetting(
            "Max Pitch",
            "Caps silent aim at this pitch offset from your view (degrees). Pitch is intrinsically clamped to \u00b190 so no seam-wrap folding is needed \u2014 a plain absolute delta. Default 90 is a no-op ceiling; tighten to reject aim vectors that would ship a suspicious head-snap up/down (e.g. crystalled feet directly below the crosshair).",
            90.0,
            0.0,
            90.0,
            0.1
        );
        this.renderDot = new BooleanSetting("Render Dot", "Draws a dot where the silent aim points.", false);
        this.dotSize = new DoubleSetting("Dot Size", "Sets the aim dot's radius (blocks).", 0.1, 0.02, 0.5, 0.01);
        this.dotColor = new ColorSetting("Dot Color", "Sets the aim dot color.", 0xFFFF4040);
        this.aimPoints = new MultiSetting(
            "Aim Points",
            AIM_POINT_NAMES.clone(),
            new boolean[]{true, false, true, false, false, false, false}
        );
        this.addSettings(
            new ConfigSetting[]{
                this.playerOnly,
                this.weaponOnly,
                this.continuousTracking,
                this.maxYaw,
                this.maxPitch,
                this.renderDot,
                this.dotSize,
                this.dotColor
            }
        );
    }

    private boolean isKillAuraActive() {
        return false;
    }

    private boolean exceedsAngleLimits(Rotation rotation) {
        return Math.abs(RotationUtil.wrapDegrees(rotation.getYaw() - mc.player.getYaw())) > this.maxYaw.getValue()
            || Math.abs(rotation.getPitch() - mc.player.getPitch()) > this.maxPitch.getValue();
    }

    private Entity findTarget() {
        Entity candidate = this.playerOnly.isEnabled() ? TargetSelector.getClosestEnemy() : TargetSelector.getClosestEntity();
        return (Entity)(candidate != null
                && candidate.isAlive()
                && !(mc.player.distanceTo(candidate) > 3.0F)
                && mc.player.canSee(candidate)
                && !BlockUtil.isCobwebInLineOfSight(candidate)
            ? candidate
            : null);
    }

    public boolean onAttack() {
        return this.onSwingHandPre() || this.onMouseButton(0, 1);
    }

    public boolean onSwingHandPre() {
        if (mc.currentScreen == null && mc.isWindowFocused()) {
            if (this.sendingAttack) {
                return false;
            } else if (!this.isKillAuraActive()) {
                if (InventoryUtil.isSword(mc.player.getMainHandStack())
                    || mc.player.getMainHandStack().getItem() instanceof AxeItem
                    || InventoryUtil.isMace(mc.player.getMainHandStack())
                    || !this.weaponOnly.isEnabled()) {
                    HitResult crosshairHit = mc.crosshairTarget;
                    if (crosshairHit != null
                        && (
                            crosshairHit.getType() == Type.MISS
                                || crosshairHit.getType() == Type.BLOCK && InventoryUtil.isSword(mc.player.getMainHandStack())
                        )) {
                        Entity target = this.findTarget();
                        if (target != null) {
                            if (!this.exceedsAngleLimits(RotationUtil.getRotationTo(
                                this.getAimPoint(target)
                            ))) {
                                this.pendingTarget = target;
                                this.hitPending = true;
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public Rotation provideRotation() {
        Entity target = this.pendingTarget;
        this.pendingTarget = null;
        if (target != null && !target.isAlive()) {
            target = null;
        }

        if (this.isKillAuraActive()) {
            this.hitPending = false;
            this.lastAimPoint = null;
            return null;
        } else {
            if (this.continuousTracking.isEnabled()) {
                if (target == null) {
                    target = this.findTarget();
                }

                Rotation trackingRotation = target != null ? this.computeAimRotation(target) : null;
                if (trackingRotation != null) {
                    this.lastAimPoint = this.getAimPoint(target);
                    if (this.hitPending) {
                        this.hitPending = false;
                        this.attackTarget(target);
                    }

                    return trackingRotation;
                }

                this.lastAimPoint = null;
            }

            if (!this.hitPending) {
                return null;
            } else {
                this.hitPending = false;
                if (target == null) {
                    return null;
                } else {
                    Rotation hitRotation = this.computeAimRotation(target);
                    if (hitRotation == null) {
                        return null;
                    } else {
                        this.lastAimPoint = this.getAimPoint(target);
                        this.attackTarget(target);
                        return hitRotation;
                    }
                }
            }
        }
    }

    private Rotation computeAimRotation(Entity target) {
        if (!InventoryUtil.isSword(mc.player.getMainHandStack())
            && !(mc.player.getMainHandStack().getItem() instanceof AxeItem)
            && !InventoryUtil.isMace(mc.player.getMainHandStack())
            && this.weaponOnly.isEnabled()) {
            return null;
        } else {
            HitResult crosshairHit = mc.crosshairTarget;
            if (this.continuousTracking.isEnabled()
                || crosshairHit != null
                    && (crosshairHit.getType() == Type.MISS || crosshairHit.getType() == Type.BLOCK && InventoryUtil.isSword(mc.player.getMainHandStack()))) {
                if (target != null && target.isAlive() && !(mc.player.distanceTo(target) > 3.0F) && mc.player.canSee(target) && !BlockUtil.isCobwebInLineOfSight(target)) {
                    Rotation rotation = RotationUtil.getRotationTo(this.getAimPoint(target));
                    return this.exceedsAngleLimits(rotation) ? null : rotation;
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }
    }

    private Vec3d getAimPoint(Entity entity) {
        Vec3d eyePos = mc.player.getEyePos();
        Box box = entity.getBoundingBox();
        double boxHeight = box.maxY - box.minY;
        if (boxHeight <= 0.0) {
            return entity.getEyePos();
        } else {
            double nearestX = MathHelper.clamp(eyePos.x, box.minX, box.maxX);
            double nearestZ = MathHelper.clamp(eyePos.z, box.minZ, box.maxZ);
            double horizontalDistSq = (eyePos.x - nearestX) * (eyePos.x - nearestX) + (eyePos.z - nearestZ) * (eyePos.z - nearestZ);
            double bestDistSq = Double.POSITIVE_INFINITY;
            Vec3d bestPoint = null;

            for (int index = 0; index < AIM_POINT_NAMES.length; index++) {
                if (this.aimPoints.isSelected(AIM_POINT_NAMES[index])) {
                    double pointY = box.minY + AIM_POINT_HEIGHTS[index] * boxHeight;
                    double dy = eyePos.y - pointY;
                    double distSq = horizontalDistSq + dy * dy;
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        bestPoint = new Vec3d(nearestX, pointY, nearestZ);
                    }
                }
            }

            return bestPoint != null ? bestPoint : entity.getEyePos();
        }
    }

    private void attackTarget(Entity target) {
        if (target != null) {
            this.sendingAttack = true;
            PlayerInteractions.attackEntity(target);
            this.sendingAttack = false;
        }
    }

    public boolean onMouseButton(int button, int action) {
        if (mc.currentScreen == null && mc.isWindowFocused()) {
            if (!this.isKillAuraActive()) {
                if (button == 0 && action == 1) {
                    HitResult crosshairHit = mc.crosshairTarget;
                    if (crosshairHit != null && crosshairHit.getType() == Type.BLOCK && InventoryUtil.isSword(mc.player.getMainHandStack())) {
                        Entity target = this.findTarget();
                        if (target == null) {
                            return false;
                        }

                        if (this.exceedsAngleLimits(RotationUtil.getRotationTo(
                            this.getAimPoint(target)
                        ))) {
                            return false;
                        }

                        this.pendingTarget = target;
                        this.hitPending = true;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean onHandleBlockBreaking() {
        if (mc.currentScreen == null && mc.isWindowFocused()) {
            if (!this.isKillAuraActive()) {
                HitResult crosshairHit = mc.crosshairTarget;
                if (crosshairHit != null && crosshairHit.getType() == Type.BLOCK && InventoryUtil.isSword(mc.player.getMainHandStack())) {
                    Entity target = this.findTarget();
                    if (target == null) {
                        return false;
                    }

                    if (this.exceedsAngleLimits(RotationUtil.getRotationTo(
                        this.getAimPoint(target)
                    ))) {
                        return false;
                    }

                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void onEnable() {
        this.lastAimPoint = null;
        this.pendingTarget = null;
        this.hitPending = false;
        this.sendingAttack = false;
        PrestigeSupport.silentRotationManager.addRotationProvider(this);
    }

    @Override
    protected void onDisable() {
        this.lastAimPoint = null;
        this.pendingTarget = null;
        this.hitPending = false;
        this.sendingAttack = false;
        PrestigeSupport.silentRotationManager.removeRotationProvider(this);
    }
}
