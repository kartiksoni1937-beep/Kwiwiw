package com.corz.client.categories.player;

import ai.auth.Module;
import com.corz.client.inventory.InventoryStackUtilsEmpty;
import com.corz.client.mixins.AccessorClientPlayerInteractionManager;
import com.corz.client.utils.IconsPngType;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

public class AttributeMineModule extends Module {
    private BlockPos lastBlockPos;
    private int pendingToolSlot = -1;
    private int ticksRemaining;

    public AttributeMineModule() {
        super("Player", IconsPngType.MISC);
    }

    @Override
    protected void onReset() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (this.pendingToolSlot != -1) {
            this.ticksRemaining--;
            if (this.ticksRemaining <= 0) {
                mc.player.getInventory().setSelectedSlot(this.pendingToolSlot);
                ((AccessorClientPlayerInteractionManager) mc.interactionManager).corzSyncSelectedSlot();
                this.pendingToolSlot = -1;
            }
            return;
        }

        if (!mc.options.attackKey.isPressed()) {
            this.lastBlockPos = null;
            return;
        }

        if (!(mc.crosshairTarget instanceof BlockHitResult blockHitResult)) {
            this.lastBlockPos = null;
            return;
        }

        BlockPos currentPos = blockHitResult.getBlockPos();
        BlockState state = mc.world.getBlockState(currentPos);
        if (state.isAir()) {
            this.lastBlockPos = null;
            return;
        }

        if (currentPos.equals(this.lastBlockPos)) return;
        this.lastBlockPos = currentPos;

        int efficiencySlot = getBestEfficiencySlot();
        if (efficiencySlot == -1) return;
        int bestToolSlot = getBestToolSlot(state);

        PlayerInventory inventory = mc.player.getInventory();
        inventory.setSelectedSlot(efficiencySlot);
        ((AccessorClientPlayerInteractionManager) mc.interactionManager).corzSyncSelectedSlot();

        if (bestToolSlot != -1 && bestToolSlot != efficiencySlot) {
            this.pendingToolSlot = bestToolSlot;
            this.ticksRemaining = 2;
        }
    }

    private int getBestEfficiencySlot() {
        PlayerInventory inventory = mc.player.getInventory();
        int bestSlot = -1;
        int bestLevel = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) continue;
            int level = InventoryStackUtilsEmpty.getEnchantmentsDefault(stack, Enchantments.EFFICIENCY);
            if (level > bestLevel) {
                bestLevel = level;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private int getBestToolSlot(BlockState state) {
        PlayerInventory inventory = mc.player.getInventory();
        int bestSlot = -1;
        float bestSpeed = 1.0f;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) continue;
            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }
        return bestSlot;
    }
}
