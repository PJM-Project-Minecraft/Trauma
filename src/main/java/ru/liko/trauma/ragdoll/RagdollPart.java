package ru.liko.trauma.ragdoll;

import org.joml.Vector3f;

/**
 * Enum representing the 6 body parts of a ragdoll.
 * Each part has a unique index and half-extents for its collision box.
 */
public enum RagdollPart {
    TORSO(0),
    HEAD(1),
    LEFT_LEG(2),
    RIGHT_LEG(3),
    LEFT_ARM(4),
    RIGHT_ARM(5);

    public final int index;

    RagdollPart(int i) {
        this.index = i;
    }

    public static RagdollPart byIndex(int i) {
        for (RagdollPart p : values()) {
            if (p.index == i) return p;
        }
        return null;
    }

    /**
     * Returns the half-extents (half-size) of the collision box for this body part.
     * Units are in Minecraft blocks (1 block = 1 meter).
     */
    public Vector3f getHalfExtents() {
        return switch (this) {
            case HEAD -> new Vector3f(0.2f, 0.2f, 0.2f);
            case TORSO -> new Vector3f(0.25f, 0.4f, 0.15f);
            case LEFT_ARM, RIGHT_ARM -> new Vector3f(0.1f, 0.35f, 0.1f);
            case LEFT_LEG, RIGHT_LEG -> new Vector3f(0.15f, 0.45f, 0.15f);
        };
    }
}
