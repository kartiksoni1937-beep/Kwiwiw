package com.corz.client.categories.mace;

import ai.auth.Module;
import com.corz.client.config.BooleanSetting;
import com.corz.client.config.ConfigSetting;
import com.corz.client.config.DoubleSetting;
import com.corz.client.config.EnumSetting;
import com.corz.client.config.FriendNameStore;
import com.corz.client.config.KeybindSetting;
import com.corz.client.categories.combat.ShieldDrain;
import com.corz.client.inventory.InventoryStackUtilsEmpty;
import com.corz.client.utils.IconsPngType;
import com.corz.client.utils.prestige.BlockUtil;
import com.corz.client.utils.prestige.InventoryUtil;
import com.corz.client.utils.prestige.PlayerInteractions;
import com.corz.client.utils.prestige.PositionUtil;
import com.corz.client.utils.prestige.PrestigeSupport;
import com.corz.client.utils.prestige.Rotation;
import com.corz.client.utils.prestige.RotationProvider;
import com.corz.client.utils.prestige.RotationUtil;
import com.corz.client.utils.prestige.StopWatch;
import com.corz.client.utils.prestige.TargetSelector;
import java.util.List;
import net.minecraft.client.util.InputUtil;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;

public class AutoMace extends Module implements RotationProvider {
    private final DoubleSetting horizontalTargetDist;
    private final DoubleSetting minFallDistance;
    private final BooleanSetting stunSlam;
    private final EnumSetting stunSlamMode;
    private final KeybindSetting stunSlamBind;
    private final DoubleSetting chance;
    private final BooleanSetting scaleWithFall;
    private final DoubleSetting stunMinFall;
    private final DoubleSetting axeHoldDelay;
    private final BooleanSetting breachCheck;
    private final BooleanSetting trigger;
    private final BooleanSetting silentAim;
    private final DoubleSetting maxYaw;
    private final BooleanSetting takeoffElytra;
    private final BooleanSetting swapBack;
    private final DoubleSetting swapBackDelay;
    private final BooleanSetting expandHitbox;
    private final DoubleSetting hitboxExpansion;

    private final StopWatch attackTimer = new StopWatch();
    private final StopWatch stunSlamTimer = new StopWatch();
    private final StopWatch swapBackTimer = new StopWatch();
    private final StopWatch savedSlotTimer = new StopWatch();

    private boolean stunSlamQueued;
    private boolean attackingInternally;
    private int previousSlot = -1;
    private boolean swapBackPending;
    private boolean equippedChestplateThisTick;
    private PlayerEntity pendingAxeTarget;
    private Rotation pendingAxeRotation;
    private long axeHoldStartTime;
    private int pendingMaceSlot = -1;
    private Runnable pendingAction;

