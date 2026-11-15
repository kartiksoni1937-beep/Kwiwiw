package com.corz.client.categories.player;

import ai.auth.Module;
import ai.auth.ModuleRegistry;
import com.corz.client.config.BooleanSetting;
import com.corz.client.config.EnumSetting;
import com.corz.client.config.IntegerSetting;
import com.corz.client.config.RangeSetting;
import com.corz.client.gui.TickProgress;
import com.corz.client.mixins.AccessorHandledScreen;
import com.corz.client.utils.IconsPngType;
import com.corz.client.utils.prestige.ContainerUtils;
import com.corz.client.utils.prestige.CursorUtil;
import com.corz.client.utils.prestige.CursorUtil.ScreenInfo;
import com.corz.client.utils.prestige.CursorUtil.SlotResult;
import com.corz.client.utils.prestige.InventoryClickCompat;
import com.corz.client.utils.prestige.InventoryClickType;
import com.corz.client.utils.prestige.MathUtil;
import com.corz.client.utils.prestige.TimerUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.Window;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import org.lwjgl.glfw.GLFW;

public class AutoTotem extends Module {
    public static volatile boolean blocked = false;

    private enum SilentStage {
        IDLE,
        WAIT_BEFORE_OPEN,
        WAIT_AFTER_OPEN,
        WAIT_BEFORE_CLOSE
    }

    private enum OpenMode {
        OPEN_INVENTORY("Open Inventory"),
        SILENT_OPEN("Silent Open"),
        DONT_OPEN("Dont Open");

        private final String option;

        OpenMode(String option) {
            this.option = option;
        }
    }

    private enum SelectionMode {
        AUTOMATIC("Automatic"),
        HOVER("Hover"),
        CURSOR_SIMULATION("Cursor Simulation");

        private final String option;

        SelectionMode(String option) {
            this.option = option;
        }
    }

    private static final float CURSOR_MIN_TIME_MS = 50.0f;
    private static final float CURSOR_MAX_TIME_MS = 250.0f;

    private final EnumSetting mode = new EnumSetting(
        "Mode",
        "How the inventory is opened before moving the totem.",
        OpenMode.OPEN_INVENTORY.option,
        OpenMode.OPEN_INVENTORY.option,
        OpenMode.SILENT_OPEN.option,
        OpenMode.DONT_OPEN.option
    );

    private final EnumSetting selectionMode = new EnumSetting(
        "Selection",
        "How the totem slot is chosen.",
        SelectionMode.AUTOMATIC.option,
        SelectionMode.AUTOMATIC.option,
        SelectionMode.HOVER.option,
        SelectionMode.CURSOR_SIMULATION.option
    );

    private final BooleanSetting autoClose = new BooleanSetting("Auto Close", "Closes the inventory after the swap.", true);
    private final RangeSetting closeDelay = new RangeSetting(
        "Close Delay",
        "Random delay before closing the inventory (ms).",
        50,
        150,
        0,
        500,
        25
    );

    private final RangeSetting silentMoveDelay = new RangeSetting(
        "Silent Move Delay",
        "Random delay used around a silent swap (ms).",
        100,
        300,
        0,
        500,
        25
    );

    private final BooleanSetting randomSlot = new BooleanSetting("Random Slot", "Picks a random totem slot instead of the first one.", false);

    private final RangeSetting delay = new RangeSetting(
        "Delay",
        "Random delay between swaps (ms).",
        50,
        150,
        0,
        500,
        25
    );

    private final BooleanSetting refillHotbar = new BooleanSetting("Refill Hotbar", "Also keeps a totem in the selected hotbar slot.", false);
    private final IntegerSetting refillSlot = new IntegerSetting("Slot to Refill Totem", "Hotbar slot to keep a totem in (1-9).", 9, 1, 9);

    private final TimerUtils actionTimer = new TimerUtils();
    private final TimerUtils silentMoveTimer = new TimerUtils();
    private final TimerUtils silentStageTickTimer = new TimerUtils();
    private final TimerUtils closeTimer = new TimerUtils();
    private final Random random = new Random();

