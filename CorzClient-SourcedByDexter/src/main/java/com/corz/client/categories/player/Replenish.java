package com.corz.client.categories.player;

import ai.auth.Module;
import com.corz.client.config.ConfigSetting;
import com.corz.client.config.DoubleSetting;
import com.corz.client.config.IntegerSetting;
import com.corz.client.utils.IconsPngType;
import com.corz.client.utils.MillisCooldownTimer;
import com.corz.client.utils.prestige.InventoryClickCompat;
import com.corz.client.utils.prestige.InventoryClickType;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;

public class Replenish extends Module {
    private final DoubleSetting minPercent = new DoubleSetting("Min %", "Refill stacks at or below this percentage", 25.0, 0.0, 100.0, 1.0);
    private final IntegerSetting cursorSpeed = new IntegerSetting("Cursor Speed", "Delay between refills", 20, 1, 100);
    private final MillisCooldownTimer timer = new MillisCooldownTimer();

    public Replenish() {
        super("Player", IconsPngType.MISC);
        this.addSettings(new ConfigSetting[]{this.minPercent, this.cursorSpeed});
    }

    @Override
    protected void onReset() {
        if (mc.player == null || mc.world == null) return;
        if (!(mc.currentScreen instanceof InventoryScreen)) return;

        long delayMs = Math.max(50L, 200L - (this.cursorSpeed.getValue() * 2L));
        if (!this.timer.hasElapsed(delayMs)) return;

        for (int hotbar = 0; hotbar < 9; hotbar++) {
            ItemStack stack = mc.player.getInventory().getStack(hotbar);
            if (stack.isEmpty() || !stack.isStackable()) continue;

            float percent = (stack.getCount() / (float) stack.getMaxCount()) * 100.0f;
            if (stack.getCount() == 1 || percent <= Math.max((float) this.minPercent.getValue(), 5.0f)) {
                if (mergeStack(stack)) {
                    this.timer.restart();
                    return;
                }
            }
        }
    }

    private boolean mergeStack(ItemStack target) {
        for (int invSlot = 9; invSlot < 36; invSlot++) {
            ItemStack stack = mc.player.getInventory().getStack(invSlot);
            if (stack.isEmpty() || !canMerge(target, stack)) continue;

            int menuSlot = invSlot + 9;
            InventoryClickCompat.handleClick(0, menuSlot, 0, InventoryClickType.QUICK_MOVE, mc.player);
            return true;
        }
        return false;
    }

    private boolean canMerge(ItemStack first, ItemStack second) {
        if (!first.getItem().equals(second.getItem())) return false;
        if (first.getItem() instanceof BlockItem firstBlock && second.getItem() instanceof BlockItem secondBlock) {
            return firstBlock.getBlock() == secondBlock.getBlock();
        }
        return true;
    }
}