    public AutoMace() {
        super("Auto Mace", IconsPngType.COMBAT);
        this.horizontalTargetDist = new DoubleSetting(
            "Horizontal Target Dist", "Swaps only with a target within this horizontal distance (blocks).", 5.0, 0.0, 6.0, 0.1
        );
        this.minFallDistance = new DoubleSetting(
            "Min Fall Distance", "Swaps to the mace only after falling this far (blocks).", 10.0, 0.0, 100.0, 1.0
        );
        this.stunSlam = new BooleanSetting("Stun Slam", "Hits with the axe to stun, then maces.", false);
        this.stunSlamMode = new EnumSetting(
            "Stun Slam Mode", "Chance fires on a random roll; Hold Key fires only while the bind is held.", "Chance", "Chance", "Hold Key"
        );
        this.stunSlamBind = new KeybindSetting("Stun Slam Bind", "Hold to allow stun slams (Hold Key mode).", -1);
        this.chance = new DoubleSetting("Chance", "Rolls this chance on each stun slam (%).", 100.0, 0.0, 100.0, 1.0);
        this.scaleWithFall = new BooleanSetting(
            "Scale With Fall", "Scales the chance with fall distance; big drops fire more often.", false
        );
        this.stunMinFall = new DoubleSetting(
            "Stun Min Fall", "Stun-slams only after falling this far (blocks). 0 = module minimum.", 0.0, 0.0, 100.0, 1.0
        );
        this.axeHoldDelay = new DoubleSetting(
            "Axe Hold Delay", "Holds the axe visibly this many ticks before hitting. 2-4 reads as legit.", 0.0, 0.0, 10.0, 1.0
        );
        this.breachCheck = new BooleanSetting("Breach Check", "Swaps to Breach instead of Density on low fall distance.", false);
        this.trigger = new BooleanSetting("Trigger", "Attacks with the mace automatically.", true);
        this.silentAim = new BooleanSetting("Silent Aim", "Aims at the target server-side; your camera never moves.", false);
        this.maxYaw = new DoubleSetting(
            "Max Yaw", "Caps silent aim at this yaw offset from your view (degrees).", 180.0, 0.0, 180.0, 1.0
        );
        this.takeoffElytra = new BooleanSetting(
            "Takeoff Elytra", "Unequips the elytra when diving on a maceable target.", false
        );
        this.swapBack = new BooleanSetting("Swap Back", "Returns to the previous slot after the mace hit lands.", false);
        this.swapBackDelay = new DoubleSetting(
            "Swap Back Delay", "Waits this long after the hit before swapping back (ms).", 50.0, 0.0, 500.0, 10.0
        );
        this.expandHitbox = new BooleanSetting(
            "Expand Hitbox", "Grows the target hitbox for the hit. Easier hits, higher flag risk.", false
        );
        this.hitboxExpansion = new DoubleSetting(
            "Hitbox Expansion", "Expands the target hitbox by this amount (blocks).", 0.5, 0.0, 5.0, 0.05
        );
        this.addSettings(
            new ConfigSetting[]{
                this.horizontalTargetDist,
                this.minFallDistance,
                this.stunSlam,
                this.stunSlamMode,
                this.stunSlamBind,
                this.chance,
                this.scaleWithFall,
                this.stunMinFall,
                this.axeHoldDelay,
                this.breachCheck,
                this.trigger,
                this.silentAim,
                this.maxYaw,
                this.takeoffElytra,
                this.swapBack,
                this.swapBackDelay,
                this.expandHitbox,
                this.hitboxExpansion
            }
        );
    }