    private long nextActionDelayMs;
    private long activeSilentMoveDelayMs;
    private long nextCloseDelayMs;
    private boolean waitingToCloseInventory;
    private boolean openedInventoryForHoverCycle;
    private boolean waitingForOpenInventoryDelay;
    private SilentStage silentStage = SilentStage.IDLE;
    private int pendingSilentSourceSlot = -1;
    private int pendingSilentButton = -1;

    private boolean cursorAnimating;
    private boolean waitingForCursorSwap;
    private long cursorAnimStartMs;
    private long cursorAnimDurationMs;
    private double cursorStartX;
    private double cursorStartY;
    private double cursorEndX;
    private double cursorEndY;
    private int pendingCursorSourceSlot = -1;
    private int pendingCursorButton = -1;

    public AutoTotem() {
        super("Player", IconsPngType.MISC);
        this.addSettings(
            this.mode,
            this.selectionMode,
            this.autoClose,
            this.closeDelay,
            this.silentMoveDelay,
            this.randomSlot,
            this.delay,
            this.refillHotbar,
            this.refillSlot
        );
    }

    @Override
    protected void onEnable() {
        this.waitingToCloseInventory = false;
        this.activeSilentMoveDelayMs = 0L;
        this.actionTimer.reset();
        this.silentMoveTimer.reset();
        this.silentStageTickTimer.reset();
        this.closeTimer.reset();
        this.nextActionDelayMs = this.randomDelayMs();
        this.nextCloseDelayMs = this.randomCloseDelayMs();
        this.openedInventoryForHoverCycle = false;
        this.waitingForOpenInventoryDelay = false;
        this.resetSilentState();
        this.resetCursorSimulationState();
    }

    @Override
    protected void onDisable() {
        this.waitingToCloseInventory = false;
        this.activeSilentMoveDelayMs = 0L;
        this.openedInventoryForHoverCycle = false;
        this.waitingForOpenInventoryDelay = false;
        this.resetSilentState();
        this.resetCursorSimulationState();
    }

    @Override
    public void onReset() {
        if (blocked) return;
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (this.isBlockedByScreen()) {
            if (this.isCursorSimulationActive()) {
                this.resetCursorSimulationState();
            }
            return;
        }

        if (!this.isMode(OpenMode.SILENT_OPEN) && this.isSilentActionActive()) {
            this.resetSilentState();
        }

        if (!this.isCursorSimulationSubMode() && this.isCursorSimulationActive()) {
            this.resetCursorSimulationState();
        }

        if (this.isMode(OpenMode.SILENT_OPEN) && this.isSilentActionActive()) {
            this.processSilentSequence();
            return;
        }

        if (this.waitingForCursorSwap) {
            this.executeCursorSimulationSwap();
            return;
        }

        this.handlePendingClose();
        this.updateOpenInventoryDelayState();

        if (this.isCursorSimulationActive()) {
            return;
        }

        if (!mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            this.handleOffhandRefill();
            return;
        }

        this.openedInventoryForHoverCycle = false;

        if (this.refillHotbar.isEnabled()) {
            this.handleHotbarRefill();
        }
    }

    @Override
    protected void onRender2D(TickProgress event) {
        if (!this.cursorAnimating) return;
        this.processCursorSimulationRender();
    }

    public static boolean shouldSuppressActions() {
        return ModuleRegistry.getModule(AutoTotem.class) instanceof AutoTotem autoTotem
            && autoTotem.isEnabled()
            && autoTotem.isSilentActionActive();
    }

    public static boolean shouldSuppressMovement() {
        return shouldSuppressActions();
    }

