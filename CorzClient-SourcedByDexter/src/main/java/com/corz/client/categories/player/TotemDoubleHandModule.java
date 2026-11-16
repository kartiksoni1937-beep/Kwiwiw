package com.corz.client.categories.player;

import ai.auth.Module;
import com.corz.client.config.BooleanSetting;
import com.corz.client.utils.AutoVanish;
import com.corz.client.utils.IconsPngType;
import com.corz.client.utils.prestige.player.InventoryUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

public class TotemDoubleHandModule extends Module {
    private final BooleanSetting onPop = new BooleanSetting("On pop", "Select a totem when your totem pops", true);

    public TotemDoubleHandModule() {
        super("Player", IconsPngType.MISC);
        this.addSettings(this.onPop);
    }

    @Override
    protected void onPacketReceive(AutoVanish packetReceiveEvent) {
        if (mc.player == null || mc.world == null) return;
        if (!this.onPop.isEnabled()) return;
        if (!(packetReceiveEvent.packet instanceof EntityStatusS2CPacket packet)) return;
        if (packet.getStatus() != 35) return;

        Entity entity = packet.getEntity(mc.world);
        if (!(entity instanceof PlayerEntity player) || player != mc.player) return;

        int totemSlot = InventoryUtils.findHotbarSlot(Items.TOTEM_OF_UNDYING);
        if (totemSlot != -1) {
            InventoryUtils.setInvSlot(totemSlot);
        }
    }
}
