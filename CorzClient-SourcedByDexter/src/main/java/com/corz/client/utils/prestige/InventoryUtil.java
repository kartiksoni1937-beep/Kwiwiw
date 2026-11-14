package com.corz.client.utils.prestige;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public class InventoryUtil implements MinecraftAccess {
    public static boolean isSword(ItemStack stack) {
        return stack.getItem() == Items.DIAMOND_SWORD
            || stack.getItem() == Items.NETHERITE_SWORD
            || stack.getItem() == Items.IRON_SWORD
            || stack.getItem() == Items.GOLDEN_SWORD
            || stack.getItem() == Items.STONE_SWORD
            || stack.getItem() == Items.WOODEN_SWORD;
    }

    public static boolean isAxe(ItemStack stack) {
        return stack.getItem() == Items.DIAMOND_AXE
            || stack.getItem() == Items.NETHERITE_AXE
            || stack.getItem() == Items.IRON_AXE
            || stack.getItem() == Items.GOLDEN_AXE
            || stack.getItem() == Items.STONE_AXE
            || stack.getItem() == Items.WOODEN_AXE;
    }

    public static boolean isPickaxe(ItemStack stack) {
        return stack.getItem() == Items.DIAMOND_PICKAXE
            || stack.getItem() == Items.NETHERITE_PICKAXE
            || stack.getItem() == Items.IRON_PICKAXE
            || stack.getItem() == Items.GOLDEN_PICKAXE
            || stack.getItem() == Items.STONE_PICKAXE
            || stack.getItem() == Items.WOODEN_PICKAXE;
    }

    public static boolean clickSlot(int slotId, int button, SlotActionType actionType) {
        if (!(mc.currentScreen instanceof InventoryScreen)) {
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slotId, button, actionType, mc.player);
            return false;
        } else {
            mc.interactionManager.clickSlot(((InventoryScreen)mc.currentScreen).getScreenHandler().syncId, slotId, button, actionType, mc.player);
            return true;
        }
    }

    public static boolean swapSlot(int slotId, int hotbarSlot) {
        boolean inventoryOpen = mc.currentScreen instanceof InventoryScreen;
        clickSlot(slotId, hotbarSlot, SlotActionType.SWAP);
        return inventoryOpen;
    }

    public static boolean clickContainerSlot(int slotId, int button, SlotActionType actionType) {
        if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler)) {
            return false;
        } else {
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slotId, button, actionType, mc.player);
            return true;
        }
    }

    public static boolean switchToItem(Item item) {
        Integer slot = findHotbarSlot(item);
        if (slot == null) {
            return false;
        } else {
            selectSlot(slot);
            return true;
        }
    }

    public static boolean switchToBlock(Block block) {
        return switchToItem(block.asItem());
    }

    public static void selectSlot(int slot) {
        if (slot <= 8 && slot >= 0) {
            mc.player.getInventory().setSelectedSlot(slot);
        }
    }

    public static void runWithSlot(int slot, Runnable action, boolean switchBack) {
        int previousSlot = getSelectedSlot();
        selectSlot(slot);
        action.run();
        if (switchBack) {
            selectSlot(previousSlot);
        }
    }

    public static boolean runWithItem(Item item, Runnable action, boolean switchBack) {
        Integer slot = findHotbarSlot(item);
        if (slot == null) {
            return false;
        } else {
            runWithSlot(slot, action, switchBack);
            return true;
        }
    }

    public static boolean runWithBlock(Block block, Runnable action, boolean switchBack) {
        return runWithItem(block.asItem(), action, switchBack);
    }

    public static Integer findHotbarSlot(Item item) {
        for (int slot = 0; slot <= 8; slot++) {
            if (mc.player.getInventory().getStack(slot).getItem() == item) {
                return slot;
            }
        }

        return null;
    }

    public static Integer findSlotWithOtherItem(Item item) {
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.getItem() != item && stack.getItem() != Items.AIR) {
                return slot;
            }
        }

        return null;
    }

    public static Integer findMaceSlot() {
        for (int slot = 0; slot <= 8; slot++) {
            if (isMace(mc.player.getInventory().getStack(slot))) {
                return slot;
            }
        }

        return null;
    }

    public static Integer findBlockSlot(Block block) {
        for (int slot = 0; slot <= 8; slot++) {
            if (mc.player.getInventory().getStack(slot).getItem() == block.asItem()) {
                return slot;
            }
        }

        return null;
    }

    public static List findInventorySlots(Item item, boolean includeHotbar) {
        ArrayList slots = new ArrayList();

        for (int slot = includeHotbar ? 0 : 9; slot <= 35; slot++) {
            if (mc.player.getInventory().getStack(slot).getItem() == item) {
                slots.add(slot);
            }
        }

        return slots;
    }

    public static List collectInventorySlots(Item item, boolean includeHotbar) {
        ArrayList slots = new ArrayList();

        for (int slot = includeHotbar ? 0 : 9; slot <= 35; slot++) {
            if (mc.player.getInventory().getStack(slot).getItem() == item) {
                slots.add(slot);
            }
        }

        return slots;
    }

    public static boolean isHolding(Item item) {
        return mc.player.getMainHandStack().getItem() == item;
    }

    public static Item getHeldItem() {
        return mc.player.getMainHandStack().getItem();
    }

    public static boolean isHoldingOffhand(Item item) {
        return mc.player.getOffHandStack().getItem() == item;
    }

    public static int getSelectedSlot() {
        return mc.player.getInventory().getSelectedSlot();
    }

    public static Integer findSplashPotion(int fromSlot, int toSlot, RegistryEntry effect) {
        for (int slot = fromSlot; slot < toSlot; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.getItem() == Items.SPLASH_POTION && (effect == null || hasEffect(stack, effect))) {
                return slot;
            }
        }

        return null;
    }

    public static boolean hasEffect(ItemStack stack, RegistryEntry effect) {
        boolean found = false;
        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null) {
            return false;
        }

        for (StatusEffectInstance instance : contents.getEffects()) {
            if (instance.getEffectType() == effect) {
                found = true;
            }
        }

        return found;
    }

    public static boolean isMace(ItemStack stack) {
        return stack.getItem().equals(Items.MACE);
    }

    public static boolean isWindCharge(ItemStack stack) {
        return stack.getItem().equals(Items.WIND_CHARGE);
    }
}
