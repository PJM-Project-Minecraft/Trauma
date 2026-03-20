package ru.liko.trauma.common.system;

public enum HitZone {
    HEAD(2.0f),
    TORSO(1.0f),
    LIMBS(0.5f);

    private final float multiplier;

    HitZone(float multiplier) {
        this.multiplier = multiplier;
    }

    public float getMultiplier() {
        return this.multiplier;
    }
}
