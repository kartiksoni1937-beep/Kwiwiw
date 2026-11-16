package com.corz.client.utils.prestige.player;

import ai.auth.Module;
import com.corz.client.entity.ClientThreadUtils;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.MinecraftClient;

public final class SwapStateManager {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Map<Module, SwapState> STATES = new IdentityHashMap<>();

    private SwapStateManager() {
    }

    public static void tick() {
        if (mc.player == null) {
            STATES.clear();
            return;
        }
        long now = ClientThreadUtils.getTime();
        Iterator<Map.Entry<Module, SwapState>> iterator = STATES.entrySet().iterator();
        while (iterator.hasNext()) {
            SwapState state = iterator.next().getValue();
            if (state.restoreTick != Long.MAX_VALUE && now >= state.restoreTick) {
                if (state.restoreOnEnd) {
                    InventoryUtils.setInvSlot(state.rootSlot);
                }
                iterator.remove();
            }
        }
    }

    public static boolean swapToIfNeeded(Module owner, int targetSlot, boolean inputSimulation, int restoreDelayTicks, boolean restoreOnEnd) {
        if (owner == null || mc.player == null || targetSlot < 0 || targetSlot > 8) return false;
        tick();
        SwapState active = STATES.get(owner);
        if (active == null && InventoryUtils.getSelectedSlot() == targetSlot) {
            return true;
        }
        return swapTo(owner, targetSlot, inputSimulation, restoreDelayTicks, restoreOnEnd);
    }

    public static boolean swapTo(Module owner, int targetSlot, boolean inputSimulation, int restoreDelayTicks, boolean restoreOnEnd) {
        if (owner == null || mc.player == null || targetSlot < 0 || targetSlot > 8) return false;
        tick();

        int current = InventoryUtils.getSelectedSlot();
        SwapState state = STATES.get(owner);
        if (state == null) {
            if (current == targetSlot) return false;
            long restoreTick = restoreDelayTicks < 0
                    ? Long.MAX_VALUE
                    : ClientThreadUtils.getTime() + restoreDelayTicks;
            state = new SwapState(current, targetSlot, restoreTick, restoreOnEnd);
            STATES.put(owner, state);
        } else {
            state.targetSlot = targetSlot;
            state.restoreOnEnd = restoreOnEnd;
            state.restoreTick = restoreDelayTicks < 0
                    ? Long.MAX_VALUE
                    : ClientThreadUtils.getTime() + restoreDelayTicks;
        }

        InventoryUtils.setInvSlot(targetSlot, inputSimulation);
        return true;
    }

    public static boolean isActive(Module owner) {
        return owner != null && STATES.containsKey(owner);
    }

    public static boolean isOwnerActive(Module owner) {
        return isActive(owner);
    }

    public static boolean hasActiveSwaps() {
        return !STATES.isEmpty();
    }

    public static int getActiveTargetSlot(Module owner) {
        SwapState state = owner == null ? null : STATES.get(owner);
        return state == null ? -1 : state.targetSlot;
    }

    public static void cancel(Module owner, boolean restore) {
        if (owner == null) return;
        SwapState state = STATES.remove(owner);
        if (state != null && restore) {
            InventoryUtils.setInvSlot(state.rootSlot);
        }
    }

    public static void clear() {
        STATES.clear();
    }

    private static final class SwapState {
        private final int rootSlot;
        private int targetSlot;
        private long restoreTick;
        private boolean restoreOnEnd;

        private SwapState(int rootSlot, int targetSlot, long restoreTick, boolean restoreOnEnd) {
            this.rootSlot = rootSlot;
            this.targetSlot = targetSlot;
            this.restoreTick = restoreTick;
            this.restoreOnEnd = restoreOnEnd;
        }
    }
}
