package com.corz.client.utils.prestige.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public final class InventoryUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Random RANDOM = new Random();

    private InventoryUtils() {
    }

    public static void setInvSlot(int slot) {
        setInvSlot(slot, false);
    }

    public static void setInvSlot(int slot, boolean inputSimulation) {
        if (mc.player == null || slot < 0 || slot > 8) return;
        PlayerInventory inventory = mc.player.getInventory();
        if (inventory.getSelectedSlot() != slot) {
            inventory.setSelectedSlot(slot);
        }
    }

    public static int getSelectedSlot() {
        return mc.player == null ? -1 : mc.player.getInventory().getSelectedSlot();
    }

    public static int findItemWithPredicateInHotbar(Predicate<ItemStack> predicate) {
        if (mc.player == null) return -1;
        PlayerInventory inventory = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (predicate.test(inventory.getStack(i))) {
                return i;
            }
        }
        return -1;
    }

    public static int findHotbarSlot(Item item) {
        return findItemWithPredicateInHotbar(stack -> stack.isOf(item));
    }

    public static boolean hasItemInHotbar(Item item) {
        return findHotbarSlot(item) != -1;
    }

    public static boolean selectItemFromHotbar(Item item) {
        int slot = findHotbarSlot(item);
        if (slot == -1) return false;
        setInvSlot(slot);
        return true;
    }

    public static int findTotemSlot() {
        if (mc.player == null) return -1;
        PlayerInventory inventory = mc.player.getInventory();
        for (int i = 9; i < 36; i++) {
            if (inventory.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                return i;
            }
        }
        return -1;
    }

    public static int findRandomTotemSlot() {
        if (mc.player == null) return -1;
        List<Integer> slots = new ArrayList<>();
        PlayerInventory inventory = mc.player.getInventory();
        for (int i = 9; i < 36; i++) {
            if (inventory.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                slots.add(i);
            }
        }
        return slots.isEmpty() ? -1 : slots.get(RANDOM.nextInt(slots.size()));
    }

    public static int findEmptyHotbarSlot() {
        if (mc.player == null) return -1;
        PlayerInventory inventory = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inventory.getStack(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    public static int findChestplateSlot() {
        if (mc.player == null) return -1;
        PlayerInventory inventory = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (isChestplate(inventory.getStack(i))) {
                return i;
            }
        }
        return -1;
    }

    public static boolean isChestplate(ItemStack stack) {
        return stack.isOf(Items.NETHERITE_CHESTPLATE)
                || stack.isOf(Items.DIAMOND_CHESTPLATE)
                || stack.isOf(Items.IRON_CHESTPLATE)
                || stack.isOf(Items.GOLDEN_CHESTPLATE)
                || stack.isOf(Items.CHAINMAIL_CHESTPLATE)
                || stack.isOf(Items.LEATHER_CHESTPLATE);
    }

    public static boolean isHoldingChestplate() {
        return mc.player != null && isChestplate(mc.player.getMainHandStack());
    }

    public static boolean isWearingElytra() {
        return mc.player != null && mc.player.getInventory().getStack(38).isOf(Items.ELYTRA);
    }

    public static boolean isHoldingItem(Item item) {
        return mc.player != null && mc.player.getMainHandStack().isOf(item);
    }

    public static boolean isOffhandHoldingItem(Item item) {
        return mc.player != null && mc.player.getOffHandStack().isOf(item);
    }

    public static boolean isHoldingAnyHand(Item item) {
        return isHoldingItem(item) || isOffhandHoldingItem(item);
    }

    public static ItemStack getMainHandItem() {
        return mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandStack();
    }
}