    @Override
    public Rotation provideRotation() {
        if (!this.isEnabled() || mc.player == null || mc.world == null) {
            return null;
        }

        this.equippedChestplateThisTick = false;

        if (this.previousSlot != -1 && !this.swapBackPending && this.savedSlotTimer.hasElapsed(2000.0F)) {
            this.clearSavedSlot();
        }

        if (this.previousSlot != -1
            && this.swapBackPending
            && !this.stunSlamQueued
            && this.pendingAxeTarget == null
            && this.swapBackTimer.hasElapsed((float)this.swapBackDelay.getValue())) {
            this.swapBackToSavedSlot();
        }

        if (this.pendingAxeTarget != null && !this.stunSlam.isEnabled()) {
            int returnSlot = this.swapBack.isEnabled() && this.previousSlot != -1 ? this.previousSlot : this.pendingMaceSlot;
            this.clearPendingStunSlam();
            this.clearSavedSlot();
            if (returnSlot >= 0 && InventoryUtil.getSelectedSlot() != returnSlot) {
                InventoryUtil.selectSlot(returnSlot);
            }
        }

        if (this.pendingAxeTarget != null) {
            return this.processPendingStunSlam();
        }

        if (this.swapElytraForChestplate()) {
            return null;
        }

        int maceSlot = this.findBestMaceSlot();
        if (maceSlot == -1) {
            this.stunSlamQueued = false;
            return null;
        }

        if (mc.player.fallDistance <= this.minFallDistance.getValue()) {
            return null;
        }

        if (this.stunSlam.isEnabled() && this.stunSlamQueued) {
            int axeSlot = this.findAxeSlot();
            if (axeSlot == -1) {
                this.stunSlamQueued = false;
                return null;
            }

            PlayerEntity target;
            Rotation rotation = null;
            if (mc.crosshairTarget instanceof EntityHitResult crosshairHit
                && crosshairHit.getEntity() instanceof PlayerEntity crosshairPlayer) {
                target = crosshairPlayer;
            } else {
                target = TargetSelector.getClosestEnemyInFov((float)this.maxYaw.getValue());
                if (target == null
                    || mc.player.distanceTo(target) > 3.0F
                    || !mc.player.canSee(target)
                    || BlockUtil.isCobwebInLineOfSight(target)) {
                    this.stunSlamQueued = false;
                    return null;
                }

                rotation = RotationUtil.getRotationTo(target);
                if (Math.abs(RotationUtil.wrapDegrees(rotation.getYaw() - mc.player.getYaw())) > this.maxYaw.getValue()) {
                    this.stunSlamQueued = false;
                    return null;
                }
            }

            if (!this.hasFallenEnoughToStun()) {
                this.stunSlamQueued = false;
                return null;
            }

            this.stunSlamQueued = false;
            return this.startStunSlam(target, rotation, axeSlot, maceSlot);
        }

        if (mc.crosshairTarget instanceof EntityHitResult crosshairHit
            && crosshairHit.getEntity() instanceof PlayerEntity hitPlayer) {
            if (this.trigger.isEnabled()
                && this.attackTimer.hasElapsed(800.0F)) {
                if (TargetSelector.isBlockingWithShield(hitPlayer)
                    && !ShieldDrain.isDraining()
                    && this.stunSlam.isEnabled()
                    && this.stunSlamTimer.hasElapsed(800.0F)
                    && this.shouldStunSlam()
                    && this.hasFallenEnoughToStun()) {
                    int crosshairAxeSlot = this.findAxeSlot();
                    if (crosshairAxeSlot != -1) {
                        return this.startStunSlam(hitPlayer, null, crosshairAxeSlot, maceSlot);
                    }
                }

                if (PositionUtil.isPlayerGliding()) {
                    return null;
                }

                this.selectMaceForAttack(maceSlot);
                this.queueAttack(hitPlayer, () -> {
                    this.attackTimer.reset();
                    if (this.swapBack.isEnabled() && this.previousSlot != -1) {
                        this.swapBackTimer.reset();
                        this.swapBackPending = true;
                    }
                });
            }
            return null;
        }

        if (this.trigger.isEnabled() && this.silentAim.isEnabled() && this.attackTimer.hasElapsed(800.0F)) {
            PlayerEntity aimTarget = TargetSelector.getClosestEnemyInFov((float)this.maxYaw.getValue());
            if (aimTarget == null
                || mc.player.distanceTo(aimTarget) > 3.0F
                || !mc.player.canSee(aimTarget)
                || BlockUtil.isCobwebInLineOfSight(aimTarget)) {
                return null;
            }

            Rotation aimRotation = RotationUtil.getRotationTo(aimTarget);
            if (Math.abs(RotationUtil.wrapDegrees(aimRotation.getYaw() - mc.player.getYaw())) > this.maxYaw.getValue()) {
                return null;
            }

            if (TargetSelector.isBlockingWithShield(aimTarget)
                && !ShieldDrain.isDraining()
                && this.stunSlam.isEnabled()
                && this.stunSlamTimer.hasElapsed(800.0F)
                && this.shouldStunSlam()
                && this.hasFallenEnoughToStun()) {
                int aimAxeSlot = this.findAxeSlot();
                if (aimAxeSlot == -1) {
                    this.stunSlamQueued = false;
                    return null;
                }
                return this.startStunSlam(aimTarget, aimRotation, aimAxeSlot, maceSlot);
            }

            if (PositionUtil.isPlayerGliding()) {
                return null;
            }

            this.selectMaceForAttack(maceSlot);
            this.queueAttack(aimTarget, () -> {
                this.attackTimer.reset();
                if (this.swapBack.isEnabled() && this.previousSlot != -1) {
                    this.swapBackTimer.reset();
                    this.swapBackPending = true;
                }
            });
            return aimRotation;
        }

        this.stunSlamQueued = false;
        return null;
    }

