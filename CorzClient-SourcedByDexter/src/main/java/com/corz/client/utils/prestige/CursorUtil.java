package com.corz.client.utils.prestige;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.Window;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public final class CursorUtil {

    public record ScreenInfo(ScreenHandler handler, int bgWidth, int bgHeight) {}

    public record SlotResult(int slotIndex, double absX, double absY, double distance) {}

    /**
     * Returns screen info for InventoryScreen or CreativeModeInventoryScreen
     */
    public static ScreenInfo getScreenInfo(Object screen) {
        if (screen instanceof InventoryScreen inv) {
            return new ScreenInfo(inv.getScreenHandler(), 176, 166);
        }
        if (screen instanceof CreativeInventoryScreen cre) {
            return new ScreenInfo(cre.getScreenHandler(), 195, 136);
        }
        return null;
    }

    public static boolean isInventoryScreen(Object screen) {
        return screen instanceof InventoryScreen || screen instanceof CreativeInventoryScreen;
    }

    /**
     * Converts a slot position to absolute screen pixel coordinates.
     */
    public static double[] slotToAbsPos(Slot slot, int screenX, int screenY, Window window) {
        double scaledX = screenX + slot.x + 8.0;
        double scaledY = screenY + slot.y + 8.0;
        double absX = scaledX * window.getWidth() / window.getScaledWidth();
        double absY = scaledY * window.getHeight() / window.getScaledHeight();
        return new double[]{absX, absY};
    }

    /**
     * Returns the top-left corner of the centered inventory background in GUI-scaled coordinates.
     */
    public static int[] getScreenOrigin(ScreenInfo info, Window window) {
        int x = (window.getScaledWidth() - info.bgWidth()) / 2;
        int y = (window.getScaledHeight() - info.bgHeight()) / 2;
        return new int[]{x, y};
    }

    /**
     * Returns the maximum distance from the mouse to any valid inventory slot (for duration scaling).
     */
    public static double maxSlotDistance(List<Slot> slots, boolean isCreative, int screenX, int screenY, Window window, double mouseX, double mouseY) {
        return maxSlotDistance(slots, isCreative, screenX, screenY, window, mouseX, mouseY, null);
    }

    public static double maxSlotDistance(List<Slot> slots, boolean isCreative, int screenX, int screenY, Window window, double mouseX, double mouseY, Predicate<Slot> slotFilter) {
        double max = 0.0;
        for (Slot slot : slots) {
            if (!isValidSlot(slot, isCreative)) continue;
            if (slotFilter != null && !slotFilter.test(slot)) continue;
            double[] pos = slotToAbsPos(slot, screenX, screenY, window);
            double d = Math.hypot(pos[0] - mouseX, pos[1] - mouseY);
            if (d > max) max = d;
        }
        return max;
    }

    /**
     * Finds the nearest slot containing the given item.
     */
    public static SlotResult findNearestSlotWithItem(List<Slot> slots, Item item, boolean isCreative, int screenX, int screenY, Window window, double mouseX, double mouseY) {
        return findNearestSlotWithItem(slots, item, isCreative, screenX, screenY, window, mouseX, mouseY, null);
    }

    public static SlotResult findNearestSlotWithItem(List<Slot> slots, Item item, boolean isCreative, int screenX, int screenY, Window window, double mouseX, double mouseY, Predicate<Slot> slotFilter) {
        double minDist = Double.MAX_VALUE;
        SlotResult best = null;

        for (int menuSlot = 0; menuSlot < slots.size(); menuSlot++) {
            Slot slot = slots.get(menuSlot);
            if (!isValidSlot(slot, isCreative)) continue;
            if (slotFilter != null && !slotFilter.test(slot)) continue;
            if (!slot.getStack().isOf(item)) continue;

            double[] pos = slotToAbsPos(slot, screenX, screenY, window);
            double dist = Math.hypot(pos[0] - mouseX, pos[1] - mouseY);
            if (dist < minDist) {
                minDist = dist;
                best = new SlotResult(menuSlot, pos[0], pos[1], dist);
            }
        }
        return best;
    }

    /**
     * Computes cursor animation duration in ms, scaled by how far the target is relative to the farthest slot.
     */
    public static long computeAnimDuration(double distance, double maxDistance, float minMs, float maxMs) {
        if (maxDistance <= 0.0) return Math.max(1L, Math.round(minMs));
        double ratio = Math.max(0.0, Math.min(1.0, distance / maxDistance));
        double mapped = minMs + ratio * (maxMs - minMs);
        return Math.max(1L, Math.round(mapped));
    }

    private static boolean isValidSlot(Slot slot, boolean isCreative) {
        if (isCreative && !(slot.inventory instanceof PlayerInventory)) return false;
        if (slot.inventory instanceof PlayerInventory && slot.getIndex() == 40) return false;
        return true;
    }
}
