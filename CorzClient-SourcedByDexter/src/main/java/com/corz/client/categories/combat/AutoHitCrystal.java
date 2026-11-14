package com.corz.client.categories.combat;

import ai.auth.Module;
import com.corz.client.config.BooleanSetting;
import com.corz.client.config.ConfigSetting;
import com.corz.client.config.EnumSetting;
import com.corz.client.config.RangeSetting;
import com.corz.client.utils.IconsPngType;
import com.corz.client.utils.ScaffoldTrade;
import com.corz.client.utils.prestige.BlockUtil;
import com.corz.client.utils.prestige.DeathLootDetector;
import com.corz.client.utils.prestige.InventoryUtil;
import com.corz.client.utils.prestige.PlayerInteractions;
import com.corz.client.utils.prestige.PositionUtil;
import com.corz.client.utils.prestige.PrestigeSupport;
import com.corz.client.utils.prestige.RandomUtil;
import com.corz.client.utils.prestige.Rotation;
import com.corz.client.utils.prestige.RotationProvider;
import com.corz.client.utils.prestige.StopWatch;
import com.corz.client.utils.prestige.TargetSelector;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class AutoHitCrystal extends Module implements RotationProvider {
    public static boolean active = false;
    private BooleanSetting stopInAir;
    private BooleanSetting swordOnly;
    private BooleanSetting pauseOnKill;
    private RangeSetting delay;
    private BooleanSetting perfectTiming;
    private EnumSetting crystalMode;
    private RangeSetting crystalDelay;
    private BooleanSetting silentRotations;
    private final StopWatch delayTimer = new StopWatch();
    private int stage = 0;
    private float delayRolled;
    private float crystalDelayRolled;
    private Runnable pendingAction;
    private Rotation pendingRotation;

    public AutoHitCrystal() {
        super("Auto Hit Crystal", IconsPngType.COMBAT);
        this.stopInAir = new BooleanSetting("Stop In Air", "Pauses while you are airborne.", false);
        this.swordOnly = new BooleanSetting("Sword Only", "Runs only while holding a sword.", false);
        this.pauseOnKill = new BooleanSetting("Pause On Kill", "Pauses while a dead body is nearby.", false);
        this.delay = new RangeSetting("Delay", "Waits a random time in this range between actions (ms).", 30.0, 50.0, 0.0, 200.0, 1.0);
        this.perfectTiming = new BooleanSetting("Perfect Timing", "Syncs crystal breaks to the enemy's damage ticks.", false);
        this.crystalMode = new EnumSetting(
            "Crystal", "Places one crystal, two, none, or keeps placing (Infinite).", "Single Tap", "None", "Single Tap", "Double Tap", "Infinite"
        );
        this.crystalDelay = new RangeSetting(
            "Crystal Delay", "Waits a random time in this range between crystal placements (ms).", 50.0, 150.0, 0.0, 200.0, 1.0
        );
        this.silentRotations = new BooleanSetting(
            "Silent Rotations", "Attacks crystals with server-side rotations; your camera never moves.", false
        );
        this.addSettings(
            new ConfigSetting[]{
                this.stopInAir, this.swordOnly, this.pauseOnKill, this.delay, this.perfectTiming, this.crystalMode, this.crystalDelay, this.silentRotations
            }
        );
        this.delayRolled = (float)this.delay.getMin();
        this.crystalDelayRolled = (float)this.crystalDelay.getMin();
    }

    @Override
    protected void onEnable() {
        if (this.getBindKey() == 9998 && PlayerInteractions.isHoldingUsableItem()) {
            this.toggle();
            return;
        }

        this.stage = 0;
        this.pendingAction = null;
        this.pendingRotation = null;
        this.delayRolled = (float)this.delay.getMin();
        this.crystalDelayRolled = (float)this.crystalDelay.getMin();
        active = !"None".equals(this.crystalMode.getCurrentOption());
        PrestigeSupport.silentRotationManager.addRotationProvider(this);
    }

    @Override
    protected void onDisable() {
        active = false;
        this.stage = 0;
        this.pendingAction = null;
        this.pendingRotation = null;
        PrestigeSupport.silentRotationManager.removeRotationProvider(this);
    }

    @Override
    public Rotation provideRotation() {
        if (!this.isEnabled() || mc.player == null || mc.world == null) {
            return null;
        }

        if (this.pendingAction != null) {
            return this.pendingRotation;
        }

        this.pendingRotation = null;
        if (this.getBindKey() == 9998 && PlayerInteractions.isHoldingUsableItem()) {
            active = false;
            return null;
        }

        if (mc.currentScreen != null
            || !mc.isWindowFocused()
            || !this.delayTimer.hasElapsed(this.delayRolled)
            || this.stopInAir.isEnabled() && !mc.player.isOnGround()
            || mc.player.isUsingItem()
            || InventoryUtil.isHolding(Items.NETHERITE_SPEAR)
            || PrestigeSupport.silentRotationManager.hasActedThisTick()) {
            return null;
        }

        PlayerEntity enemy = TargetSelector.getClosestEnemy();
        if (enemy != null && enemy.distanceTo(mc.player) > 10.0F) {
            enemy = null;
        }

        if (this.stage == 0 && this.swordOnly.isEnabled() && !InventoryUtil.isSword(mc.player.getMainHandStack())) {
            this.toggle();
            return null;
        }

        HitResult hitResult = mc.crosshairTarget;
        if (hitResult == null) {
            return null;
        }

        if (!"None".equals(this.crystalMode.getCurrentOption())) {
            active = true;
        }

        switch (this.stage) {
            case 0:
                if (hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof EndCrystalEntity crystal) {
                    if ("None".equals(this.crystalMode.getCurrentOption())) {
                        active = false;
                        return null;
                    }

                    if (this.pauseOnKill.isEnabled() && DeathLootDetector.isLootNearby(crystal.getBlockPos())) {
                        active = false;
                        return null;
                    }

                    if (this.perfectTiming.isEnabled() && enemy != null && enemy.hurtTime != 0) {
                        return null;
                    }

                    this.queueAttack(crystal, () -> {
                        this.delayRolled = this.roll(this.delay);
                        this.delayTimer.reset();
                        active = false;
                    });
                    return null;
                }

                if (!(hitResult instanceof BlockHitResult blockHit) || !BlockUtil.isClickableHit(hitResult)) {
                    active = false;
                    return null;
                }

                if (mc.world.getBlockState(blockHit.getBlockPos()).getBlock() == Blocks.OBSIDIAN) {
                    this.stage = 2;
                    return null;
                }

                if (!InventoryUtil.switchToBlock(Blocks.OBSIDIAN)) {
                    active = false;
                    return null;
                }

                this.nextStage();
                return null;
            case 1:
                if (!(hitResult instanceof BlockHitResult blockHit)) {
                    active = false;
                    return null;
                }

                if (!BlockUtil.isClickableHit(hitResult)) {
                    active = false;
                    return null;
                }

                if (BlockUtil.hasEntityAt(blockHit.getBlockPos().offset(blockHit.getSide()))) {
                    active = false;
                    return null;
                }

                if (mc.world.getBlockState(blockHit.getBlockPos()).getBlock() == Blocks.OBSIDIAN) {
                    active = false;
                    this.nextStage();
                    return null;
                }

                if (BlockUtil.hasEntityAt(blockHit.getBlockPos())) {
                    active = false;
                    return null;
                }

                Integer obsidianSlot = InventoryUtil.findHotbarSlot(Blocks.OBSIDIAN.asItem());
                if (obsidianSlot == null) {
                    return null;
                }

                if (mc.player.getInventory().getSelectedSlot() != obsidianSlot) {
                    InventoryUtil.selectSlot(obsidianSlot);
                    return null;
                }

                this.queueAction(() -> {
                    PlayerInteractions.interactBlock(blockHit);
                    this.nextStage();
                }, null);
                return null;
            case 2:
                if (!InventoryUtil.switchToItem(Items.END_CRYSTAL)) {
                    return null;
                }

                this.nextStage();
                return null;
            case 3:
            case 5:
                if ("None".equals(this.crystalMode.getCurrentOption())) {
                    this.toggle();
                    return null;
                }

                if (this.stage == 5 && !this.delayTimer.hasElapsed(this.crystalDelayRolled)) {
                    return null;
                }

                if (!(hitResult instanceof BlockHitResult blockHit)) {
                    return null;
                }

                BlockPos blockPos = blockHit.getBlockPos();
                if (mc.world.getBlockState(blockPos).getBlock() != Blocks.OBSIDIAN) {
                    return null;
                }

                if (BlockUtil.hasEntityAt(blockPos)) {
                    return null;
                }

                Integer crystalSlot = InventoryUtil.findHotbarSlot(Items.END_CRYSTAL);
                if (crystalSlot == null) {
                    return null;
                }

                if (mc.player.getInventory().getSelectedSlot() != crystalSlot) {
                    InventoryUtil.selectSlot(crystalSlot);
                    return null;
                }

                this.queueAction(() -> {
                    PlayerInteractions.interactBlock(blockHit);
                    this.nextStage();
                }, null);
                return null;
            case 4:
                if (this.perfectTiming.isEnabled() && enemy != null && enemy.hurtTime != 0) {
                    return null;
                }

                if (mc.crosshairTarget.getType() == Type.ENTITY
                    && hitResult instanceof EntityHitResult entityHit
                    && entityHit.getEntity() instanceof EndCrystalEntity crystal) {
                    if (this.pauseOnKill.isEnabled() && DeathLootDetector.isLootNearby(crystal.getBlockPos())) {
                        return null;
                    }

                    this.queueAttack(crystal, () -> {
                        if (!"Double Tap".equals(this.crystalMode.getCurrentOption())
                            && !"Infinite".equals(this.crystalMode.getCurrentOption())) {
                            this.toggle();
                        } else {
                            this.nextStage();
                        }
                    });
                    return null;
                }

                if (this.silentRotations.isEnabled()) {
                    this.breakCrystalSilently();
                }
                return null;
            case 6:
                if (!this.delayTimer.hasElapsed(this.crystalDelayRolled)) {
                    return null;
                }

                if (this.perfectTiming.isEnabled() && enemy != null && enemy.hurtTime != 0) {
                    return null;
                }

                if (mc.crosshairTarget.getType() == Type.ENTITY
                    && hitResult instanceof EntityHitResult entityHit
                    && entityHit.getEntity() instanceof EndCrystalEntity crystal) {
                    if (this.pauseOnKill.isEnabled() && DeathLootDetector.isLootNearby(crystal.getBlockPos())) {
                        return null;
                    }

                    this.queueAttack(crystal, () -> {
                        if ("Infinite".equals(this.crystalMode.getCurrentOption())) {
                            this.stage = 5;
                            this.delayRolled = this.roll(this.delay);
                            this.crystalDelayRolled = this.roll(this.crystalDelay);
                            this.delayTimer.reset();
                        } else {
                            this.toggle();
                        }
                    });
                    return null;
                }

                if (this.silentRotations.isEnabled()) {
                    this.breakCrystalSilently();
                }
                return null;
            default:
                return null;
        }
    }

    @Override
    protected void onPreItemUse(ScaffoldTrade event) {
        if (this.getBindKey() == 9998
            && (mc.player.getMainHandStack().getItem().equals(Items.END_CRYSTAL)
                || mc.player.getMainHandStack().getItem().equals(Blocks.OBSIDIAN.asItem()))) {
            event.updateFlag();
        }
    }

    @Override
    public void onMovementPacketSent() {
        Runnable action = this.pendingAction;
        this.pendingAction = null;
        this.pendingRotation = null;
        if (action != null) {
            action.run();
        }
    }

    private void breakCrystalSilently() {
        BlockPos obsidianPos = null;
        if (mc.crosshairTarget.getType() == Type.BLOCK) {
            obsidianPos = ((BlockHitResult)mc.crosshairTarget).getBlockPos();
        }

        if (obsidianPos != null && mc.world.getBlockState(obsidianPos).getBlock() == Blocks.OBSIDIAN) {
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof EndCrystalEntity crystal) {
                    Vec3d crystalPos = PositionUtil.getEntityPos(crystal);
                    if (crystalPos.distanceTo(obsidianPos.up().toCenterPos()) < 1.0) {
                        double reach = mc.player.getEyePos().distanceTo(crystal.getBoundingBox().getCenter());
                        if (reach <= 3.0 && (!this.pauseOnKill.isEnabled() || !DeathLootDetector.isLootNearby(crystal.getBlockPos()))) {
                            this.queueAttack(crystal, () -> {
                                if ("Infinite".equals(this.crystalMode.getCurrentOption())) {
                                    this.stage = 5;
                                    this.delayRolled = this.roll(this.delay);
                                    this.delayTimer.reset();
                                } else if (this.stage != 6 && "Double Tap".equals(this.crystalMode.getCurrentOption())) {
                                    this.nextStage();
                                } else {
                                    this.toggle();
                                }
                            });
                            return;
                        }
                    }
                }
            }
        } else {
            this.toggle();
        }
    }

    private void queueAttack(EndCrystalEntity crystal, Runnable afterAttack) {
        Rotation rotation = this.silentRotations.isEnabled()
            ? PrestigeSupport.silentRotationManager.getRotationTo(crystal.getBoundingBox().getCenter())
            : null;
        this.queueAction(() -> {
            PlayerInteractions.attackEntity(crystal);
            afterAttack.run();
        }, rotation);
    }

    private void queueAction(Runnable action, Rotation rotation) {
        if (this.pendingAction == null) {
            this.pendingAction = action;
            this.pendingRotation = rotation;
        }
    }

    private void nextStage() {
        this.crystalDelayRolled = this.roll(this.crystalDelay);
        this.delayRolled = this.roll(this.delay);
        this.stage++;
        this.delayTimer.reset();
    }

    private float roll(RangeSetting setting) {
        return RandomUtil.randomFloat((float)setting.getMin(), (float)setting.getMax());
    }
}
