package com.corz.client.categories.player;

import ai.auth.Module;
import com.corz.client.config.BooleanSetting;
import com.corz.client.config.ConfigSetting;
import com.corz.client.config.EnumSetting;
import com.corz.client.config.IntegerSetting;
import com.corz.client.utils.AutoVanish;
import com.corz.client.utils.IconsPngType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class BlockBruteforcer extends Module {
    private static final String MODE_ABORT = "Abort";
    private static final String MODE_START = "Start";

    private final IntegerSetting radius = new IntegerSetting("Radius", "Horizontal scan radius", 5, 1, 8);
    private final IntegerSetting up = new IntegerSetting("Up", "Blocks scanned above you", 3, 0, 64);
    private final IntegerSetting down = new IntegerSetting("Down", "Blocks scanned below you", 3, 0, 64);
    private final IntegerSetting delay = new IntegerSetting("Delay", "Ticks between scan batches", 0, 0, 10);
    private final IntegerSetting blocksPerTick = new IntegerSetting("Blocks Per Tick", "Blocks brute forced each batch", 4, 1, 5);
    private final BooleanSetting freezeMovement = new BooleanSetting("Freeze Movement", "Zero your movement while scanning", true);
    private final EnumSetting mode = new EnumSetting("Mode", "Use start or abort block actions", MODE_ABORT, MODE_ABORT, MODE_START);

    private final List<BlockPos> toCheck = new ArrayList<>();
    private final Set<BlockPos> checked = new HashSet<>();
    private final Set<BlockPos> revealed = new HashSet<>();
    private int tickCounter;
    private boolean scanning;
    private BlockPos scanOrigin;

    public BlockBruteforcer() {
        super("Player", IconsPngType.MISC);
        this.addSettings(new ConfigSetting[]{
                this.radius, this.up, this.down, this.delay, this.blocksPerTick,
                this.freezeMovement, this.mode
        });
    }

    @Override
    protected void onEnable() {
        this.toCheck.clear();
        this.checked.clear();
        this.revealed.clear();
        startScan();
    }

    @Override
    protected void onDisable() {
        this.toCheck.clear();
        this.checked.clear();
        this.revealed.clear();
        this.scanning = false;
        this.scanOrigin = null;
    }

    @Override
    protected void onPacketReceive(AutoVanish packetReceiveEvent) {
        if (mc.player == null) return;
        if (packetReceiveEvent.packet instanceof PlayerPositionLookS2CPacket) {
            this.setEnabled(false);
            return;
        }
        if (packetReceiveEvent.packet instanceof BlockUpdateS2CPacket blockUpdate) {
            if (this.checked.contains(blockUpdate.getPos())) {
                this.revealed.add(blockUpdate.getPos());
            }
        }
    }

    @Override
    protected void onReset() {
        if (mc.player == null || mc.world == null) {
            this.setEnabled(false);
            return;
        }
        if (!this.scanning) return;

        if (this.freezeMovement.isEnabled()) {
            mc.player.setVelocity(0.0, mc.player.getVelocity().y, 0.0);
        }

        processScan();
    }

    private void startScan() {
        if (mc.player == null || mc.world == null) return;
        this.scanning = true;
        this.toCheck.clear();
        this.checked.clear();

        BlockPos playerPos = mc.player.getBlockPos();
        this.scanOrigin = playerPos;
        int r = this.radius.getValue();
        int u = this.up.getValue();
        int d = this.down.getValue();

        for (int y = -d; y <= u; y++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (!mc.world.isAir(pos) && !this.checked.contains(pos)) {
                        this.toCheck.add(pos);
                    }
                }
            }
        }
    }

    private void processScan() {
        if (this.toCheck.isEmpty()) {
            this.scanning = false;
            this.setEnabled(false);
            return;
        }
        if (mc.getNetworkHandler() == null || mc.player == null) {
            this.scanning = false;
            return;
        }

        this.tickCounter++;
        if (this.tickCounter < this.delay.getValue()) return;
        this.tickCounter = 0;

        int count = Math.min(this.blocksPerTick.getValue(), this.toCheck.size());
        for (int i = 0; i < count; i++) {
            BlockPos pos = this.toCheck.remove(0);
            this.checked.add(pos);
            PlayerActionC2SPacket.Action action = this.mode.getCurrentOption().equals(MODE_START)
                    ? PlayerActionC2SPacket.Action.START_DESTROY_BLOCK
                    : PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK;
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(action, pos, Direction.UP));
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    public Set<BlockPos> getRevealedBlocks() {
        return this.revealed;
    }

    public boolean isScanning() {
        return this.scanning;
    }

    public int getProgress() {
        int total = this.toCheck.size() + this.checked.size();
        return total == 0 ? 100 : (this.checked.size() * 100) / total;
    }
}
