package com.corz.client.utils.prestige;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

public class DeathLootDetector implements MinecraftAccess {
    public static boolean isLootNearby(BlockPos pos) {
        int lootCount = 0;

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof ItemEntity itemEntity
                && !(itemEntity.getY() < pos.getY())
                && isValuableLoot(itemEntity.getStack().getItem())
                && !(Math.sqrt(itemEntity.squaredDistanceTo(pos.toCenterPos())) > 15.0)) {
                lootCount++;
            }
        }

        return lootCount > 3;
    }

    public static boolean isValuableLoot(Item item) {
        return item.equals(Items.DIAMOND_HELMET)
            || item.equals(Items.DIAMOND_CHESTPLATE)
            || item.equals(Items.DIAMOND_LEGGINGS)
            || item.equals(Items.DIAMOND_BOOTS)
            || item.equals(Items.NETHERITE_HELMET)
            || item.equals(Items.NETHERITE_CHESTPLATE)
            || item.equals(Items.NETHERITE_LEGGINGS)
            || item.equals(Items.NETHERITE_BOOTS)
            || item.equals(Items.DIAMOND_PICKAXE)
            || item.equals(Items.NETHERITE_PICKAXE)
            || item.equals(Items.DIAMOND_SWORD)
            || item.equals(Items.NETHERITE_SWORD)
            || item.equals(Items.DIAMOND_AXE)
            || item.equals(Items.NETHERITE_AXE)
            || item.equals(Items.TOTEM_OF_UNDYING)
            || item.equals(Items.GOLDEN_APPLE)
            || item.equals(Items.ENCHANTED_GOLDEN_APPLE)
            || item.equals(Items.ENDER_PEARL)
            || item.equals(Items.EXPERIENCE_BOTTLE)
            || item.equals(Items.END_CRYSTAL)
            || item.equals(Items.RESPAWN_ANCHOR)
            || item.equals(Items.GLOWSTONE);
    }
}
