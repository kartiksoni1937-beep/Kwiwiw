package com.corz.client.categories.player;

import ai.auth.Module;
import com.corz.client.config.BooleanSetting;
import com.corz.client.config.ConfigSetting;
import com.corz.client.config.IntegerSetting;
import com.corz.client.config.KeybindSetting;
import com.corz.client.utils.IconsPngType;
import com.corz.client.utils.prestige.PlayerInteractions;
import com.corz.client.utils.prestige.player.InputHandler;
import com.corz.client.utils.prestige.player.InventoryUtils;
import com.google.common.base.Stopwatch;
import java.util.concurrent.TimeUnit;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public class FastEXP extends Module {
    private final IntegerSetting speed = new IntegerSetting("Speed", "Bottles thrown per second", 4, 1, 12);
    private final BooleanSetting autoEXP = new BooleanSetting("Auto EXP", "Throw while right click is held", true);
    private final KeybindSetting activateKey = new KeybindSetting("Activate Key", "Optional keybind for throwing EXP");

    private final Stopwatch stopWatch = Stopwatch.createUnstarted();

    public FastEXP() {
        super("Player", IconsPngType.MISC);
        this.addSettings(new ConfigSetting[]{this.speed, this.autoEXP, this.activateKey});
    }

    @Override
    protected void onEnable() {
        this.stopWatch.reset();
        this.stopWatch.start();
    }

    @Override
    protected void onDisable() {
        this.stopWatch.stop();
        this.stopWatch.reset();
    }

    @Override
    protected void onReset() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        boolean rightMouse = this.autoEXP.isEnabled()
                && InputHandler.isMouseDown(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        boolean keyDown = InputHandler.isBindDown(this.activateKey.getKeyCode());
        if (!rightMouse && !keyDown) return;

        boolean offHandXP = mc.player.getOffHandStack().isOf(Items.EXPERIENCE_BOTTLE);
        boolean mainHandXP = mc.player.getMainHandStack().isOf(Items.EXPERIENCE_BOTTLE);

        if (!offHandXP && !mainHandXP) {
            int slot = InventoryUtils.findHotbarSlot(Items.EXPERIENCE_BOTTLE);
            if (slot == -1) return;
            mc.player.getInventory().setSelectedSlot(slot);
            mainHandXP = true;
        }

        long interval = 1000L / Math.max(1, this.speed.getValue());
        if (!this.stopWatch.isRunning()) {
            this.stopWatch.start();
        }
        if (this.stopWatch.elapsed(TimeUnit.MILLISECONDS) < interval) return;

        this.stopWatch.reset();
        this.stopWatch.start();
        Hand hand = offHandXP ? Hand.OFF_HAND : Hand.MAIN_HAND;
        PlayerInteractions.interactItem(hand);
        mc.player.swingHand(hand);
    }
}