    private Rotation startStunSlam(PlayerEntity target, Rotation rotation, int axeSlot, int maceSlot) {
        if (InventoryUtil.getSelectedSlot() != axeSlot) {
            this.savePreviousSlot();
            InventoryUtil.selectSlot(axeSlot);
        }

        int holdTicks = (int)this.axeHoldDelay.getValue();
        if (holdTicks <= 0) {
            this.queueAttack(target, this.stunSlamTimer::reset);
            return rotation;
        }

        this.pendingAxeTarget = target;
        this.pendingAxeRotation = rotation;
        this.pendingMaceSlot = maceSlot;
        this.axeHoldStartTime = System.currentTimeMillis();
        return rotation;
    }

    private Rotation processPendingStunSlam() {
        if (this.pendingAxeTarget != null
            && this.pendingAxeTarget.isAlive()
            && mc.player != null
            && mc.world != null
            && !(mc.player.distanceTo(this.pendingAxeTarget) > 6.0F)) {
            if (System.currentTimeMillis() - this.axeHoldStartTime < (long)((int)this.axeHoldDelay.getValue()) * 50L) {
                return this.pendingAxeRotation;
            }

            PlayerEntity target = this.pendingAxeTarget;
            Rotation rotation = this.pendingAxeRotation;
            int maceSlot = this.pendingMaceSlot;
            this.queueAttack(target, () -> {
                this.stunSlamTimer.reset();
                if (maceSlot >= 0 && InventoryUtil.getSelectedSlot() != maceSlot) {
                    InventoryUtil.selectSlot(maceSlot);
                }
                this.clearPendingStunSlam();
            });
            return rotation;
        }

        int returnSlot = this.swapBack.isEnabled() && this.previousSlot != -1 ? this.previousSlot : this.pendingMaceSlot;
        this.clearPendingStunSlam();
        this.clearSavedSlot();
        if (returnSlot >= 0 && InventoryUtil.getSelectedSlot() != returnSlot) {
            InventoryUtil.selectSlot(returnSlot);
        }
        return null;
    }

    private void selectMaceForAttack(int maceSlot) {
        if (InventoryUtil.getSelectedSlot() != maceSlot) {
            this.savePreviousSlot();
            InventoryUtil.selectSlot(maceSlot);
        }
    }

    private void queueAttack(PlayerEntity target, Runnable afterAttack) {
        if (this.pendingAction == null && !this.attackingInternally) {
            this.pendingAction = () -> {
                this.attackingInternally = true;
                boolean queued = PlayerInteractions.requestAttack(target, () -> {
                    this.attackingInternally = false;
                    if (afterAttack != null) {
                        afterAttack.run();
                    }
                });
                if (!queued) {
                    this.attackingInternally = false;
                }
            };
        }
    }

    @Override
    public void flushActions() {
        Runnable action = this.pendingAction;
        this.pendingAction = null;
        if (action != null) {
            action.run();
        }
    }

    private void savePreviousSlot() {
        if (this.swapBack.isEnabled() && this.previousSlot == -1 && !this.equippedChestplateThisTick) {
            this.previousSlot = InventoryUtil.getSelectedSlot();
            this.savedSlotTimer.reset();
        }
    }

    private void swapBackToSavedSlot() {
        int savedSlot = this.previousSlot;
        this.clearSavedSlot();
        if (savedSlot >= 0 && InventoryUtil.getSelectedSlot() != savedSlot) {
            InventoryUtil.selectSlot(savedSlot);
        }
    }

    private void clearSavedSlot() {
        this.previousSlot = -1;
        this.swapBackPending = false;
    }

    private void clearPendingStunSlam() {
        this.pendingAxeTarget = null;
        this.pendingAxeRotation = null;
        this.axeHoldStartTime = 0L;
        this.pendingMaceSlot = -1;
    }

