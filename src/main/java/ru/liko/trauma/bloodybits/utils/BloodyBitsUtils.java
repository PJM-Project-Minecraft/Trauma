package ru.liko.trauma.bloodybits.utils;

import ru.liko.trauma.bloodybits.entity.BloodSprayEntity;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class BloodyBitsUtils {
    public static ConcurrentLinkedDeque<BloodSprayEntity> BLOOD_SPRAY_ENTITIES = new ConcurrentLinkedDeque<>();
    public static ConcurrentHashMap<Integer, BloodSprayEntity> CLIENT_SIDE_BLOOD_SPRAYS = new ConcurrentHashMap<>();

    public static void vertex(Matrix4f pMatrix, Matrix3f pNormal, VertexConsumer pConsumer, float pX, float pY,
            float pZ, float pU, float pV, int pNormalX, int pNormalZ, int pNormalY, int packedLight, int red, int green,
            int blue, int alpha) {
        pConsumer
                .addVertex(pMatrix, pX, pY, pZ)
                .setColor(red, green, blue, alpha)
                .setUv(pU, pV)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pNormalX, pNormalY, pNormalZ);
    }

    public static SoundEvent getRandomSound(int randomNumber) {
        return switch (randomNumber) {
            case 1 -> SoundEvents.MUD_HIT;
            case 2 -> SoundEvents.WET_GRASS_HIT;
            default -> SoundEvents.MUD_STEP;
        };
    }
}
