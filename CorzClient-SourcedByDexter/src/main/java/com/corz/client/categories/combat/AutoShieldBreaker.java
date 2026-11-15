package com.corz.client.categories.combat;

import ai.auth.Module;
import com.corz.client.categories.settings.Notifications;
import com.corz.client.config.BooleanSetting;
import com.corz.client.config.ConfigSetting;
import com.corz.client.config.DoubleSetting;
import com.corz.client.config.EnumSetting;
import com.corz.client.config.FriendNameStore;
import com.corz.client.config.RangeSetting;
import com.corz.client.gui.DisabledEnabled;
import com.corz.client.utils.IconsPngType;
import com.corz.client.utils.prestige.InventoryUtil;
import com.corz.client.utils.prestige.PlayerInteractions;
import com.corz.client.utils.prestige.PrestigeSupport;
import com.corz.client.utils.prestige.RandomUtil;
import com.corz.client.utils.prestige.Rotation;
import com.corz.client.utils.prestige.RotationProvider;
import com.corz.client.utils.prestige.RotationUtil;
import com.corz.client.utils.prestige.StopWatch;
import com.corz.client.utils.prestige.TargetSelector;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;

public class AutoShieldBreaker extends Module implements RotationProvider {
    private final EnumSetting mode;
    private final BooleanSetting stun;
    private final DoubleSetting chance;
    private final BooleanSetting fromInventory;
    private final RangeSetting hitDelay;
    private final RangeSetting breakCooldown;
    private final BooleanSetting swapBack;
    private final BooleanSetting silentAim;
    private final DoubleSetting maxYaw;

    private final StopWatch breakCooldownTimer = new StopWatch();
    private final StopWatch hitDelayTimer = new StopWatch();
    private final StopWatch axeSwapTimer = new StopWatch();

    private int previousSlot = -1;
    private int axeInventorySlot = -1;
    private boolean swappedToAxe;
    private int lastActionTick = -1;

    private boolean pendingStunAttack;
    private PlayerEntity pendingStunTarget;
    private int pendingStunTick = -1;

    public AutoShieldBreaker() {
        super("Auto Shield Breaker", IconsPngType.COMBAT);
        this.mode = new EnumSetting(
            "Mode", "Mode to use to break shields", "Automatic", "On Hit", "Automatic"
        );
        this.stun = new BooleanSetting(
            "Stun", "Double hits to hit the enemy up after breaking their shield", false
        );
        this.chance = new DoubleSetting(
            "Chance", "Chance to break the shield on each attempt", 100.0, 0.0, 100.0, 1.0
        );
        this.fromInventory = new BooleanSetting(
            "From Inventory", "Also pull an axe out of your main inventory (silent SWAP).", true
        );
        this.hitDelay = new RangeSetting(
            "Hit Delay",
            "Randomized delay window between swapping to the axe and delivering the hit (ms).",
            100.0,
            150.0,
            0.0,
            300.0,
            1.0
        );
        this.breakCooldown = new RangeSetting(
            "Break Cooldown",
            "Waits a random time in this range between consecutive shield breaks (ms).",
            900.0,
            1200.0,
            0.0,
            3000.0,
            1.0
        );
        this.swapBack = new BooleanSetting(
            "Swap Back", "Silently swaps to the axe to break the shield", true
        );
        this.silentAim = new BooleanSetting(
            "Silent Aim", "Silently swaps to the axe to break the shield", false
        );
        this.maxYaw = new DoubleSetting(
            "Max Yaw", "The maximum difference in yaw the player can rotate to", 180.0, 0.0, 180.0, 1.0
        );
        this.addSettings(
            new ConfigSetting[]{
                this.mode,
                this.stun,
                this.chance,
                this.fromInventory,
                this.hitDelay,
                this.breakCooldown,
                this.swapBack,
                this.silentAim,
                this.maxYaw
            }
        );
    }

    @Override
    public Rotation provideRotation() {
        if (!this.isEnabled() || mc.player == null || mc.world == null) {
            return null;
        }
        if (!this.silentAim.isEnabled() || mc.player.fallDistance > 2.0) {
            return null;
        }

        PlayerEntity target = this.findSilentTarget();
        if (target == null || !this.isBlockingWithShield(target) || !this.isValidTarget(target)) {
            return null;
        }

        return RotationUtil.getRotationTo(target);
    }

