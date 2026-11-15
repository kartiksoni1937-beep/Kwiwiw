package com.corz.client.utils.prestige;

import com.corz.client.entity.ClientThreadUtils;
import com.corz.client.mixins.AccessorClientPlayerInteractionManager;
import com.corz.client.mixins.AccessorClientWorldPending;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.item.Items;
import net.minecraft.item.PotionItem;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;

public class PlayerInteractions implements MinecraftAccess {
    private static long lastAttackTick = Long.MIN_VALUE;
    private static long lastUseTick = Long.MIN_VALUE;

    public static void markUseAction() {
        lastUseTick = ClientThreadUtils.getTime();
    }

    public static boolean isUseActionThisTick() {
        return ClientThreadUtils.getTime() == lastUseTick;
    }

    public static boolean isAttackThisTick() {
        return ClientThreadUtils.getTime() == lastAttackTick;
    }

    public static boolean isAttackPending() {
        return PrestigeSupport.silentRotationManager.hasPendingAttack();
    }

    public static boolean interactBlock(BlockHitResult hitResult) {
        return interactBlock(hitResult, Hand.MAIN_HAND);
    }

    public static boolean interactBlock(BlockHitResult hitResult, Hand hand) {
        markUseAction();
        ActionResult result = mc.interactionManager.interactBlock(mc.player, hand, hitResult);
        if (!mc.player.isSpectator()) {
            PrestigeSupport.silentRotationManager.setActedThisTick(true);
        }

        if (result.isAccepted()) {
            mc.player.swingHand(hand);
            return true;
        } else {
            return false;
        }
    }

    public static boolean requestAttack(Entity target) {
        return requestAttack(target, shouldCrit(), null);
    }

    public static boolean requestAttack(Entity target, Runnable afterAttack) {
        return requestAttack(target, shouldCrit(), afterAttack);
    }

    public static boolean requestAttack(Entity target, boolean wantCrit, Runnable afterAttack) {
        return PrestigeSupport.silentRotationManager.queueAttack(target, wantCrit, afterAttack);
    }

    public static boolean shouldCrit() {
        return mc.player != null
            && mc.player.isSprinting()
            && !mc.player.isOnGround()
            && mc.player.fallDistance > 0.0F
            && !mc.player.isClimbing()
            && !mc.player.isSubmergedInWater()
            && !mc.player.isInLava();
    }

    public static void prepareCritSprintReset() {
        if (mc.player != null && mc.player.isSprinting()) {
            // Vanilla sends STOP_SPRINTING at the start of this tick's movement packet, before
            // the delayed attack packet on the following tick.
            mc.player.setSprinting(false);
        }
    }

    public static boolean attackEntity(Entity target) {
        if (mc.player == null || mc.getNetworkHandler() == null || mc.interactionManager == null || target == null) {
            return false;
        }

        long tick = ClientThreadUtils.getTime();
        if (tick == lastAttackTick || tick == lastUseTick) {
            return false;
        }
        lastAttackTick = tick;

        // Reuse vanilla's slot ledger instead of keeping a second one. This orders a late
        // slot change before the attack without sending a duplicate slot packet.
        ((AccessorClientPlayerInteractionManager) mc.interactionManager).corzSyncSelectedSlot();

        mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

        if (!mc.player.isSpectator()) {
            // Preserve vanilla's local attack effects after both packets; this sends no second swing or interact packet.
            mc.player.attack(target);
            mc.player.resetTicksSince();
        }

        PrestigeSupport.silentRotationManager.setActedThisTick(true);
        return true;
    }

    public static boolean interactItem(Hand hand) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) {
            return false;
        }

        markUseAction();
        if (mc.interactionManager != null) {
            ((AccessorClientPlayerInteractionManager) mc.interactionManager).corzSyncSelectedSlot();
        }

        Rotation rotation = PrestigeSupport.silentRotationManager.getLockedRotation();
        if (rotation == null) {
            rotation = PrestigeSupport.silentRotationManager.getActiveRotation();
        }

        float yaw = rotation != null ? rotation.getYaw() : mc.player.getYaw();
        float pitch = rotation != null ? rotation.getPitch() : mc.player.getPitch();

        PendingUpdateManager pending = ((AccessorClientWorldPending) mc.world).corzPendingUpdates();
        if (pending != null) {
            pending = pending.incrementSequence();
            try {
                mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(hand, pending.getSequence(), yaw, pitch));
            } finally {
                pending.close();
            }
        } else {
            mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(hand, 0, yaw, pitch));
        }

        if (!mc.player.isSpectator()) {
            PrestigeSupport.silentRotationManager.setActedThisTick(true);
        }

        return true;
    }

    public static void startGliding() {
        mc.player.startGliding();
    }

    public static float getReachDistance(boolean creative) {
        return creative ? 5.0F : 4.5F;
    }

    public static float getReachDistance() {
        return getReachDistance(mc.player.isCreative());
    }

    public static boolean isBlocking() {
        return mc.player.isUsingItem()
            && (mc.player.getMainHandStack().getItem() == Items.SHIELD || mc.player.getOffHandStack().getItem() == Items.SHIELD && !isHoldingUsableItem());
    }

    public static boolean isHoldingUsableItem() {
        return mc.player.getMainHandStack().get(DataComponentTypes.FOOD) != null
            ? true
            : mc.player.getMainHandStack().getItem() instanceof PotionItem
                || mc.player.getMainHandStack().getItem() == Items.SNOWBALL
                || mc.player.getMainHandStack().getItem() == Items.EGG
                || mc.player.getMainHandStack().getItem() == Items.ENDER_PEARL
                || mc.player.getMainHandStack().getItem() == Items.EXPERIENCE_BOTTLE
                || mc.player.getMainHandStack().getItem() == Items.SPLASH_POTION
                || mc.player.getMainHandStack().getItem() == Items.LINGERING_POTION;
    }
}
