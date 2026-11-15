package com.corz.client.utils.prestige;

public final class MathUtil {

    private MathUtil() {
    }

    public static double smoothStepLerp(double delta, double start, double end) {
        delta = Math.max(0.0, Math.min(1.0, delta));
        double t = delta * delta * (3.0 - 2.0 * delta);
        return start + (end - start) * t;
    }
}