    private PlayerEntity findSilentTarget() {
        PlayerEntity target = TargetSelector.getClosestEnemyInFov((float)this.maxYaw.getValue());
        if (target == null) {
            return null;
        }
        if (mc.player.distanceTo(target) > 3.0F || !mc.player.canSee(target)) {
            return null;
        }

        Rotation rotation = RotationUtil.getRotationTo(target);
        if (Math.abs(MathHelper.wrapDegrees(rotation.getYaw() - mc.player.getYaw())) > this.maxYaw.getValue()) {
            return null;
        }

        return target;
    }

    @Override
    public void flushActions() {
        if (!this.isEnabled() || mc.player == null || mc.world == null) {
            return;
        }

        int currentTick = mc.player.age;
        if (currentTick == this.lastActionTick) {
            return;
        }
        this.lastActionTick = currentTick;

        if (!mc.player.isAlive()) {
            this.resetState();
            return;
        }

        if (this.pendingStunAttack) {
            if (currentTick >= this.pendingStunTick) {
                PlayerEntity target = this.pendingStunTarget;
                this.pendingStunAttack = false;
                this.pendingStunTarget = null;
                this.pendingStunTick = -1;
                if (target != null
                    && target.isAlive()
                    && mc.player.fallDistance <= 2.0
                    && mc.currentScreen == null
                    && mc.isWindowFocused()
                    && mc.player.getMainHandStack().getItem() instanceof AxeItem
                    && this.isBlockingWithShield(target)
                    && this.isValidTarget(target)) {
                    PlayerInteractions.requestAttack(target, false, null);
                }
                this.swappedToAxe = false;
            }
            return;
        }

        if (mc.player.fallDistance > 2.0) {
            return;
        }

        if (this.swappedToAxe && this.axeSwapTimer.hasElapsed(this.rolledHitDelay() + 400.0F)) {
            this.swappedToAxe = false;
        }

        if (this.previousSlot != -1
            && !this.swappedToAxe
            && mc.currentScreen == null
            && !PlayerInteractions.isHoldingUsableItem()) {
            if (this.swapBack.isEnabled()) {
                if (this.axeInventorySlot != -1) {
                    InventoryUtil.swapSlot(this.axeInventorySlot, this.previousSlot);
                } else {
                    InventoryUtil.selectSlot(this.previousSlot);
                }
            }
            this.previousSlot = -1;
            this.axeInventorySlot = -1;
        }

        PlayerEntity target = mc.targetedEntity instanceof PlayerEntity crosshairTarget
            ? crosshairTarget
            : null;
        if (target == null || !this.isBlockingWithShield(target)) {
            target = this.silentAim.isEnabled() ? this.findSilentTarget() : null;
        }

        if (target != null) {
            // This is the only place that attempts a break, so it runs at most once per tick.
            this.tryBreakShield(target);
        }
    }

    public void onDoAttack() {
        if (!this.isEnabled() || !"On Hit".equals(this.mode.getCurrentOption())) {
            return;
        }
        if (mc.player == null || mc.world == null || mc.currentScreen != null || !mc.isWindowFocused()) {
            return;
        }
        if (!(mc.targetedEntity instanceof PlayerEntity target)) {
            return;
        }
        if (FriendNameStore.isFriend(target.getName().getString()) || !this.isValidTarget(target)) {
            return;
        }
        if (!this.isBlockingWithShield(target)) {
            return;
        }

        int axeSlot = this.findHotbarAxe();
        if (axeSlot == -1) {
            return;
        }
        if (this.previousSlot == -1) {
            this.previousSlot = InventoryUtil.getSelectedSlot();
            this.axeInventorySlot = -1;
        }
        InventoryUtil.selectSlot(axeSlot);
    }

