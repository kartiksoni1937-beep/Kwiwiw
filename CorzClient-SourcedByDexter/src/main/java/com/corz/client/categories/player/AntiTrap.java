package com.corz.client.categories.player;

import ai.auth.Module;
import com.corz.client.config.BooleanSetting;
import com.corz.client.config.ConfigSetting;
import com.corz.client.utils.IconsPngType;
import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.HangingSignBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.GlowItemFrameEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.painting.PaintingEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class AntiTrap extends Module {
    private final BooleanSetting noInteract = new BooleanSetting("No Interact", "Blocks interactions with trap blocks and entities", true);
    private final BooleanSetting armorStands = new BooleanSetting("Armor Stands", "Treat armor stands as traps", true);
    private final BooleanSetting itemFrames = new BooleanSetting("Item Frames", "Treat item frames as traps", true);
    private final BooleanSetting glowItemFrames = new BooleanSetting("Glow Frames", "Treat glow item frames as traps", true);
    private final BooleanSetting paintings = new BooleanSetting("Paintings", "Treat paintings as traps", true);
    private final BooleanSetting signs = new BooleanSetting("Signs", "Treat signs as traps", true);
    private final BooleanSetting hangingSigns = new BooleanSetting("Hanging Signs", "Treat hanging signs as traps", true);
    private final BooleanSetting obsidian = new BooleanSetting("Obsidian", "Treat obsidian as a trap block", true);
    private final BooleanSetting respawnAnchors = new BooleanSetting("Respawn Anchors", "Treat respawn anchors as trap blocks", true);
    private final BooleanSetting enderChests = new BooleanSetting("Ender Chests", "Treat ender chests as trap blocks", true);
    private final BooleanSetting anvils = new BooleanSetting("Anvils", "Treat anvils as trap blocks", true);
    private final BooleanSetting craftingTables = new BooleanSetting("Crafting Tables", "Treat crafting tables as trap blocks", false);
    private final BooleanSetting beds = new BooleanSetting("Beds", "Treat beds as trap blocks", false);
    private final BooleanSetting chests = new BooleanSetting("Chests", "Treat chests as trap blocks", false);

    public AntiTrap() {
        super("Player", IconsPngType.MISC);
        this.addSettings(new ConfigSetting[]{
                this.noInteract, this.armorStands, this.itemFrames, this.glowItemFrames,
                this.paintings, this.signs, this.hangingSigns, this.obsidian,
                this.respawnAnchors, this.enderChests, this.anvils, this.craftingTables,
                this.beds, this.chests
        });
    }

    @Override
    protected void onReset() {
        if (!this.noInteract.isEnabled() || mc.player == null || mc.world == null) return;

        Entity targeted = mc.targetedEntity;
        if (targeted != null && matchesEntity(targeted)) {
            BlockPos pos = targeted.getBlockPos();
            mc.targetedEntity = null;
            mc.crosshairTarget = BlockHitResult.createMissed(targeted.getEntityPos(), Direction.DOWN, pos);
            return;
        }

        if (!(mc.crosshairTarget instanceof BlockHitResult hitResult) || hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        if (matchesBlock(mc.world.getBlockState(hitResult.getBlockPos()))) {
            mc.targetedEntity = null;
            mc.crosshairTarget = BlockHitResult.createMissed(hitResult.getPos(), hitResult.getSide(), hitResult.getBlockPos());
        }
    }

    private boolean matchesEntity(Entity entity) {
        if (this.armorStands.isEnabled() && entity instanceof ArmorStandEntity) return true;
        if (this.itemFrames.isEnabled() && entity instanceof ItemFrameEntity && !(entity instanceof GlowItemFrameEntity)) return true;
        if (this.glowItemFrames.isEnabled() && entity instanceof GlowItemFrameEntity) return true;
        return this.paintings.isEnabled() && entity instanceof PaintingEntity;
    }

    private boolean matchesBlock(BlockState state) {
        if (state == null || state.isAir()) return false;

        if (state.getBlock() instanceof HangingSignBlock) {
            return this.hangingSigns.isEnabled();
        }
        if (state.getBlock() instanceof AbstractSignBlock) {
            return this.signs.isEnabled();
        }
        if (this.obsidian.isEnabled() && (state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.CRYING_OBSIDIAN))) return true;
        if (this.respawnAnchors.isEnabled() && state.isOf(Blocks.RESPAWN_ANCHOR)) return true;
        if (this.enderChests.isEnabled() && state.isOf(Blocks.ENDER_CHEST)) return true;
        if (this.anvils.isEnabled() && state.getBlock() instanceof AnvilBlock) return true;
        if (this.craftingTables.isEnabled() && state.getBlock() instanceof CraftingTableBlock) return true;
        if (this.beds.isEnabled() && state.getBlock() instanceof BedBlock) return true;
        return this.chests.isEnabled() && (state.isOf(Blocks.CHEST) || state.isOf(Blocks.TRAPPED_CHEST));
    }
}
