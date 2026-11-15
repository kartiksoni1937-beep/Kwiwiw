package com.corz.client.utils.prestige;

import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.sync.ItemStackHash;

public final class InventoryClickCompat implements MinecraftAccess {

    private InventoryClickCompat() {
    }

    public static void handleClick(int containerId, int slot, int button, InventoryClickType clickType) {
        if (mc.player == null) return;
        handleClick(containerId, slot, button, clickType, mc.player);
    }

    public static void handleClick(int containerId, int slot, int button, InventoryClickType clickType, PlayerEntity player) {
        if (player == null) return;
        if (mc.interactionManager == null) return;

        mc.interactionManager.clickSlot(containerId, slot, button, toSlotActionType(clickType), player);
    }

    public static void sendClickPacket(int containerId, int slot, int button, InventoryClickType clickType) {
        if (mc.getNetworkHandler() == null || mc.player == null) return;

        ScreenHandler handler = mc.player.currentScreenHandler;
        mc.getNetworkHandler().sendPacket(new ClickSlotC2SPacket(
            containerId,
            handler.getRevision(),
            (short) slot,
            (byte) button,
            toSlotActionType(clickType),
            Int2ObjectMaps.emptyMap(),
            ItemStackHash.EMPTY
        ));
    }

    public static InventoryClickType fromVanilla(Object clickType) {
        if (!(clickType instanceof Enum<?> enumValue)) return null;

        try {
            return InventoryClickType.valueOf(enumValue.name());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static String describePacketClickType(ClickSlotC2SPacket clickPacket) {
        return clickPacket.actionType().name();
    }

    private static SlotActionType toSlotActionType(InventoryClickType clickType) {
        return switch (clickType) {
            case PICKUP -> SlotActionType.PICKUP;
            case QUICK_MOVE -> SlotActionType.QUICK_MOVE;
            case SWAP -> SlotActionType.SWAP;
            case CLONE -> SlotActionType.CLONE;
            case THROW -> SlotActionType.THROW;
            case QUICK_CRAFT -> SlotActionType.QUICK_CRAFT;
            case PICKUP_ALL -> SlotActionType.PICKUP_ALL;
        };
    }
}
