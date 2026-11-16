package com.corz.client.categories.player;

import ai.auth.Module;
import com.corz.client.config.BooleanSetting;
import com.corz.client.config.ConfigSetting;
import com.corz.client.config.DoubleSetting;
import com.corz.client.config.EnumSetting;
import com.corz.client.utils.IconsPngType;
import com.corz.client.utils.MillisCooldownTimer;
import com.corz.client.utils.prestige.PlayerInteractions;
import com.corz.client.utils.prestige.player.InventoryUtils;
import com.corz.client.utils.prestige.player.PotionUtils;
import com.corz.client.utils.prestige.player.StatusUtils;
import com.corz.client.utils.prestige.player.SwapStateManager;
import net.minecraft.util.Hand;

public class SmartPot extends Module {
    private final BooleanSetting angleCheck = new BooleanSetting("Angle Check", "Require looking down before throwing", true);
    private final DoubleSetting fov = new DoubleSetting("FOV", "Look-down angle used for potion throws", 20.0, 0.0, 90.0, 1.0);
    private final DoubleSetting minHealth = new DoubleSetting("Min Health", "Health at or below which health potions are used", 16.0, 0.0, 20.0, 1.0);
    private final BooleanSetting throwPot = new BooleanSetting("Throw Potion", "Actually throw the potion", false);
    private final BooleanSetting autoAim = new BooleanSetting("Auto Aim", "Aim down automatically before throwing", false);
    private final BooleanSetting silentAim = new BooleanSetting("Silent Aim", "Prefer silent aiming", true);
    private final DoubleSetting throwDelay = new DoubleSetting("Throw Delay", "Delay between potion throws in milliseconds", 250.0, 0.0, 450.0, 5.0);
    private final EnumSetting eventMode = new EnumSetting("Event Mode", "Tick or real-time processing", "Tick", "Tick", "Real");
    private final BooleanSetting autoBuff = new BooleanSetting("Auto Buff", "Keep speed and strength potions active", true);
    private final BooleanSetting swapBack = new BooleanSetting("Swap Back", "Return to the previous hotbar slot", false);
    private final DoubleSetting swapBackDelay = new DoubleSetting("Swap Back Delay", "Delay before swapping back in milliseconds", 100.0, 0.0, 500.0, 5.0);

    private final MillisCooldownTimer throwTimer = new MillisCooldownTimer();
    private final MillisCooldownTimer swapBackTimer = new MillisCooldownTimer();
    private int lastSpeedThrowTick = -1;
    private int lastStrengthThrowTick = -1;

    public SmartPot() {
        super("Player", IconsPngType.MISC);
        this.addSettings(new ConfigSetting[]{
                this.angleCheck, this.fov, this.minHealth, this.throwPot, this.autoAim,
                this.silentAim, this.throwDelay, this.eventMode, this.autoBuff,
                this.swapBack, this.swapBackDelay
        });
    }

    @Override
    protected void onReset() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        SwapStateManager.tick();
        handlePotions();
    }

    @Override
    protected void onDisable() {
        SwapStateManager.cancel(this, false);
        this.lastSpeedThrowTick = -1;
        this.lastStrengthThrowTick = -1;
        this.throwTimer.restart();
        this.swapBackTimer.restart();
    }

    private void handlePotions() {
        mainLogic();
        buffLogic();
        if (this.swapBack.isEnabled() && SwapStateManager.isOwnerActive(this)
                && this.swapBackTimer.hasElapsed((long) this.swapBackDelay.getValue())) {
            resetSwapBack();
        }
    }

    private void mainLogic() {
        if (!shouldUseHealthPotion()) return;

        if (PotionUtils.hasInstantHealthPotionInOffhand()) {
            if (this.throwPot.isEnabled()) {
                throwPotion(Hand.OFF_HAND);
            }
        } else {
            int potionSlot = PotionUtils.findInstantHealthPotionInHotbar();
            if (potionSlot != -1) {
                usePotionFromHotbar(potionSlot);
            }
        }
    }

    private void buffLogic() {
        if (!this.autoBuff.isEnabled()) return;
        if (this.angleCheck.isEnabled() && mc.player.getPitch() < 90.0f - (float) this.fov.getValue()) return;
        if (StatusUtils.hasSpeed() && StatusUtils.hasStrength()) return;

        if (this.lastSpeedThrowTick != -1 && mc.player.age <= this.lastSpeedThrowTick) {
            this.lastSpeedThrowTick = -1;
        }
        if (this.lastStrengthThrowTick != -1 && mc.player.age <= this.lastStrengthThrowTick) {
            this.lastStrengthThrowTick = -1;
        }

        if (!StatusUtils.hasSpeed() && mc.player.age > this.lastSpeedThrowTick) {
            if (PotionUtils.hasSpeedPotionInOffhand()) {
                if (this.throwPot.isEnabled()) {
                    throwPotion(Hand.OFF_HAND);
                    this.lastSpeedThrowTick = mc.player.age;
                }
            } else {
                int potionSlot = PotionUtils.findSpeedPotionInHotbar();
                if (potionSlot != -1 && usePotionFromHotbar(potionSlot)) {
                    this.lastSpeedThrowTick = mc.player.age;
                }
            }
        }

        if (!StatusUtils.hasStrength() && mc.player.age > this.lastStrengthThrowTick) {
            if (PotionUtils.hasStrengthPotionInOffhand()) {
                if (this.throwPot.isEnabled()) {
                    throwPotion(Hand.OFF_HAND);
                    this.lastStrengthThrowTick = mc.player.age;
                }
            } else {
                int potionSlot = PotionUtils.findStrengthPotionInHotbar();
                if (potionSlot != -1 && usePotionFromHotbar(potionSlot)) {
                    this.lastStrengthThrowTick = mc.player.age;
                }
            }
        }
    }

    private boolean shouldUseHealthPotion() {
        if (!mc.player.isOnGround() || !isLowHealth()) return false;

        float targetPitch = 90.0f - (float) this.fov.getValue();
        if (this.autoAim.isEnabled() && hasAnyHealthPotion()) {
            if (mc.player.getPitch() < targetPitch) {
                mc.player.setPitch(Math.min(targetPitch, mc.player.getPitch() + 20.0f));
            }
        }

        return !this.angleCheck.isEnabled() || mc.player.getPitch() >= targetPitch;
    }

    private boolean usePotionFromHotbar(int potionSlot) {
        if (this.swapBack.isEnabled()) {
            if (!SwapStateManager.swapToIfNeeded(this, potionSlot, false, -1, true)) {
                return false;
            }
        } else {
            InventoryUtils.setInvSlot(potionSlot);
        }

        this.swapBackTimer.restart();
        if (this.throwPot.isEnabled()) {
            throwPotion(Hand.MAIN_HAND);
            return true;
        }
        return false;
    }

    private void throwPotion(Hand hand) {
        if (this.throwTimer.hasElapsed((long) this.throwDelay.getValue())) {
            this.throwTimer.restart();
            PlayerInteractions.interactItem(hand);
            if (mc.player != null) {
                mc.player.swingHand(hand);
            }
        }
    }

    private boolean hasAnyHealthPotion() {
        return PotionUtils.hasInstantHealthPotionInOffhand()
                || PotionUtils.findInstantHealthPotionInHotbar() != -1;
    }

    private boolean isLowHealth() {
        return mc.player != null && mc.player.getHealth() <= (float) this.minHealth.getValue();
    }

    private void resetSwapBack() {
        SwapStateManager.cancel(this, true);
        this.swapBackTimer.restart();
    }
}
