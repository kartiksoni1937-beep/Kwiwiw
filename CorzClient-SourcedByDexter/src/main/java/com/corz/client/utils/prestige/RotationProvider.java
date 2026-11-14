package com.corz.client.utils.prestige;

public interface RotationProvider {
    Rotation provideRotation();

    default int getRotationPriority() {
        return 0;
    }
}
