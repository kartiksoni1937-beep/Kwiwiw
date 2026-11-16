package com.corz.client.utils.prestige.player;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

public final class InputHandler {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private InputHandler() {
    }

    public static boolean isBindDown(int bind) {
        if (bind == -1 || bind == 0) return false;
        if (mc.getWindow() == null) return false;
        long handle = mc.getWindow().getHandle();
        if ((bind & 0x80000000) != 0) {
            return GLFW.glfwGetMouseButton(handle, bind & 0xFF) == GLFW.GLFW_PRESS;
        }
        return GLFW.glfwGetKey(handle, bind) == GLFW.GLFW_PRESS;
    }

    public static boolean isMouseDown(int button) {
        if (mc.getWindow() == null) return false;
        return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), button) == GLFW.GLFW_PRESS;
    }

    public static boolean isKeyDown(int key) {
        if (mc.getWindow() == null) return false;
        return GLFW.glfwGetKey(mc.getWindow().getHandle(), key) == GLFW.GLFW_PRESS;
    }

    public static boolean isWindowFocused() {
        return mc.getWindow() != null
                && GLFW.glfwGetWindowAttrib(mc.getWindow().getHandle(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
    }

    public static void simulateClick(KeyBinding keyBinding, boolean useSimulation) {
        if (!useSimulation || keyBinding == null || !isWindowFocused()) return;
        KeyBinding.setKeyPressed(keyBinding.getDefaultKey(), true);
        KeyBinding.setKeyPressed(keyBinding.getDefaultKey(), false);
    }
}
