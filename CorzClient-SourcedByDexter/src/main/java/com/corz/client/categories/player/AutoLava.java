package com.corz.client.categories.player;

import ai.auth.Module;
import com.corz.client.utils.IconsPngType;
import com.corz.client.utils.prestige.PlayerInteractions;
import com.corz.client.utils.prestige.player.BlockUtils;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

public class AutoLava extends Module {
    public AutoLava() {
        super("Player", IconsPngType.MISC);
    }

    @Override
    protected void onReset() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (!mc.player.getMainHandStack().isOf(Items.LAVA_BUCKET)) return;

        BlockPos targetPos = BlockUtils.getTargetPlacePos();
        if (targetPos == null) return;
        if (!BlockUtils.isValidPlacement(targetPos)) return;
        if (BlockUtils.isBlockPlacingCollideWithEntity(targetPos, Blocks.LAVA)) return;
        if (!(mc.crosshairTarget instanceof BlockHitResult blockHitResult)) return;

        BlockHitResult placementHitResult = new BlockHitResult(
                blockHitResult.getPos(),
                blockHitResult.getSide(),
                blockHitResult.getBlockPos(),
                blockHitResult.isInsideBlock()
        );
        PlayerInteractions.interactBlock(placementHitResult);
    }
}
