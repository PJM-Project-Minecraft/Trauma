package ru.liko.trauma.common.system;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class HitZoneCalculator {

    public static HitZone determineHitZone(LivingEntity victim, Vec3 hitVec) {
        AABB box = victim.getBoundingBox();

        double totalHeight = box.getYsize();
        double hitY = hitVec.y - box.minY;
        double percentY = hitY / totalHeight;

        boolean isCrouching = victim.isCrouching();
        double headThreshold = isCrouching ? 0.75 : 0.80;
        double torsoThreshold = isCrouching ? 0.40 : 0.45;

        // FirstAid pancake hitboxes (Y only splits)
        if (percentY >= headThreshold) {
            return HitZone.HEAD; // Head
        } else if (percentY >= torsoThreshold) {
            return HitZone.TORSO; // Torso
        } else {
            return HitZone.LIMBS; // Limbs (Legs + Feet)
        }
    }
}