    private boolean shouldStunSlam() {
        if (!this.stunSlam.isEnabled()) {
            return false;
        }

        if (!"Hold Key".equals(this.stunSlamMode.getCurrentOption())) {
            float rollChance = (float)this.chance.getValue();
            if (this.scaleWithFall.isEnabled()) {
                float fallScale = Math.max(
                    0.0F,
                    Math.min(
                        1.0F,
                        ((float)mc.player.fallDistance - (float)Math.max(this.minFallDistance.getValue(), this.stunMinFall.getValue())) / 10.0F
                    )
                );
                rollChance *= fallScale;
            }
            return Math.random() * 100.0 < rollChance;
        }

        int keyCode = this.stunSlamBind.getKeyCode();
        return keyCode >= 0 && InputUtil.isKeyPressed(mc.getWindow(), keyCode);
    }

    private boolean hasFallenEnoughToStun() {
        return mc.player.fallDistance >= Math.max(this.minFallDistance.getValue(), this.stunMinFall.getValue());
    }

    public boolean shouldExpandHitbox(Entity entity) {
        if (!this.isEnabled() || !this.expandHitbox.isEnabled() || mc.player == null || mc.world == null) {
            return false;
        }
        if (mc.player.fallDistance <= this.minFallDistance.getValue()) {
            return false;
        }

        PlayerEntity target = this.findClosestHorizontalTarget((float)this.horizontalTargetDist.getValue());
        return target != null && target.equals(entity);
    }

    public float getHitboxExpansion() {
        return (float)this.hitboxExpansion.getValue() / 5.0F;
    }

    public void onDoAttack() {
        if (!this.isEnabled() || this.attackingInternally || mc.player == null || mc.world == null) {
            return;
        }

        PlayerEntity target = this.findClosestHorizontalTarget((float)this.horizontalTargetDist.getValue());
        if (!this.stunSlamQueued
            && mc.player.fallDistance > this.minFallDistance.getValue()
            && target != null
            && TargetSelector.isBlockingWithShield(target)
            && mc.player.getMainHandStack().getItem() instanceof AxeItem
            && this.stunSlam.isEnabled()
            && this.stunSlamTimer.hasElapsed(800.0F)
            && this.shouldStunSlam()
            && this.hasFallenEnoughToStun()
            && this.findAxeSlot() != -1) {
            this.stunSlamQueued = true;
            return;
        }

        int maceSlot = this.findBestMaceSlot();
        if (maceSlot != -1
            && InventoryUtil.getSelectedSlot() != maceSlot
            && mc.crosshairTarget instanceof EntityHitResult
            && mc.player.fallDistance > this.minFallDistance.getValue()) {
            this.savePreviousSlot();
            InventoryUtil.selectSlot(maceSlot);
            if (this.swapBack.isEnabled() && this.previousSlot != -1) {
                this.swapBackTimer.reset();
                this.swapBackPending = true;
            }
        }
    }

