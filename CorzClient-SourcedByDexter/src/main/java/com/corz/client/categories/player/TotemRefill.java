package com.corz.client.categories.player;

import ai.auth.Module;
import com.corz.client.config.BooleanSetting;
import com.corz.client.config.ConfigSetting;
import com.corz.client.config.DoubleSetting;
import com.corz.client.config.EnumSetting;
import com.corz.client.config.IntegerSetting;
import com.corz.client.mixins.AccessorHandledScreen;
import com.corz.client.utils.IconsPngType;
import com.corz.client.utils.MillisCooldownTimer;
import com.corz.client.utils.prestige.InventoryClickCompat;
import com.corz.client.utils.prestige.InventoryClickType;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;

public class TotemRefill extends Module {
    private static final String MODE_CURSOR = "Cursor";
    private static final String MODE_HOVER = "Hover";

    private final EnumSetting mode = new EnumSetting("Mode", "How totems are selected", MODE_CURSOR, MODE_CURSOR, MODE_HOVER);
    private final BooleanSetting autoOpen = new BooleanSetting("Auto Open", "Open the inventory when a totem needs moving", false);
    private final BooleanSetting autoClose = new BooleanSetting("Auto Close", "Close the inventory after moving a totem", true);
    private final BooleanSetting autoOffhand = new BooleanSetting("Auto Offhand", "Move a totem into your offhand", true);
    private final BooleanSetting moveToHotbar = new BooleanSetting("Move to Hotbar", "Also keep a totem in the selected hotbar slot", false);
    private final IntegerSetting hotbarSlot = new IntegerSetting("Hotbar Slot", "Hotbar slot used by Move to Hotbar", 9, 1, 9);
    private final DoubleSetting cursorTime = new DoubleSetting("Cursor Time", "Delay between cursor actions in milliseconds", 50.0, 25.0, 500.0, 1.0);

    private final MillisCooldownTimer timer = new MillisCooldownTimer();

    public TotemRefill() {
        super("Player", IconsPngType.MISC);
        this.addSettings(new ConfigSetting[]{
                this.mode, this.autoOpen, this.autoClose, this.autoOffhand,
                this.moveToHotbar, this.hotbarSlot, this.cursorTime
        });
    }

    @Override
    protected void onReset() {
        if (mc.player == null || mc.world == null || mc.player.isDead()) return;
        if (!this.timer.hasElapsed((long) this.cursorTime.getValue())) return;

        int hotbarIndex = Math.max(0, Math.min(8, this.hotbarSlot.getValue() - 1));
        boolean offhandHas = mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
        boolean hotbarHas = mc.player.getInventory().getStack(hotbarIndex).isOf(Items.TOTEM_OF_UNDYING);
        boolean needOffhand = this.autoOffhand.isEnabled() && !offhandHas;
        boolean needHotbar = this.moveToHotbar.isEnabled() && !hotbarHas;
        if (!needOffhand && !needHotbar) return;

        if (mc.currentScreen instanceof InventoryScreen inventoryScreen) {
            if (this.mode.getCurrentOption().equals(MODE_HOVER)) {
                handleHover(inventoryScreen, hotbarIndex);
            } else {
                handleCursor(inventoryScreen, hotbarIndex);
            }
            return;
        }

        if (this.mode.getCurrentOption().equals(MODE_CURSOR) && this.autoOpen.isEnabled()
                && hasTotemInInventory(hotbarIndex)) {
            mc.setScreen(new InventoryScreen(mc.player));
        }
    }

    private void handleHover(InventoryScreen screen, int hotbarIndex) {
        if (!(screen instanceof AccessorHandledScreen accessor)) return;
        Slot focused = accessor.corzFocusedSlot();
        if (focused == null || !focused.getStack().isOf(Items.TOTEM_OF_UNDYING)) return;
        applySlot(screen, focused, hotbarIndex);
    }

    private void handleCursor(InventoryScreen screen, int hotbarIndex) {
        for (int menuSlot = 9; menuSlot < 45; menuSlot++) {
            Slot slot = screen.getScreenHandler().getSlot(menuSlot);
            if (slot == null || !slot.getStack().isOf(Items.TOTEM_OF_UNDYING)) continue;
            if (slot.getIndex() == 40 || slot.getIndex() == hotbarIndex) continue;
            if (applySlot(screen, slot, hotbarIndex)) {
                return;
            }
        }
    }

    private boolean applySlot(InventoryScreen screen, Slot slot, int hotbarIndex) {
        int menuSlot = screen.getScreenHandler().slots.indexOf(slot);
        if (menuSlot == -1) return false;

        boolean needOffhand = this.autoOffhand.isEnabled() && !mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
        boolean needHotbar = this.moveToHotbar.isEnabled()
                && !mc.player.getInventory().getStack(hotbarIndex).isOf(Items.TOTEM_OF_UNDYING);

        if (needOffhand) {
            InventoryClickCompat.handleClick(0, menuSlot, 40, InventoryClickType.SWAP, mc.player);
        } else if (needHotbar) {
            InventoryClickCompat.handleClick(0, menuSlot, hotbarIndex, InventoryClickType.SWAP, mc.player);
        } else {
            return false;
        }

        this.timer.restart();
        boolean offhandDone = !this.autoOffhand.isEnabled() || mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
        boolean hotbarDone = !this.moveToHotbar.isEnabled()
                || mc.player.getInventory().getStack(hotbarIndex).isOf(Items.TOTEM_OF_UNDYING);
        if (this.autoClose.isEnabled() && offhandDone && hotbarDone) {
            mc.player.closeHandledScreen();
        }
        return true;
    }

    private boolean hasTotemInInventory(int excludedHotbarSlot) {
        for (int i = 0; i < 40; i++) {
            if (i == excludedHotbarSlot) continue;
            if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                return true;
            }
        }
        return false;
    }
}
