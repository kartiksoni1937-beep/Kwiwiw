package com.corz.client.utils.prestige;

public class FrameDeltaTimer {
    private long lastFrameTime;
    private long frameTimeMs;

    public FrameDeltaTimer() {
        super();
        this.lastFrameTime = 0L;
        this.frameTimeMs = 0L;
    }

    public void tick() {
        this.frameTimeMs = System.currentTimeMillis() - this.lastFrameTime;
        this.lastFrameTime = System.currentTimeMillis();
        if ((float)this.frameTimeMs > 30.0F) {
            this.frameTimeMs = 0L;
        }
    }

    public float getAnimationDelta() {
        return (float)this.frameTimeMs * 0.005F;
    }
}
