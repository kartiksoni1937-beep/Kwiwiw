package com.corz.client.categories.player;

import ai.auth.Module;
import com.corz.client.config.BooleanSetting;
import com.corz.client.config.ConfigSetting;
import com.corz.client.utils.IconsPngType;
import com.corz.client.utils.prestige.InventoryClickCompat;
import com.corz.client.utils.prestige.InventoryClickType;
import com.corz.client.utils.prestige.player.InventoryUtils;
import net.minecraft.item.Items;

public class TotemOffhandModule extends Module {
    private final BooleanSetting mustHold = new BooleanSetting("Must Hold", "Only swap the totem you are holding", true);
    private final BooleanSetting hotbarOnly = new BooleanSetting("Hotbar Only", "Do not search your main inventory", false);

    private int lastSwapTick = -1000;

    public TotemOffhandModule() {
        super("Player", IconsPngType.MISC);
        this.addSettings(new ConfigSetting[]{this.mustHold, this.hotbarOnly});
    }

    @Override
    protected void onEnable() {
        this.lastSwapTick = -1000;
    }

    @Override
    protected void onReset() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (InventoryUtils.isOffhandHoldingItem(Items.TOTEM_OF_UNDYING)) return;

        int age = mc.player.age;
        if (age - this.lastSwapTick < 2) return;

        if (this.mustHold.isEnabled()) {
            if (!InventoryUtils.isHoldingItem(Items.TOTEM_OF_UNDYING)) return;
            swapSlot(mc.player.getInventory().getSelectedSlot() + 36, age);
            return;
        }

        int hotbarSlot = InventoryUtils.findHotbarSlot(Items.TOTEM_OF_UNDYING);
        if (hotbarSlot != -1) {
            swapSlot(hotbarSlot + 36, age);
            return;
        }

        if (this.hotbarOnly.isEnabled()) return;

        int invSlot = InventoryUtils.findTotemSlot();
        if (invSlot != -1) {
            swapSlot(invSlot, age);
        }
    }

    private void swapSlot(int containerSlot, int tick) {
        InventoryClickCompat.handleClick(0, containerSlot, 40, InventoryClickType.SWAP, mc.player);
        this.lastSwapTick = tick;
    }
}