    private PlayerEntity findClosestHorizontalTarget(float maxDistance) {
        float maxDistanceSq = maxDistance * maxDistance;
        PlayerEntity closest = null;
        double closestDistanceSq = Double.MAX_VALUE;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player.isAlive() && !player.equals(mc.player) && !FriendNameStore.isFriend(player.getName().getString())) {
                Vec3d levelPos = new Vec3d(player.getX(), mc.player.getY(), player.getZ());
                double distanceSq = mc.player.squaredDistanceTo(levelPos);
                if (distanceSq <= maxDistanceSq && distanceSq < closestDistanceSq) {
                    closestDistanceSq = distanceSq;
                    closest = player;
                }
            }
        }

        return closest;
    }

    private boolean swapElytraForChestplate() {
        if (!this.takeoffElytra.isEnabled()
            || mc.player.fallDistance < this.minFallDistance.getValue()) {
            return false;
        }

        PlayerEntity target = this.findClosestHorizontalTarget((float)this.horizontalTargetDist.getValue());
        if (target == null
            || !target.isAlive()
            || mc.player.distanceTo(target) > (TargetSelector.isBlockingWithShield(target) ? 8.0F : 6.0F)
            || !PositionUtil.isPlayerGliding()
            || mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) {
            return false;
        }

        Integer chestplateSlot = InventoryUtil.findHotbarSlot(Items.NETHERITE_CHESTPLATE);
        if (chestplateSlot != null) {
            InventoryUtil.selectSlot(chestplateSlot);
            PlayerInteractions.interactItem(Hand.MAIN_HAND);
            this.equippedChestplateThisTick = true;
            return false;
        }

        chestplateSlot = InventoryUtil.findHotbarSlot(Items.DIAMOND_CHESTPLATE);
        if (chestplateSlot != null) {
            InventoryUtil.selectSlot(chestplateSlot);
            PlayerInteractions.interactItem(Hand.MAIN_HAND);
            this.equippedChestplateThisTick = true;
            return false;
        }

        List<Integer> inventorySlots = InventoryUtil.findInventorySlots(Items.NETHERITE_CHESTPLATE, false);
        if (!inventorySlots.isEmpty()) {
            this.swapChestplateFromInventory(inventorySlots.get(0));
            return true;
        }

        inventorySlots = InventoryUtil.findInventorySlots(Items.DIAMOND_CHESTPLATE, false);
        if (!inventorySlots.isEmpty()) {
            this.swapChestplateFromInventory(inventorySlots.get(0));
            return true;
        }

        return false;
    }

    private void swapChestplateFromInventory(int sourceSlot) {
        InventoryUtil.clickSlot(sourceSlot, 0, SlotActionType.PICKUP);
        InventoryUtil.clickSlot(6, 0, SlotActionType.PICKUP);
        InventoryUtil.clickSlot(sourceSlot, 0, SlotActionType.PICKUP);
    }

    private int findAxeSlot() {
        for (int slot = 0; slot <= 8; slot++) {
            if (mc.player.getInventory().getStack(slot).getItem() instanceof AxeItem) {
                return slot;
            }
        }
        return -1;
    }

    private int findBestMaceSlot() {
        int bestLevel = -1;
        int bestSlot = -1;

        for (int slot = 0; slot <= 8; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!InventoryUtil.isMace(stack)) {
                continue;
            }

            int level = this.breachCheck.isEnabled() && mc.player.fallDistance <= 7.0
                ? this.getBreachLevel(stack)
                : this.getDensityLevel(stack);
            if (level > bestLevel) {
                bestLevel = level;
                bestSlot = slot;
            }
        }

        return bestSlot;
    }

    private int getDensityLevel(ItemStack stack) {
        return InventoryStackUtilsEmpty.getEnchantmentsDefault(stack, Enchantments.DENSITY);
    }

    private int getBreachLevel(ItemStack stack) {
        return InventoryStackUtilsEmpty.getEnchantmentsDefault(stack, Enchantments.BREACH);
    }

    @Override
    protected void onEnable() {
        this.stunSlamQueued = false;
        this.attackingInternally = false;
        this.previousSlot = -1;
        this.swapBackPending = false;
        this.equippedChestplateThisTick = false;
        this.pendingAxeTarget = null;
        this.pendingAxeRotation = null;
        this.pendingMaceSlot = -1;
        this.pendingAction = null;
        this.attackTimer.reset();
        this.stunSlamTimer.reset();
        this.swapBackTimer.reset();
        this.savedSlotTimer.reset();
        PrestigeSupport.silentRotationManager.addRotationProvider(this);
    }

    @Override
    protected void onDisable() {
        PrestigeSupport.silentRotationManager.removeRotationProvider(this);
        this.clearPendingStunSlam();
        this.clearSavedSlot();
        this.stunSlamQueued = false;
        this.attackingInternally = false;
        this.equippedChestplateThisTick = false;
        this.pendingAction = null;
    }
}