    private void handleOffhandRefill() {
        if (this.isMode(OpenMode.OPEN_INVENTORY) && !this.isInventoryScreenOpen()) {
            if (!this.hasTotemInInventory(-1)) return;
            if (!this.beginOpenInventoryDelay()) return;
            if (!this.isActionDelayReady()) return;
            if (this.isHoverSubMode() && this.openedInventoryForHoverCycle) return;
            this.prepareModeForAction();
            this.resetOpenInventoryDelay();
            if (this.isHoverSubMode()) {
                this.openedInventoryForHoverCycle = true;
            }
            return;
        }

        int sourceMenuSlot = this.findOffhandSourceSlot();
        if (sourceMenuSlot == -1) return;
        if (!this.isActionDelayReady()) return;

        if (this.isMode(OpenMode.SILENT_OPEN)) {
            this.startSilentSequence(sourceMenuSlot, 40);
            return;
        }

        if (!this.prepareModeForAction()) return;

        if (this.isCursorSimulationSubMode()) {
            this.startCursorSimulation(sourceMenuSlot, 40);
            return;
        }

        this.clickSwap(sourceMenuSlot, 40);
    }

    private void handleHotbarRefill() {
        int targetHotbarIndex = this.refillSlot.getValue() - 1;
        if (mc.player.getInventory().getStack(targetHotbarIndex).isOf(Items.TOTEM_OF_UNDYING)) return;

        if (this.isMode(OpenMode.OPEN_INVENTORY) && !this.isInventoryScreenOpen()) {
            if (!this.hasTotemInInventory(targetHotbarIndex)) return;
            if (!this.beginOpenInventoryDelay()) return;
            if (!this.isActionDelayReady()) return;
            this.prepareModeForAction();
            this.resetOpenInventoryDelay();
            return;
        }

        int sourceMenuSlot = this.findHotbarRefillSourceSlot(targetHotbarIndex);
        if (sourceMenuSlot == -1) return;
        if (!this.isActionDelayReady()) return;

        if (this.isMode(OpenMode.SILENT_OPEN)) {
            this.startSilentSequence(sourceMenuSlot, targetHotbarIndex);
            return;
        }

        if (!this.prepareModeForAction()) return;

        if (this.isCursorSimulationSubMode()) {
            this.startCursorSimulation(sourceMenuSlot, targetHotbarIndex);
            return;
        }

        this.clickSwap(sourceMenuSlot, targetHotbarIndex);
    }

    private boolean isActionDelayReady() {
        return this.actionTimer.hasReached(this.nextActionDelayMs);
    }

    private boolean beginOpenInventoryDelay() {
        if (this.waitingForOpenInventoryDelay) {
            return true;
        }

        this.waitingForOpenInventoryDelay = true;
        this.actionTimer.reset();
        this.nextActionDelayMs = this.randomDelayMs();
        return false;
    }

    private void updateOpenInventoryDelayState() {
        if (!this.waitingForOpenInventoryDelay) return;
        if (this.shouldKeepOpenInventoryDelay()) return;
        this.resetOpenInventoryDelay();
    }

    private boolean shouldKeepOpenInventoryDelay() {
        if (!this.isMode(OpenMode.OPEN_INVENTORY)) return false;
        if (this.isInventoryScreenOpen()) return false;

        if (!mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            return this.hasTotemInInventory(-1);
        }

        if (!this.refillHotbar.isEnabled()) {
            return false;
        }

        int targetHotbarIndex = this.refillSlot.getValue() - 1;
        if (mc.player.getInventory().getStack(targetHotbarIndex).isOf(Items.TOTEM_OF_UNDYING)) {
            return false;
        }

        return this.hasTotemInInventory(targetHotbarIndex);
    }

    private void resetOpenInventoryDelay() {
        this.waitingForOpenInventoryDelay = false;
    }

    private boolean prepareModeForAction() {
        if (this.isMode(OpenMode.OPEN_INVENTORY)) {
            if (this.isInventoryScreenOpen()) return true;

            mc.setScreen(new InventoryScreen(mc.player));
            return false;
        }

        if (this.isMode(OpenMode.DONT_OPEN)) {
            return this.isInventoryScreenOpen();
        }

        return !this.isMode(OpenMode.SILENT_OPEN);
    }

