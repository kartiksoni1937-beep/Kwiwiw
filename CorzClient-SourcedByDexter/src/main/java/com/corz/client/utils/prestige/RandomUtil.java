package com.corz.client.utils.prestige;

import java.util.Random;

public class RandomUtil {
    public static final Random RANDOM = new Random();

    public static float randomFloat(float min, float max) {
        return clamp(min + RANDOM.nextFloat() * (max - min), min, max);
    }

    public static int randomInt(int min, int max) {
        return min + RANDOM.nextInt(max - min + 1);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
