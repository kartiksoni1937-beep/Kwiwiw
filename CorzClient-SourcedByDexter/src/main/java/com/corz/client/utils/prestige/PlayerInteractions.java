package com.corz.client.utils.prestige;

import com.corz.client.mixins.AccessorClientWorldPending;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.item.Items;
import net.minecraft.item.PotionItem;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;

public class PlayerInteractions implements MinecraftAccess {
    private static int lastSyncedSlot = -1;

    public static boolean interactBlock(BlockHitResult hitResult) {
        return interactBlock(hitResult, Hand.MAIN_HAND);
    }

    public static boolean interactBlock(BlockHitResult hitResult, Hand hand) {
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

    public static void attackEntity(Entity target) {
        if (mc.player == null || mc.getNetworkHandler() == null || target == null) {
            return;
        }

        syncSelectedSlot();

        // Keep the rotation packet first by only attacking after sendMovementPackets has flushed it.
        mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

        if (!mc.player.isSpectator()) {
            // Preserve vanilla's local attack effects after both packets; this sends no second swing or interact packet.
            mc.player.attack(target);
            mc.player.resetTicksSince();
        }

        PrestigeSupport.silentRotationManager.setActedThisTick(true);
    }

    public static boolean interactItem(Hand hand) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) {
            return false;
        }

        syncSelectedSlot();

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

    private static void syncSelectedSlot() {
        if (mc.player == null || mc.getNetworkHandler() == null) {
            lastSyncedSlot = -1;
            return;
        }

        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        if (selectedSlot != lastSyncedSlot) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(selectedSlot));
            lastSyncedSlot = selectedSlot;
        }
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