    private int findOffhandSourceSlot() {
        if (this.isHoverSubMode()) {
            return this.findHoveredTotemSlot();
        }

        if (this.isCursorSimulationSubMode()) {
            return this.findNearestCursorSimulationSlot(-1);
        }

        if (this.randomSlot.isEnabled()) {
            return this.findRandomTotemMenuSlot(-1);
        }

        return this.findFirstTotemMenuSlot(-1);
    }

    private int findHotbarRefillSourceSlot(int targetHotbarIndex) {
        if (this.isHoverSubMode()) {
            int hovered = this.findHoveredTotemSlot();
            if (hovered == -1) return -1;
            if (hovered == this.toMenuSlot(targetHotbarIndex)) return -1;
            return hovered;
        }

        if (this.isCursorSimulationSubMode()) {
            return this.findNearestCursorSimulationSlot(targetHotbarIndex);
        }

        if (this.randomSlot.isEnabled()) {
            return this.findRandomTotemMenuSlot(targetHotbarIndex);
        }

        return this.findFirstTotemMenuSlot(targetHotbarIndex);
    }

    private int findHoveredTotemSlot() {
        if (!(mc.currentScreen instanceof InventoryScreen || mc.currentScreen instanceof CreativeInventoryScreen)) {
            return -1;
        }

        if (!(mc.currentScreen instanceof AccessorHandledScreen accessor)) {
            return -1;
        }

        Slot hoveredSlot = accessor.corzFocusedSlot();
        if (hoveredSlot == null) return -1;
        if (!hoveredSlot.getStack().isOf(Items.TOTEM_OF_UNDYING)) return -1;

        int menuSlot = mc.player.currentScreenHandler.slots.indexOf(hoveredSlot);
        if (menuSlot == -1) return -1;
        if (menuSlot == 40) return -1;
        if (menuSlot >= 36 && menuSlot <= 44) return -1;

        return menuSlot;
    }

    private int findNearestCursorSimulationSlot(int excludedHotbarIndex) {
        SlotResult nearest = this.findNearestCursorSimulationTarget(excludedHotbarIndex);
        if (nearest == null) return -1;
        return nearest.slotIndex();
    }

    private SlotResult findNearestCursorSimulationTarget(int excludedHotbarIndex) {
        if (!this.isInventoryScreenOpen()) return null;

        ScreenInfo screenInfo = CursorUtil.getScreenInfo(mc.currentScreen);
        if (screenInfo == null) return null;

        Window window = mc.getWindow();
        int[] origin = CursorUtil.getScreenOrigin(screenInfo, window);
        double mouseX = mc.mouse.getX();
        double mouseY = mc.mouse.getY();
        boolean isCreative = mc.currentScreen instanceof CreativeInventoryScreen;
        return CursorUtil.findNearestSlotWithItem(
            screenInfo.handler().slots,
            Items.TOTEM_OF_UNDYING,
            isCreative,
            origin[0],
            origin[1],
            window,
            mouseX,
            mouseY,
            slot -> this.isCursorSimulationCandidate(slot, excludedHotbarIndex)
        );
    }

    private boolean isCursorSimulationCandidate(Slot slot, int excludedHotbarIndex) {
        if (!(slot.inventory instanceof PlayerInventory)) return true;

        int containerSlot = slot.getIndex();
        if (containerSlot == 40) return false;
        if (containerSlot == excludedHotbarIndex) return false;
        if (containerSlot >= 0 && containerSlot <= 8) return false;
        return true;
    }

    private int findFirstTotemMenuSlot(int excludedHotbarIndex) {
        for (int inventorySlot = 9; inventorySlot < 36; inventorySlot++) {
            if (inventorySlot == excludedHotbarIndex) continue;
            if (!mc.player.getInventory().getStack(inventorySlot).isOf(Items.TOTEM_OF_UNDYING)) continue;
            return this.toMenuSlot(inventorySlot);
        }

        return -1;
    }

