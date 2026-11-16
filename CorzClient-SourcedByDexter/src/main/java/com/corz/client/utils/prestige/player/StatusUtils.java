package com.corz.client.utils.prestige.player;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;

public final class StatusUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private StatusUtils() {
    }

    public static boolean hasStatusEffect(RegistryEntry<StatusEffect> effect) {
        return mc.player != null && mc.player.hasStatusEffect(effect);
    }

    public static StatusEffectInstance getStatusEffectInstance(RegistryEntry<StatusEffect> effect) {
        return mc.player == null ? null : mc.player.getStatusEffect(effect);
    }

    public static boolean hasSpeed() {
        return hasStatusEffect(StatusEffects.SPEED);
    }

    public static boolean hasSlowness() {
        return hasStatusEffect(StatusEffects.SLOWNESS);
    }

    public static boolean hasHaste() {
        return hasStatusEffect(StatusEffects.HASTE);
    }

    public static boolean hasMiningFatigue() {
        return hasStatusEffect(StatusEffects.MINING_FATIGUE);
    }

    public static boolean hasStrength() {
        return hasStatusEffect(StatusEffects.STRENGTH);
    }

    public static boolean hasJumpBoost() {
        return hasStatusEffect(StatusEffects.JUMP_BOOST);
    }

    public static boolean hasRegeneration() {
        return hasStatusEffect(StatusEffects.REGENERATION);
    }

    public static boolean hasResistance() {
        return hasStatusEffect(StatusEffects.RESISTANCE);
    }

    public static boolean hasFireResistance() {
        return hasStatusEffect(StatusEffects.FIRE_RESISTANCE);
    }

    public static boolean hasWaterBreathing() {
        return hasStatusEffect(StatusEffects.WATER_BREATHING);
    }

    public static boolean hasInvisibility() {
        return hasStatusEffect(StatusEffects.INVISIBILITY);
    }

    public static boolean hasNightVision() {
        return hasStatusEffect(StatusEffects.NIGHT_VISION);
    }

    public static boolean hasWeakness() {
        return hasStatusEffect(StatusEffects.WEAKNESS);
    }

    public static boolean hasPoison() {
        return hasStatusEffect(StatusEffects.POISON);
    }

    public static boolean hasWither() {
        return hasStatusEffect(StatusEffects.WITHER);
    }

    public static boolean hasAbsorption() {
        return hasStatusEffect(StatusEffects.ABSORPTION);
    }

    public static boolean hasSlowFalling() {
        return hasStatusEffect(StatusEffects.SLOW_FALLING);
    }

    public static boolean hasLevitation() {
        return hasStatusEffect(StatusEffects.LEVITATION);
    }

    public static boolean hasDarkness() {
        return hasStatusEffect(StatusEffects.DARKNESS);
    }
}
