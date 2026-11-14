package com.corz.client.utils.prestige;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;

public class BlockUtil implements MinecraftAccess {
    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();

    public static boolean isCobwebInLineOfSight(Entity target) {
        Vec3d eyePos = CLIENT.player.getEyePos();
        Vec3d targetEyePos = target.getEyePos();
        Box targetBox = target.getBoundingBox();
        Vec3d direction = targetEyePos.subtract(eyePos).normalize();
        double distance = eyePos.distanceTo(targetEyePos);

        for (double traveled = 0.0; traveled < distance; traveled += 0.1) {
            Vec3d point = eyePos.add(direction.multiply(traveled));
            if (targetBox != null && targetBox.contains(point)) {
                return false;
            }

            BlockPos blockPos = BlockPos.ofFloored(point.x, point.y, point.z);
            if (CLIENT.world.getBlockState(blockPos).getBlock() == Blocks.COBWEB) {
                return true;
            }
        }

        return false;
    }

    public static BlockPos getPlacePos(BlockHitResult hitResult) {
        return hitResult.getBlockPos().add(hitResult.getSide().getOffsetX(), hitResult.getSide().getOffsetY(), hitResult.getSide().getOffsetZ());
    }

    public static boolean isOf(BlockPos pos, Block block) {
        return getState(pos).isOf(block);
    }

    public static boolean isClickableHit(HitResult hitResult) {
        return hitResult instanceof BlockHitResult blockHit && isClickableBlock(blockHit.getBlockPos());
    }

    public static boolean isClickableBlockHit(BlockHitResult hitResult) {
        return isClickableBlock(hitResult.getBlockPos());
    }

    public static boolean isClickableBlock(BlockPos pos) {
        return !CLIENT.world.getBlockState(pos).isReplaceable()
            || CLIENT.world.getBlockState(pos).isOf(Blocks.FIRE)
            || CLIENT.world.getBlockState(pos).isOf(getShortGrass())
            || CLIENT.world.getBlockState(pos).isOf(Blocks.SNOW) && CLIENT.world.getBlockState(pos).get(Properties.LAYERS) == 1;
    }

    public static Block getShortGrass() {
        return Blocks.SHORT_GRASS;
    }

    public static boolean canPlaceCrystal(BlockPos pos) {
        return (isOf(pos, Blocks.OBSIDIAN) || isOf(pos, Blocks.BEDROCK))
            && isOf(pos.up(), Blocks.AIR)
            && !hasEntityAt(pos.up());
    }

    public static boolean hasEntityAt(BlockPos pos) {
        Box box = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
        return !CLIENT.world.getOtherEntities(null, box, entity -> !(entity instanceof ItemEntity)).isEmpty();
    }

    public static List getBlocksInRadius(int radius, Predicate filter) {
        ArrayList result = new ArrayList();
        BlockPos center = CLIENT.player.getBlockPos();
        int centerX = center.getX();
        int centerY = center.getY();
        int centerZ = center.getZ();
        Mutable mutable = new Mutable();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    mutable.set(centerX + dx, centerY + dy, centerZ + dz);
                    if (filter.test(mutable)) {
                        result.add(mutable.toImmutable());
                    }
                }
            }
        }

        return result;
    }

    public static boolean isBlock(BlockPos pos, Block block) {
        return CLIENT.world.getBlockState(pos).getBlock() == block;
    }

    public static BlockState getState(BlockPos pos) {
        return CLIENT.world.getBlockState(pos);
    }

    public static int getAnchorCharges(BlockPos pos) {
        BlockState state = getState(pos);
        return state.contains(Properties.CHARGES) ? state.get(Properties.CHARGES) : 0;
    }
}