    private int findRandomTotemMenuSlot(int excludedHotbarIndex) {
        List<Integer> candidates = new ArrayList<>();

        for (int inventorySlot = 9; inventorySlot < 36; inventorySlot++) {
            if (inventorySlot == excludedHotbarIndex) continue;
            if (!mc.player.getInventory().getStack(inventorySlot).isOf(Items.TOTEM_OF_UNDYING)) continue;
            candidates.add(this.toMenuSlot(inventorySlot));
        }

        if (candidates.isEmpty()) {
            return -1;
        }

        return candidates.get(this.random.nextInt(candidates.size()));
    }

    private boolean hasTotemInInventory(int excludedHotbarIndex) {
        for (int inventorySlot = 9; inventorySlot < 36; inventorySlot++) {
            if (inventorySlot == excludedHotbarIndex) continue;
            if (mc.player.getInventory().getStack(inventorySlot).isOf(Items.TOTEM_OF_UNDYING)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlockedByScreen() {
        if (mc.currentScreen == null) return false;
        if (mc.currentScreen instanceof InventoryScreen) return false;
        if (mc.currentScreen instanceof CreativeInventoryScreen) return false;
        return true;
    }

    private boolean isInventoryScreenOpen() {
        return mc.currentScreen instanceof InventoryScreen || mc.currentScreen instanceof CreativeInventoryScreen;
    }

    private void clickSwap(int sourceMenuSlot, int button) {
        this.clickSwap(sourceMenuSlot, button, true);
    }

    private void clickSwap(int sourceMenuSlot, int button, boolean scheduleClose) {
        InventoryClickCompat.handleClick(
            mc.player.currentScreenHandler.syncId,
            sourceMenuSlot,
            button,
            InventoryClickType.SWAP,
            mc.player
        );

        this.actionTimer.reset();
        this.nextActionDelayMs = this.randomDelayMs();
        this.openedInventoryForHoverCycle = false;
        if (scheduleClose) {
            this.startCloseCountdownIfNeeded();
        }
    }

    private void startCursorSimulation(int sourceMenuSlot, int button) {
        if (this.isCursorSimulationActive()) return;
        if (!this.isInventoryScreenOpen()) return;

        ScreenInfo screenInfo = CursorUtil.getScreenInfo(mc.currentScreen);
        if (screenInfo == null) return;
        if (sourceMenuSlot < 0 || sourceMenuSlot >= screenInfo.handler().slots.size()) return;

        Window window = mc.getWindow();
        int[] origin = CursorUtil.getScreenOrigin(screenInfo, window);
        double mouseX = mc.mouse.getX();
        double mouseY = mc.mouse.getY();
        boolean isCreative = mc.currentScreen instanceof CreativeInventoryScreen;
        double maxDistance = CursorUtil.maxSlotDistance(
            screenInfo.handler().slots,
            isCreative,
            origin[0],
            origin[1],
            window,
            mouseX,
            mouseY,
            slot -> this.isCursorSimulationCandidate(slot, button)
        );

        Slot targetSlot = screenInfo.handler().slots.get(sourceMenuSlot);
        double[] targetPos = CursorUtil.slotToAbsPos(targetSlot, origin[0], origin[1], window);
        double distance = Math.hypot(targetPos[0] - mouseX, targetPos[1] - mouseY);

        this.cursorStartX = mouseX;
        this.cursorStartY = mouseY;
        this.cursorEndX = targetPos[0];
        this.cursorEndY = targetPos[1];
        this.cursorAnimDurationMs = CursorUtil.computeAnimDuration(distance, maxDistance, CURSOR_MIN_TIME_MS, CURSOR_MAX_TIME_MS);
        this.cursorAnimStartMs = System.currentTimeMillis();
        this.pendingCursorSourceSlot = sourceMenuSlot;
        this.pendingCursorButton = button;
        this.cursorAnimating = true;
        this.waitingForCursorSwap = false;

        mc.execute(() -> mc.mouse.unlockCursor());
    }

    private void processCursorSimulationRender() {
        if (mc.player == null || mc.player.isDead()) {
            this.resetCursorSimulationState();
            return;
        }

        if (!this.isCursorSimulationSubMode()) {
            this.resetCursorSimulationState();
            return;
        }

        if (!this.isInventoryScreenOpen()) {
            this.resetCursorSimulationState();
            return;
        }

        long elapsed = System.currentTimeMillis() - this.cursorAnimStartMs;
        double progress = Math.max(0.0, Math.min(1.0, (double) elapsed / this.cursorAnimDurationMs));
        double eased = MathUtil.smoothStepLerp(progress, 0.0, 1.0);

        double x = this.cursorStartX + (this.cursorEndX - this.cursorStartX) * eased;
        double y = this.cursorStartY + (this.cursorEndY - this.cursorStartY) * eased;
        long handle = mc.getWindow().getHandle();
        GLFW.glfwSetCursorPos(handle, x, y);
        GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);

        if (progress < 1.0) return;

        this.cursorAnimating = false;
        this.waitingForCursorSwap = true;
    }

    private void executeCursorSimulationSwap() {
        ScreenInfo screenInfo = CursorUtil.getScreenInfo(mc.currentScreen);
        if (screenInfo == null || mc.interactionManager == null) {
            this.resetCursorSimulationState();
            return;
        }

        if (this.pendingCursorSourceSlot < 0 || this.pendingCursorSourceSlot >= screenInfo.handler().slots.size()) {
            this.resetCursorSimulationState();
            return;
        }

        if (!this.needsPendingCursorSwap()) {
            this.resetCursorSimulationState();
            return;
        }

        Slot slot = screenInfo.handler().slots.get(this.pendingCursorSourceSlot);
        if (!slot.getStack().isOf(Items.TOTEM_OF_UNDYING)) {
            this.resetCursorSimulationState();
            return;
        }

        this.clickSwap(this.pendingCursorSourceSlot, this.pendingCursorButton);
        this.resetCursorSimulationState();
    }

    private boolean needsPendingCursorSwap() {
        if (this.pendingCursorButton == 40) {
            return !mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
        }

        if (this.pendingCursorButton < 0 || this.pendingCursorButton > 8) {
            return false;
        }

        return !mc.player.getInventory().getStack(this.pendingCursorButton).isOf(Items.TOTEM_OF_UNDYING);
    }

    private int toMenuSlot(int inventorySlot) {
        if (inventorySlot < 9) return inventorySlot + 36;
        return inventorySlot;
    }

    private long randomDelayMs() {
        return this.randomRangeMs(this.delay);
    }

    private long randomSilentDelayMs() {
        return this.randomRangeMs(this.silentMoveDelay);
    }

    private long randomCloseDelayMs() {
        return this.randomRangeMs(this.closeDelay);
    }

    private long randomRangeMs(RangeSetting setting) {
        double min = setting.getMin();
        double max = setting.getMax();
        if (max <= min) {
            return Math.max(0L, (long) min);
        }
        return Math.max(0L, (long) (min + this.random.nextDouble() * (max - min)));
    }

    private void startCloseCountdownIfNeeded() {
        if (!this.autoClose.isEnabled()) return;

        this.nextCloseDelayMs = this.randomCloseDelayMs();
        if (this.nextCloseDelayMs <= 0L) {
            this.closeInventoryNow();
            return;
        }

        this.closeTimer.reset();
        this.waitingToCloseInventory = true;
    }

    private void handlePendingClose() {
        if (!this.waitingToCloseInventory) return;
        if (!this.autoClose.isEnabled()) {
            this.waitingToCloseInventory = false;
            return;
        }

        if (this.isMode(OpenMode.OPEN_INVENTORY) && !this.isInventoryScreenOpen()) {
            this.waitingToCloseInventory = false;
            return;
        }
        if (!this.closeTimer.hasReached(this.nextCloseDelayMs)) return;

        this.closeInventoryNow();
    }

    private void closeInventoryNow() {
        if (mc.player == null) {
            this.waitingToCloseInventory = false;
            return;
        }

        if (this.isInventoryScreenOpen()) {
            mc.player.closeHandledScreen();
        } else {
            ContainerUtils.closeContainer(mc.player.currentScreenHandler.syncId);
        }
        this.waitingToCloseInventory = false;
    }

    private void startSilentSequence(int sourceMenuSlot, int button) {
        if (this.isSilentActionActive()) return;

        this.pendingSilentSourceSlot = sourceMenuSlot;
        this.pendingSilentButton = button;
        this.silentStage = SilentStage.WAIT_BEFORE_OPEN;
        this.activeSilentMoveDelayMs = this.randomSilentDelayMs();
        this.silentMoveTimer.reset();
    }

    private void processSilentSequence() {
        if (this.silentStage == SilentStage.WAIT_BEFORE_OPEN) {
            if (!this.silentMoveTimer.hasReached(this.activeSilentMoveDelayMs)) return;

            mc.player.openRidingInventory();
            this.silentStage = SilentStage.WAIT_AFTER_OPEN;
            this.silentStageTickTimer.reset();
            return;
        }

        if (this.silentStage == SilentStage.WAIT_AFTER_OPEN) {
            if (!this.silentStageTickTimer.hasReachedTicks(1)) return;

            this.clickSwap(this.pendingSilentSourceSlot, this.pendingSilentButton, false);

            if (!this.autoClose.isEnabled()) {
                this.resetSilentState();
                return;
            }

            this.silentStage = SilentStage.WAIT_BEFORE_CLOSE;
            this.activeSilentMoveDelayMs = this.randomSilentDelayMs();
            this.silentMoveTimer.reset();
            return;
        }

        if (this.silentStage == SilentStage.WAIT_BEFORE_CLOSE) {
            if (!this.silentMoveTimer.hasReached(this.activeSilentMoveDelayMs)) return;
            ContainerUtils.closeContainer(mc.player.currentScreenHandler.syncId);
            this.resetSilentState();
        }
    }

    private boolean usesSelectionMode() {
        return this.isMode(OpenMode.OPEN_INVENTORY) || this.isMode(OpenMode.DONT_OPEN);
    }

    private boolean isAutomaticSubMode() {
        return this.usesSelectionMode() && this.selectionMode.getCurrentOption().equals(SelectionMode.AUTOMATIC.option);
    }

    private boolean isHoverSubMode() {
        return this.usesSelectionMode() && this.selectionMode.getCurrentOption().equals(SelectionMode.HOVER.option);
    }

    private boolean isCursorSimulationSubMode() {
        return this.usesSelectionMode() && this.selectionMode.getCurrentOption().equals(SelectionMode.CURSOR_SIMULATION.option);
    }

    private boolean isSilentActionActive() {
        return this.silentStage != SilentStage.IDLE;
    }

    private boolean isCursorSimulationActive() {
        return this.cursorAnimating || this.waitingForCursorSwap;
    }

    private boolean isMode(OpenMode openMode) {
        return this.mode.getCurrentOption().equals(openMode.option);
    }

    private void resetSilentState() {
        this.silentStage = SilentStage.IDLE;
        this.pendingSilentSourceSlot = -1;
        this.pendingSilentButton = -1;
        this.activeSilentMoveDelayMs = 0L;
    }

    private void resetCursorSimulationState() {
        this.cursorAnimating = false;
        this.waitingForCursorSwap = false;
        this.cursorAnimStartMs = 0L;
        this.cursorAnimDurationMs = 0L;
        this.pendingCursorSourceSlot = -1;
        this.pendingCursorButton = -1;
    }
}
