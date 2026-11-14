package com.corz.client.utils.prestige;

import com.corz.client.config.RangeSetting;

public class StopWatch {
    private long startTime = System.currentTimeMillis();

    public StopWatch() {
    }

    public boolean hasElapsed(float millis) {
        return (float)(System.currentTimeMillis() - this.startTime) > millis;
    }

    public float getElapsed() {
        return (float)(System.currentTimeMillis() - this.startTime);
    }

    public boolean hasElapsedRandom(RangeSetting delayRange) {
        return this.hasElapsed(RandomUtil.randomFloat((float)delayRange.getMin(), (float)delayRange.getMax()));
    }

    public void reset() {
        this.startTime = System.currentTimeMillis();
    }
}
