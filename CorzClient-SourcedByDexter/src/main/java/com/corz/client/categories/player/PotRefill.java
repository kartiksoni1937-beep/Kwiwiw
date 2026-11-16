package com.corz.client.categories.player;

import ai.auth.Module;
import com.corz.client.config.BooleanSetting;
import com.corz.client.config.ConfigSetting;
import com.corz.client.config.DoubleSetting;
import com.corz.client.config.EnumSetting;
import com.corz.client.mixins.AccessorHandledScreen;
import com.corz.client.utils.IconsPngType;
import com.corz.client.utils.MillisCooldownTimer;
import com.corz.client.utils.prestige.InventoryClickCompat;
import com.corz.client.utils.prestige.InventoryClickType;
import com.corz.client.utils.prestige.player.InventoryUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.Slot;
import org.lwjgl.glfw.GLFW;

public class PotRefill extends Module {
    private static final String MODE_HOVER = "Hover";
    private static final String MODE_SHIFT_LMB = "ShiftLMB";
    private static final String MODE_AUTO = "Auto";

    private final EnumSetting mode = new EnumSetting("Mode", "How potions are moved into your hotbar",
            MODE_HOVER, MODE_HOVER, MODE_SHIFT_LMB, MODE_AUTO);
    private final BooleanSetting refillBuffPotions = new BooleanSetting("Refill Buff Potions", "Also refill missing buff potions", true);
    private final DoubleSetting delay = new DoubleSetting("Delay", "Delay between refills in milliseconds", 50.0, 0.0, 400.0, 5.0);
    private final MillisCooldownTimer refillTimer = new MillisCooldownTimer();

    public PotRefill() {
        super("Player", IconsPngType.MISC);
        this.addSettings(new ConfigSetting[]{this.mode, this.refillBuffPotions, this.delay});
    }

    @Override
    protected void onReset() {
        if (mc.player == null || mc.currentScreen == null) return;
        if (!(mc.currentScreen instanceof InventoryScreen inventoryScreen)) return;

        String currentMode = this.mode.getCurrentOption();
        if (currentMode.equals(MODE_HOVER)) {
            handleHoverRefill(inventoryScreen);
        } else if (currentMode.equals(MODE_SHIFT_LMB)) {
            if (GLFW.glfwGetKey(mc.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                    && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) {
                handleHoverRefill(inventoryScreen);
            }
        } else if (currentMode.equals(MODE_AUTO)) {
            handleAutoRefill(inventoryScreen);
        }
    }

    private void handleHoverRefill(InventoryScreen inventoryScreen) {
        if (!(inventoryScreen instanceof AccessorHandledScreen accessor)) return;
        Slot focusedSlot = accessor.corzFocusedSlot();
        if (focusedSlot == null) return;
        applyRefill(inventoryScreen, focusedSlot);
    }

    private void handleAutoRefill(InventoryScreen inventoryScreen) {
        if (!this.refillTimer.hasElapsed((long) this.delay.getValue())) return;
        for (int invSlot = 9; invSlot < 45; invSlot++) {
            Slot slot = inventoryScreen.getScreenHandler().getSlot(invSlot);
            if (slot == null) continue;
            if (tryRefill(inventoryScreen, slot)) {
                this.refillTimer.restart();
                return;
            }
        }
    }

    private void applyRefill(InventoryScreen inventoryScreen, Slot focusedSlot) {
        if (this.refillTimer.hasElapsed((long) this.delay.getValue()) && tryRefill(inventoryScreen, focusedSlot)) {
            this.refillTimer.restart();
        }
    }

    private boolean tryRefill(InventoryScreen inventoryScreen, Slot slot) {
        ItemStack stack = slot.getStack();
        if (!shouldRefillPotion(stack) || isInHotbar(slot.getIndex())) return false;

        int emptySlot = InventoryUtils.findEmptyHotbarSlot();
        if (emptySlot == -1) return false;
        int menuSlot = inventoryScreen.getScreenHandler().slots.indexOf(slot);
        if (menuSlot == -1) return false;

        InventoryClickCompat.handleClick(0, menuSlot, emptySlot, InventoryClickType.SWAP, mc.player);
        return true;
    }

    private boolean shouldRefillPotion(ItemStack stack) {
        if (!stack.isOf(Items.SPLASH_POTION) || mc.player == null) return false;
        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null) return false;

        for (StatusEffectInstance effect : contents.getEffects()) {
            RegistryEntry<StatusEffect> effectHolder = effect.getEffectType();
            StatusEffect statusEffect = effectHolder.value();

            if (statusEffect.isInstant() && statusEffect.isBeneficial()) {
                return true;
            }
            if (!this.refillBuffPotions.isEnabled()) continue;
            if (statusEffect.isBeneficial()
                    && !statusEffect.isInstant()
                    && !mc.player.hasStatusEffect(effectHolder)
                    && !hasBuffPotionInHotbar(effectHolder)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasBuffPotionInHotbar(RegistryEntry<StatusEffect> targetEffect) {
        if (mc.player == null) return false;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isOf(Items.SPLASH_POTION)) continue;
            PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
            if (contents == null) continue;
            for (StatusEffectInstance effect : contents.getEffects()) {
                RegistryEntry<StatusEffect> effectHolder = effect.getEffectType();
                StatusEffect statusEffect = effectHolder.value();
                if (statusEffect.isBeneficial() && !statusEffect.isInstant() && effectHolder.equals(targetEffect)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isInHotbar(int containerSlot) {
        return containerSlot >= 0 && containerSlot <= 8;
    }
}
