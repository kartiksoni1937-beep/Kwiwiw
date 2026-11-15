package com.corz.client.utils.prestige;

import net.minecraft.item.Item;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;

public final class ContainerUtils implements MinecraftAccess {

    public static void click(int slot, InventoryClickType clickType) {
        if (mc.player == null || mc.interactionManager == null) return;
        var container = mc.player.currentScreenHandler;
        InventoryClickCompat.handleClick(container.syncId, slot, 0, clickType, mc.player);
    }

    public static void clickPacket(int containerId, int slot, InventoryClickType clickType) {
        InventoryClickCompat.sendClickPacket(containerId, slot, 0, clickType);
    }

    public static void closeContainer(int containerId) {
        if (mc.getNetworkHandler() == null) return;
        mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(containerId));
    }

    public static void quickMove(int slot) {
        click(slot, InventoryClickType.QUICK_MOVE);
    }

    public static void pickup(int slot) {
        click(slot, InventoryClickType.PICKUP);
    }

    public static void quickMoveAll(Item item) {
        if (mc.player == null) return;
        var container = mc.player.currentScreenHandler;
        for (int i = 0; i < container.slots.size(); i++) {
            if (container.slots.get(i).getStack().getItem() == item) {
                quickMove(i);
            }
        }
    }

    public static int getContainerSize() {
        if (mc.player == null) return 0;
        return mc.player.currentScreenHandler.slots.size() - 36;
    }
}