    private boolean tryBreakShield(PlayerEntity target) {
        if (mc.player.fallDistance > 2.0) {
            return false;
        }
        if (mc.currentScreen != null || !mc.isWindowFocused()) {
            return false;
        }
        if (PlayerInteractions.isHoldingUsableItem() || mc.player.isUsingItem()) {
            return false;
        }
        if (RandomUtil.randomInt(0, 100) > this.chance.getValue()) {
            return false;
        }
        if (!this.breakCooldownTimer.hasElapsedRandom(this.breakCooldown)) {
            return false;
        }
        if (!"Automatic".equals(this.mode.getCurrentOption())) {
            return false;
        }
        if (FriendNameStore.isFriend(target.getName().getString()) || !this.isValidTarget(target)) {
            return false;
        }
        if (!this.isBlockingWithShield(target)) {
            return false;
        }

        if (!(mc.player.getMainHandStack().getItem() instanceof AxeItem)) {
            if (this.swappedToAxe) {
                return false;
            }

            int hotbarAxeSlot = this.findHotbarAxe();
            if (hotbarAxeSlot != -1) {
                this.previousSlot = InventoryUtil.getSelectedSlot();
                this.axeInventorySlot = -1;
                InventoryUtil.selectSlot(hotbarAxeSlot);
                this.swappedToAxe = true;
                this.axeSwapTimer.reset();
            } else if (this.fromInventory.isEnabled()) {
                int inventoryAxeSlot = this.findInventoryAxe();
                if (inventoryAxeSlot != -1) {
                    this.previousSlot = InventoryUtil.getSelectedSlot();
                    this.axeInventorySlot = inventoryAxeSlot;
                    InventoryUtil.swapSlot(inventoryAxeSlot, this.previousSlot);
                    this.swappedToAxe = true;
                    this.axeSwapTimer.reset();
                }
            }

            this.hitDelayTimer.reset();
            return false;
        }

        if (!this.hitDelayTimer.hasElapsedRandom(this.hitDelay)) {
            return false;
        }

        PlayerInteractions.requestAttack(target, false, null);

        if (this.stun.isEnabled()) {
            // Never send two interact packets in one tick. The second hit is delivered next tick.
            this.pendingStunAttack = true;
            this.pendingStunTarget = target;
            this.pendingStunTick = mc.player.age + 1;
        } else {
            this.swappedToAxe = false;
        }

        if (Notifications.isInstanceBase()) {
            DisabledEnabled.getInstance()
                .handleDisabledEnabled("Auto Shield Breaker", "Broke target's shield!", true);
        }

        this.breakCooldownTimer.reset();
        return true;
    }

    private boolean isBlockingWithShield(PlayerEntity target) {
        return target.isUsingItem() && target.getActiveItem().getItem() == Items.SHIELD;
    }

    private boolean isValidTarget(PlayerEntity target) {
        // Corz has no Prestige bot filter; accept all non-friend players.
        return target != null && target.isAlive() && !target.equals(mc.player);
    }

    private int findHotbarAxe() {
        for (int slot = 0; slot <= 8; slot++) {
            if (mc.player.getInventory().getStack(slot).getItem() instanceof AxeItem) {
                return slot;
            }
        }
        return -1;
    }

    private int findInventoryAxe() {
        for (int slot = 9; slot <= 35; slot++) {
            if (mc.player.getInventory().getStack(slot).getItem() instanceof AxeItem) {
                return slot;
            }
        }
        return -1;
    }

    private float rolledHitDelay() {
        return RandomUtil.randomFloat((float)this.hitDelay.getMin(), (float)this.hitDelay.getMax());
    }

    private void resetState() {
        this.previousSlot = -1;
        this.axeInventorySlot = -1;
        this.swappedToAxe = false;
        this.pendingStunAttack = false;
        this.pendingStunTarget = null;
        this.pendingStunTick = -1;
        this.lastActionTick = -1;
    }

    @Override
    protected void onEnable() {
        this.resetState();
        this.breakCooldownTimer.reset();
        this.hitDelayTimer.reset();
        this.axeSwapTimer.reset();
        PrestigeSupport.silentRotationManager.addRotationProvider(this);
    }

    @Override
    protected void onDisable() {
        PrestigeSupport.silentRotationManager.removeRotationProvider(this);
        this.resetState();
    }
}
