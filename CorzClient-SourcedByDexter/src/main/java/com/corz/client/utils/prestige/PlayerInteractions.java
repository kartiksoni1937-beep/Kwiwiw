package com.corz.client.utils.prestige;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.item.Items;
import net.minecraft.item.PotionItem;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;

public class PlayerInteractions implements MinecraftAccess {
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
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        PrestigeSupport.silentRotationManager.setActedThisTick(true);
    }

    public static boolean interactItem(Hand hand) {
        ActionResult result = mc.interactionManager.interactItem(mc.player, hand);
        if (!mc.player.isSpectator()) {
            PrestigeSupport.silentRotationManager.setActedThisTick(true);
        }

        if (result.isAccepted()) {
            mc.player.swingHand(Hand.MAIN_HAND);
            return true;
        } else {
            return false;
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
