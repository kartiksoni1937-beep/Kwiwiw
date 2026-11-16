package com.corz.client.categories.player;

import ai.auth.Module;
import com.corz.client.config.BooleanSetting;
import com.corz.client.config.ConfigSetting;
import com.corz.client.config.KeybindSetting;
import com.corz.client.utils.IconsPngType;
import com.corz.client.utils.prestige.PlayerInteractions;
import com.corz.client.utils.prestige.player.InputHandler;
import com.corz.client.utils.prestige.player.InventoryUtils;
import com.corz.client.utils.prestige.player.SwapStateManager;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public class ElytraSwap extends Module {
    private final BooleanSetting switchBack = new BooleanSetting("Switch Back", "Restore your previous slot after the swap", true);
    private final BooleanSetting inputSimulation = new BooleanSetting("Click Simulation", "Simulate the use key when swapping", false);
    private final BooleanSetting silentSwap = new BooleanSetting("Silent Swap", "Swap and use the item in the same tick", false);
    private final KeybindSetting activateKey = new KeybindSetting("Activate Key", "Key used to start an elytra swap");

    private boolean startedWithElytra;
    private boolean swapped;
    private boolean keyWasDown;
    private boolean activated;
    private boolean shouldSwapBack;

    public ElytraSwap() {
        super("Player", IconsPngType.MISC);
        this.addSettings(new ConfigSetting[]{this.switchBack, this.inputSimulation, this.silentSwap, this.activateKey});
    }

    @Override
    protected void onReset() {
        SwapStateManager.tick();
        if (mc.player == null || mc.world == null || mc.player.isCreative()) return;
        if (mc.currentScreen != null) return;

        boolean keyDown = InputHandler.isBindDown(this.activateKey.getKeyCode());
        if (keyDown && !this.keyWasDown && !this.activated) {
            startSequence();
        }
        this.keyWasDown = keyDown;
        if (!this.activated) return;

        boolean wearingElytra = InventoryUtils.isWearingElytra();

        if (!this.swapped) {
            boolean holdingChestplate = InventoryUtils.isHoldingChestplate();
            boolean holdingElytra = mc.player.getMainHandStack().isOf(Items.ELYTRA);

            if ((holdingChestplate && this.startedWithElytra) || (holdingElytra && !this.startedWithElytra)) {
                useItem();
                this.swapped = true;
                if (this.silentSwap.isEnabled()) {
                    this.shouldSwapBack = true;
                }
            } else {
                int slot = this.startedWithElytra ? InventoryUtils.findChestplateSlot() : InventoryUtils.findHotbarSlot(Items.ELYTRA);
                if (slot == -1) {
                    reset();
                    return;
                }
                if (!SwapStateManager.swapToIfNeeded(this, slot, this.inputSimulation.isEnabled(), -1, true)) {
                    reset();
                    return;
                }
                if (this.silentSwap.isEnabled()) {
                    useItem();
                    this.swapped = true;
                    this.shouldSwapBack = true;
                }
            }
        }

        if (this.swapped) {
            boolean shouldReset = this.startedWithElytra ? !wearingElytra : wearingElytra;
            if (shouldReset) {
                SwapStateManager.cancel(this, this.switchBack.isEnabled());
                reset();
            }
        }
    }

    private void useItem() {
        if (this.inputSimulation.isEnabled()) {
            InputHandler.simulateClick(mc.options.useKey, true);
        }
        PlayerInteractions.interactItem(Hand.MAIN_HAND);
        if (mc.player != null) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private void startSequence() {
        if (mc.player == null) return;
        this.swapped = false;
        this.startedWithElytra = InventoryUtils.isWearingElytra();
        this.activated = true;
    }

    private void reset() {
        SwapStateManager.cancel(this, false);
        this.swapped = false;
        this.activated = false;
        this.shouldSwapBack = false;
    }

    @Override
    protected void onEnable() {
        this.activated = false;
        this.swapped = false;
        this.keyWasDown = false;
        this.shouldSwapBack = false;
        SwapStateManager.cancel(this, false);
    }

    @Override
    protected void onDisable() {
        reset();
    }
}
