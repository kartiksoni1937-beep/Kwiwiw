package com.corz.client.utils.prestige.player;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public final class BlockUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private BlockUtils() {
    }

    public static BlockPos getTargetPlacePos() {
        HitResult hitResult = mc.crosshairTarget;
        if (!(hitResult instanceof BlockHitResult blockHitResult) || hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return blockHitResult.getBlockPos().offset(blockHitResult.getSide());
    }

    public static boolean isValidPlacement(BlockPos pos) {
        if (pos == null || mc.world == null) return false;
        BlockState state = mc.world.getBlockState(pos);
        return state.isAir() || state.isReplaceable();
    }

    public static boolean isBlockPlacingCollideWithEntity(BlockPos pos, Block block) {
        if (pos == null || block == null || mc.world == null || mc.player == null) return false;
        BlockState state = block.getDefaultState();
        if (state.getCollisionShape(mc.world, pos).isEmpty()) {
            return false;
        }
        Box box = new Box(pos);
        return !mc.world.getOtherEntities(mc.player, box, entity -> entity.isAlive() && !entity.isSpectator()).isEmpty();
    }

    public static boolean isBlockAtPosition(BlockPos pos, Block block) {
        return pos != null && mc.world != null && mc.world.getBlockState(pos).isOf(block);
    }
}
