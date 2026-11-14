package com.corz.client.utils.prestige;

public class Rotation {
    private float yaw;
    private float pitch;
    private boolean clamped;

    public Rotation(float initialYaw, float initialPitch) {
        this.yaw = initialYaw;
        this.pitch = initialPitch;
    }

    public float getYaw() {
        return this.yaw;
    }

    public void setYaw(float newYaw) {
        this.yaw = newYaw;
    }

    public float getPitch() {
        return this.pitch;
    }

    public void setPitch(float newPitch) {
        this.pitch = newPitch;
    }

    public boolean isClamped() {
        return this.clamped;
    }

    public void setClamped(boolean wasClamped) {
        this.clamped = wasClamped;
    }
}
