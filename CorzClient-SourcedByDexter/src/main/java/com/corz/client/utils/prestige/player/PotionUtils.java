package com.corz.client.utils.prestige.player;

import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;

public final class PotionUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private PotionUtils() {
    }

    public static boolean isSplashPotion(ItemStack stack) {
        return stack.isOf(Items.SPLASH_POTION);
    }

    public static boolean hasEffect(ItemStack stack, RegistryEntry<StatusEffect> effect) {
        if (!isSplashPotion(stack)) return false;
        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null) return false;
        for (StatusEffectInstance instance : contents.getEffects()) {
            if (instance.getEffectType().equals(effect)) {
                return true;
            }
        }
        return false;
    }

    public static int findPotionInHotbar(RegistryEntry<StatusEffect> effect) {
        if (mc.player == null) return -1;
        PlayerInventory inventory = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (hasEffect(inventory.getStack(i), effect)) {
                return i;
            }
        }
        return -1;
    }

    public static boolean hasPotionInOffhand(RegistryEntry<StatusEffect> effect) {
        return mc.player != null && hasEffect(mc.player.getOffHandStack(), effect);
    }

    public static int findInstantHealthPotionInHotbar() {
        return findPotionInHotbar(StatusEffects.INSTANT_HEALTH);
    }

    public static int findSpeedPotionInHotbar() {
        return findPotionInHotbar(StatusEffects.SPEED);
    }

    public static int findStrengthPotionInHotbar() {
        return findPotionInHotbar(StatusEffects.STRENGTH);
    }

    public static boolean hasInstantHealthPotionInOffhand() {
        return hasPotionInOffhand(StatusEffects.INSTANT_HEALTH);
    }

    public static boolean hasSpeedPotionInOffhand() {
        return hasPotionInOffhand(StatusEffects.SPEED);
    }

    public static boolean hasStrengthPotionInOffhand() {
        return hasPotionInOffhand(StatusEffects.STRENGTH);
    }
}
