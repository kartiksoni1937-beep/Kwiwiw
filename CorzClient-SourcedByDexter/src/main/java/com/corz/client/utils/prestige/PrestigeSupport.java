package com.corz.client.utils.prestige;

import net.minecraft.client.MinecraftClient;

public final class PrestigeSupport {
    public static final FrameDeltaTimer frameDeltaTimer = new FrameDeltaTimer();
    public static final SilentRotationManager silentRotationManager = new SilentRotationManager();
    public static final VisualRotationManager visualRotationManager = new VisualRotationManager();

    private PrestigeSupport() {
    }

    public static float getServerYaw() {
        Rotation rotation = silentRotationManager.getServerRotation();
        return rotation != null ? rotation.getYaw() : (MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getYaw() : 0.0F);
    }

    public static float getServerPitch() {
        Rotation rotation = silentRotationManager.getServerRotation();
        return rotation != null ? rotation.getPitch() : (MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getPitch() : 0.0F);
    }
}
