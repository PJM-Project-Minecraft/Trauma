package ru.liko.trauma.bloodybits.utils;

import ru.liko.trauma.bloodybits.client.model.EntityInjuries;
import ru.liko.trauma.bloodybits.config.ClientConfig;
import ru.liko.trauma.bloodybits.config.CommonConfig;
import ru.liko.trauma.bloodybits.entity.BloodSprayEntity;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

public class BloodyBitsUtils {
    public static ConcurrentLinkedDeque<BloodSprayEntity> BLOOD_SPRAY_ENTITIES = new ConcurrentLinkedDeque<>();
    public static ConcurrentHashMap<Integer, BloodSprayEntity> CLIENT_SIDE_BLOOD_SPRAYS = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<Integer, EntityInjuries> INJURED_ENTITIES = new ConcurrentHashMap<>();
    public static final CopyOnWriteArrayList<String> INJURY_LAYER_ENTITIES = new CopyOnWriteArrayList<>();

    public static void vertex(Matrix4f pMatrix, Matrix3f pNormal, VertexConsumer pConsumer, float pX, float pY,
            float pZ, float pU, float pV, int pNormalX, int pNormalZ, int pNormalY, int packedLight, int red, int green,
            int blue, int alpha) {
        pConsumer
                .addVertex(pMatrix, pX, pY, pZ)
                .setColor(red, green, blue, alpha)
                .setUv(pU, pV)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pNormalX, pNormalY, pNormalZ); // In 1.21.1 vertex builder
                                                          // ends
                                                          // implicitly
    }

    public static SoundEvent getRandomSound(int randomNumber) {
        return switch (randomNumber) {
            case 1 -> SoundEvents.MUD_HIT;
            case 2 -> SoundEvents.WET_GRASS_HIT;
            default -> SoundEvents.MUD_STEP;
        };
    }

    public static String[] decompose(String pLocation, char pSeparator) {
        String[] astring = new String[] { "minecraft", pLocation };
        int i = pLocation.indexOf(pSeparator);
        if (i >= 0) {
            astring[1] = pLocation.substring(i + 1);
            if (i >= 1) {
                astring[0] = pLocation.substring(0, i);
            }
        }
        return astring;
    }

    public static String getEntityDamageHexColor(String entityName) {
        String damageHexColor = "#c80000";

        if (CommonConfig.solidEntities().contains(entityName)) {
            damageHexColor = "#ffffff";
        } else {
            for (Map.Entry<String, List<String>> mapElement : ClientConfig.entityBloodColors().entrySet()) {
                if (mapElement.getValue().contains(Objects.requireNonNull(entityName))) {
                    damageHexColor = mapElement.getKey();
                    break;
                }
            }
        }
        return damageHexColor;
    }
}
