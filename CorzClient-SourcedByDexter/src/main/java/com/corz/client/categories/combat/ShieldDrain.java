package com.corz.client.categories.combat;

import ai.auth.Module;
import com.corz.client.config.BooleanSetting;
import com.corz.client.config.ConfigSetting;
import com.corz.client.config.DoubleSetting;
import com.corz.client.config.FriendNameStore;
import com.corz.client.utils.IconsPngType;
import com.corz.client.utils.prestige.InventoryUtil;
import com.corz.client.utils.prestige.PlayerInteractions;
import com.corz.client.utils.prestige.TargetSelector;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;

public class ShieldDrain extends Module {
    private final DoubleSetting horizontalTargetDist;
    private final DoubleSetting maxRange;
    private final DoubleSetting minFallDistance;
    private final BooleanSetting onlyOnCrosshair;
    private final BooleanSetting requireMace;
    private final BooleanSetting pauseOnGround;
    private final DoubleSetting clicksPerTick;
    private final BooleanSetting autoSwap;
    private final BooleanSetting silent;

    private int previousSlot = -1;
    private boolean restoreNextTick;
    private static boolean drainingThisTick;

    public ShieldDrain() {
        super("Shield Drain", IconsPngType.COMBAT);
        this.horizontalTargetDist = new DoubleSetting(
            "Horizontal Target Dist", "Searches for shielders within this horizontal distance (blocks).", 5.0, 0.0, 6.0, 0.1
        );
        this.maxRange = new DoubleSetting("Max Range", "Ignores targets beyond this distance (blocks).", 4.5, 3.0, 6.0, 0.1);
        this.minFallDistance = new DoubleSetting(
            "Min Fall Distance", "Drains only after falling this far (blocks). 0 = always.", 0.0, 0.0, 50.0, 1.0
        );
        this.onlyOnCrosshair = new BooleanSetting(
            "Only On Crosshair", "Drains only the player under your crosshair.", false
        );
        this.requireMace = new BooleanSetting("Require Mace", "Runs only while holding a mace; disables auto-swap.", true);
        this.pauseOnGround = new BooleanSetting("Pause On Ground", "Stops draining when you touch the ground.", false);
        this.clicksPerTick = new DoubleSetting(
            "Clicks Per Tick", "Sends this many attacks per tick. 3 is about 60 CPS; 10 is about 200 CPS, blatant.", 2.0, 1.0, 10.0, 1.0
        );
        this.autoSwap = new BooleanSetting("Auto Swap", "Switches to a mace when you are not holding one.", true);
        this.silent = new BooleanSetting("Silent", "Returns to the previous slot once the target stops shielding.", false);
        this.addSettings(
            new ConfigSetting[]{
                this.horizontalTargetDist,
                this.maxRange,
                this.minFallDistance,
                this.onlyOnCrosshair,
                this.requireMace,
                this.pauseOnGround,
                this.clicksPerTick,
                this.autoSwap,
                this.silent
            }
        );
    }

    public static boolean isDraining() {
        return drainingThisTick;
    }

    @Override
    protected void onEnable() {
        this.previousSlot = -1;
        this.restoreNextTick = false;
        drainingThisTick = false;
    }

    @Override
    protected void onDisable() {
        drainingThisTick = false;
        this.restoreNextTick = false;
        this.restorePreviousSlot();
    }

    @Override
    protected void onReset() {
        if (this.restoreNextTick) {
            this.restorePreviousSlot();
        }
        drainingThisTick = false;

        if (mc.player == null || mc.world == null || mc.currentScreen != null || !mc.isWindowFocused()) {
            return;
        }

        if (mc.player.isUsingItem()) {
            return;
        }

        if (this.pauseOnGround.isEnabled() && mc.player.isOnGround()) {
            this.restorePreviousSlot();
            return;
        }

        if (mc.player.fallDistance < this.minFallDistance.getValue()) {
            this.restorePreviousSlot();
            return;
        }

        PlayerEntity target = this.findShieldTarget();
        if (target == null || mc.player.distanceTo(target) > this.maxRange.getValue()) {
            this.restorePreviousSlot();
            return;
        }

        if (!this.isHoldingDrainWeapon()) {
            if (this.requireMace.isEnabled() && !this.silent.isEnabled()) {
                return;
            }
            if (!this.autoSwap.isEnabled()) {
                return;
            }

            Integer swapSlot = this.silent.isEnabled() ? this.findAxeSlot() : InventoryUtil.findMaceSlot();
            if (swapSlot == null) {
                swapSlot = InventoryUtil.findMaceSlot();
            }
            if (swapSlot == null) {
                return;
            }

            if (this.silent.isEnabled() && this.previousSlot == -1) {
                this.previousSlot = InventoryUtil.getSelectedSlot();
            }
            InventoryUtil.selectSlot(swapSlot);
            if (this.silent.isEnabled()) {
                this.restoreNextTick = true;
            }
        }

        int clicks = (int)this.clicksPerTick.getValue();
        for (int clickIndex = 0; clickIndex < clicks; clickIndex++) {
            PlayerInteractions.attackEntity(target);
        }
        drainingThisTick = true;
    }

    private boolean isHoldingDrainWeapon() {
        return InventoryUtil.isMace(mc.player.getMainHandStack())
            || this.silent.isEnabled() && mc.player.getMainHandStack().getItem() instanceof AxeItem;
    }

    private Integer findAxeSlot() {
        for (int slot = 0; slot <= 8; slot++) {
            if (mc.player.getInventory().getStack(slot).getItem() instanceof AxeItem) {
                return slot;
            }
        }
        return null;
    }

    private PlayerEntity findShieldTarget() {
        if (mc.crosshairTarget instanceof EntityHitResult crosshairHit
            && crosshairHit.getEntity() instanceof PlayerEntity crosshairPlayer
            && TargetSelector.isBlockingWithShield(crosshairPlayer)
            && !FriendNameStore.isFriend(crosshairPlayer.getName().getString())) {
            return crosshairPlayer;
        }

        if (this.onlyOnCrosshair.isEnabled()) {
            return null;
        }

        return this.findClosestShielder((float)this.horizontalTargetDist.getValue());
    }

    private PlayerEntity findClosestShielder(float maxDistance) {
        PlayerEntity closest = null;
        double closestDistanceSq = Double.MAX_VALUE;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player.isAlive()
                && !player.equals(mc.player)
                && !FriendNameStore.isFriend(player.getName().getString())
                && TargetSelector.isBlockingWithShield(player)) {
                Vec3d levelPos = new Vec3d(player.getX(), mc.player.getY(), player.getZ());
                double distanceSq = mc.player.squaredDistanceTo(levelPos);
                if (distanceSq <= maxDistance * maxDistance && distanceSq < closestDistanceSq) {
                    closestDistanceSq = distanceSq;
                    closest = player;
                }
            }
        }

        return closest;
    }

    private void restorePreviousSlot() {
        this.restoreNextTick = false;
        if (this.previousSlot != -1) {
            InventoryUtil.selectSlot(this.previousSlot);
            this.previousSlot = -1;
        }
    }
}
