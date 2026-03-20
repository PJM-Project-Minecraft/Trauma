package ru.liko.trauma.bloodybits.client.render;

import ru.liko.trauma.Trauma;
import ru.liko.trauma.bloodybits.config.CommonConfig;
import ru.liko.trauma.bloodybits.entity.BloodSprayEntity;
import ru.liko.trauma.bloodybits.utils.BloodyBitsUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class BloodSprayRenderer extends EntityRenderer<BloodSprayEntity> {
        public static final ResourceLocation SPRAY = ResourceLocation.fromNamespaceAndPath(Trauma.MODID,
                        "textures/entity/blood_projectile/spray.png");

        public BloodSprayRenderer(EntityRendererProvider.Context context) {
                super(context);
        }

        @Override
        public void render(BloodSprayEntity entity, float pEntityYaw, float pPartialTicks, PoseStack pPoseStack,
                        @NotNull MultiBufferSource pBuffer, int pPackedLight) {
                int correctedPackedLight = Math.max(pPackedLight, 10485776);
                pPoseStack.pushPose();

                if (entity.entityDirection == null) {
                        pPoseStack
                                        .mulPose(Axis.YP.rotationDegrees(
                                                        Mth.lerp(pPartialTicks, entity.yRotO, entity.getYRot())
                                                                        - 90.0F));
                        pPoseStack.mulPose(Axis.ZP
                                        .rotationDegrees(Mth.lerp(pPartialTicks, entity.xRotO, entity.getXRot())));
                } else if (entity.entityDirection.equals(Direction.NORTH)
                                || entity.entityDirection.equals(Direction.SOUTH)) {
                        pPoseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
                } else if (entity.entityDirection.equals(Direction.UP)
                                || entity.entityDirection.equals(Direction.DOWN)) {
                        pPoseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
                }

                int alpha = (int) (225 - (((double) entity.getLife() / CommonConfig.despawnTime()) * 225));
                alpha = Math.max(0, alpha);

                pPoseStack.scale(0.05625F, 0.05625F, 0.05625F);
                VertexConsumer vertexConsumer = pBuffer
                                .getBuffer(RenderType.entityTranslucent(getTextureLocation(entity)));
                PoseStack.Pose posestack$pose = pPoseStack.last();
                Matrix4f matrix4f = posestack$pose.pose();
                Matrix3f matrix3f = posestack$pose.normal();

                if (entity.xMin < entity.xMax) {
                        BloodyBitsUtils.vertex(matrix4f, matrix3f, vertexConsumer, entity.xMin, entity.yMin,
                                        entity.zMax, 0.5F,
                                        0.0F, 1, 1, 1, correctedPackedLight, entity.red, entity.green, entity.blue,
                                        alpha);
                        BloodyBitsUtils.vertex(matrix4f, matrix3f, vertexConsumer, entity.xMax, entity.yMin,
                                        entity.zMax, 0.0F,
                                        0.0F, 1, 1, 1, correctedPackedLight, entity.red, entity.green, entity.blue,
                                        alpha);
                        BloodyBitsUtils.vertex(matrix4f, matrix3f, vertexConsumer, entity.xMax, entity.yMax,
                                        entity.zMax, 0.0F,
                                        0.125F, 1, 1, 1, correctedPackedLight, entity.red, entity.green, entity.blue,
                                        alpha);
                        BloodyBitsUtils.vertex(matrix4f, matrix3f, vertexConsumer, entity.xMin, entity.yMax,
                                        entity.zMax, 0.5F,
                                        0.125F, 1, 1, 1, correctedPackedLight, entity.red, entity.green, entity.blue,
                                        alpha);

                        BloodyBitsUtils.vertex(matrix4f, matrix3f, vertexConsumer, entity.xMin, entity.yMax,
                                        entity.zMin, 0.5F,
                                        0.0F, 1, 1, 1, correctedPackedLight, entity.red, entity.green, entity.blue,
                                        alpha);
                        BloodyBitsUtils.vertex(matrix4f, matrix3f, vertexConsumer, entity.xMax, entity.yMax,
                                        entity.zMin, 0.0F,
                                        0.0F, 1, 1, 1, correctedPackedLight, entity.red, entity.green, entity.blue,
                                        alpha);
                        BloodyBitsUtils.vertex(matrix4f, matrix3f, vertexConsumer, entity.xMax, entity.yMin,
                                        entity.zMin, 0.0F,
                                        0.125F, 1, 1, 1, correctedPackedLight, entity.red, entity.green, entity.blue,
                                        alpha);
                        BloodyBitsUtils.vertex(matrix4f, matrix3f, vertexConsumer, entity.xMin, entity.yMin,
                                        entity.zMin, 0.5F,
                                        0.125F, 1, 1, 1, correctedPackedLight, entity.red, entity.green, entity.blue,
                                        alpha);

                        BloodyBitsUtils.vertex(matrix4f, matrix3f, vertexConsumer, entity.xMin, entity.yMax,
                                        entity.zMin, 0.5F,
                                        0.0F, 1, 1, 1, correctedPackedLight, entity.red, entity.green, entity.blue,
                                        alpha);
                        BloodyBitsUtils.vertex(matrix4f, matrix3f, vertexConsumer, entity.xMax, entity.yMax,
                                        entity.zMin, 0.0F,
                                        0.0F, 1, 1, 1, correctedPackedLight, entity.red, entity.green, entity.blue,
                                        alpha);
                        BloodyBitsUtils.vertex(matrix4f, matrix3f, vertexConsumer, entity.xMax, entity.yMax,
                                        entity.zMax, 0.0F,
                                        0.125F, 1, 1, 1, correctedPackedLight, entity.red, entity.green, entity.blue,
                                        alpha);
                        BloodyBitsUtils.vertex(matrix4f, matrix3f, vertexConsumer, entity.xMin, entity.yMax,
                                        entity.zMax, 0.5F,
                                        0.125F, 1, 1, 1, correctedPackedLight, entity.red, entity.green, entity.blue,
                                        alpha);

                        BloodyBitsUtils.vertex(matrix4f, matrix3f, vertexConsumer, entity.xMin, entity.yMin,
                                        entity.zMin, 0.5F,
                                        0.0F, 1, 1, 1, correctedPackedLight, entity.red, entity.green, entity.blue,
                                        alpha);
                        BloodyBitsUtils.vertex(matrix4f, matrix3f, vertexConsumer, entity.xMax, entity.yMin,
                                        entity.zMin, 0.0F,
                                        0.0F, 1, 1, 1, correctedPackedLight, entity.red, entity.green, entity.blue,
                                        alpha);
                        BloodyBitsUtils.vertex(matrix4f, matrix3f, vertexConsumer, entity.xMax, entity.yMin,
                                        entity.zMax, 0.0F,
                                        0.125F, 1, 1, 1, correctedPackedLight, entity.red, entity.green, entity.blue,
                                        alpha);
                        BloodyBitsUtils.vertex(matrix4f, matrix3f, vertexConsumer, entity.xMin, entity.yMin,
                                        entity.zMax, 0.5F,
                                        0.125F, 1, 1, 1, correctedPackedLight, entity.red, entity.green, entity.blue,
                                        alpha);

                        BloodyBitsUtils.vertex(matrix4f, matrix3f, vertexConsumer, entity.xMin, entity.yMax,
                                        entity.zMin, 0.375F,
                                        0.0F, 1, 1, 1, correctedPackedLight, entity.red, entity.green, entity.blue,
                                        alpha);
                        BloodyBitsUtils.vertex(matrix4f, matrix3f, vertexConsumer, entity.xMin, entity.yMin,
                                        entity.zMin, 0.375F,
                                        0.125F, 1, 1, 1, correctedPackedLight, entity.red, entity.green, entity.blue,
                                        alpha);
                        BloodyBitsUtils.vertex(matrix4f, matrix3f, vertexConsumer, entity.xMin, entity.yMin,
                                        entity.zMax, 0.5F,
                                        0.125F, 1, 1, 1, correctedPackedLight, entity.red, entity.green, entity.blue,
                                        alpha);
                        BloodyBitsUtils.vertex(matrix4f, matrix3f, vertexConsumer, entity.xMin, entity.yMax,
                                        entity.zMax, 0.5F,
                                        0.125F, 1, 1, 1, correctedPackedLight, entity.red, entity.green, entity.blue,
                                        alpha);

                        BloodyBitsUtils.vertex(matrix4f, matrix3f, vertexConsumer, entity.xMax, entity.yMax,
                                        entity.zMin, 0.0F,
                                        0.0F, 1, 1, 1, correctedPackedLight, entity.red, entity.green, entity.blue,
                                        alpha);
                        BloodyBitsUtils.vertex(matrix4f, matrix3f, vertexConsumer, entity.xMax, entity.yMax,
                                        entity.zMax, 0.125F,
                                        0.0F, 1, 1, 1, correctedPackedLight, entity.red, entity.green, entity.blue,
                                        alpha);
                        BloodyBitsUtils.vertex(matrix4f, matrix3f, vertexConsumer, entity.xMax, entity.yMin,
                                        entity.zMax, 0.125F,
                                        0.125F, 1, 1, 1, correctedPackedLight, entity.red, entity.green, entity.blue,
                                        alpha);
                        BloodyBitsUtils.vertex(matrix4f, matrix3f, vertexConsumer, entity.xMax, entity.yMin,
                                        entity.zMin, 0.0F,
                                        0.125F, 1, 1, 1, correctedPackedLight, entity.red, entity.green, entity.blue,
                                        alpha);
                } else {

                        BloodyBitsUtils.vertex(matrix4f, matrix3f, vertexConsumer, entity.xMin, entity.yMax,
                                        entity.zMin, 0.0F,
                                        0.0F, 1, 1, 1, correctedPackedLight, entity.red, entity.green, entity.blue,
                                        alpha);
                        BloodyBitsUtils.vertex(matrix4f, matrix3f, vertexConsumer, entity.xMin, entity.yMin,
                                        entity.zMin, 0.0F,
                                        1.0F, 1, 1, 1, correctedPackedLight, entity.red, entity.green, entity.blue,
                                        alpha);
                        BloodyBitsUtils.vertex(matrix4f, matrix3f, vertexConsumer, entity.xMin, entity.yMin,
                                        entity.zMax, 1.0F,
                                        1.0F, 1, 1, 1, correctedPackedLight, entity.red, entity.green, entity.blue,
                                        alpha);
                        BloodyBitsUtils.vertex(matrix4f, matrix3f, vertexConsumer, entity.xMin, entity.yMax,
                                        entity.zMax, 1.0F,
                                        0.0F, 1, 1, 1, correctedPackedLight, entity.red, entity.green, entity.blue,
                                        alpha);

                        if (entity.getLife() > 50 && entity.entityDirection != null
                                        && entity.entityDirection.equals(Direction.DOWN)) {
                                VertexConsumer dripVertexConsumer = pBuffer
                                                .getBuffer(RenderType.entityTranslucent(SPRAY));
                                float zPos = (Math.abs(entity.zMax - entity.zMin) / 2) + entity.zMin;
                                float yPos = (Math.abs(entity.yMax - entity.yMin) / 2) + entity.yMin;
                                float thickness = (entity.drip * 0.01F);
                                alpha = (int) (255 - (255 * (entity.drip / BloodSprayEntity.MAX_DRIP_LENGTH)));

                                BloodyBitsUtils.vertex(matrix4f, matrix3f, dripVertexConsumer, entity.xMax, yPos,
                                                zPos - 0.5F + thickness, 0.5F, 0.0F, 1, 1, 1, correctedPackedLight,
                                                entity.red, entity.green,
                                                entity.blue, alpha);
                                BloodyBitsUtils.vertex(matrix4f, matrix3f, dripVertexConsumer, -entity.drip, yPos,
                                                zPos - 0.5F + thickness, 0.0F, 0.0F, 1, 1, 1, correctedPackedLight,
                                                entity.red, entity.green,
                                                entity.blue, alpha);
                                BloodyBitsUtils.vertex(matrix4f, matrix3f, dripVertexConsumer, -entity.drip, yPos,
                                                zPos + 0.5F - thickness, 0.0F, 0.125F, 1, 1, 1, correctedPackedLight,
                                                entity.red, entity.green,
                                                entity.blue, alpha);
                                BloodyBitsUtils.vertex(matrix4f, matrix3f, dripVertexConsumer, entity.xMax, yPos,
                                                zPos + 0.5F - thickness, 0.5F, 0.125F, 1, 1, 1, correctedPackedLight,
                                                entity.red, entity.green,
                                                entity.blue, alpha);

                                BloodyBitsUtils.vertex(matrix4f, matrix3f, dripVertexConsumer, entity.xMax,
                                                yPos - 0.5F + thickness, zPos,
                                                0.5F, 0.0F, 1, 1, 1, correctedPackedLight, entity.red, entity.green,
                                                entity.blue, alpha);
                                BloodyBitsUtils.vertex(matrix4f, matrix3f, dripVertexConsumer, -entity.drip,
                                                yPos - 0.5F + thickness, zPos,
                                                0.0F, 0.0F, 1, 1, 1, correctedPackedLight, entity.red, entity.green,
                                                entity.blue, alpha);
                                BloodyBitsUtils.vertex(matrix4f, matrix3f, dripVertexConsumer, -entity.drip,
                                                yPos + 0.5F - thickness, zPos,
                                                0.0F, 0.125F, 1, 1, 1, correctedPackedLight, entity.red, entity.green,
                                                entity.blue, alpha);
                                BloodyBitsUtils.vertex(matrix4f, matrix3f, dripVertexConsumer, entity.xMax,
                                                yPos + 0.5F - thickness, zPos,
                                                0.5F, 0.125F, 1, 1, 1, correctedPackedLight, entity.red, entity.green,
                                                entity.blue, alpha);
                        }
                }
                pPoseStack.popPose();
        }

        @Override
        public ResourceLocation getTextureLocation(BloodSprayEntity bloodSprayEntity) {
                if (!bloodSprayEntity.isSolid && bloodSprayEntity.inGround) {
                        return this.getRandomSpatterTexture(bloodSprayEntity.randomTextureNumber);
                } else {
                        return SPRAY;
                }
        }

        private ResourceLocation getRandomSpatterTexture(int randomInt) {
                return switch (randomInt) {
                        case 1 ->
                                ResourceLocation.fromNamespaceAndPath(Trauma.MODID,
                                                "textures/entity/blood_spatter/spatter_1.png");
                        case 2 ->
                                ResourceLocation.fromNamespaceAndPath(Trauma.MODID,
                                                "textures/entity/blood_spatter/spatter_2.png");
                        case 3 ->
                                ResourceLocation.fromNamespaceAndPath(Trauma.MODID,
                                                "textures/entity/blood_spatter/spatter_3.png");
                        case 4 ->
                                ResourceLocation.fromNamespaceAndPath(Trauma.MODID,
                                                "textures/entity/blood_spatter/spatter_4.png");
                        case 5 ->
                                ResourceLocation.fromNamespaceAndPath(Trauma.MODID,
                                                "textures/entity/blood_spatter/spatter_5.png");
                        case 6 ->
                                ResourceLocation.fromNamespaceAndPath(Trauma.MODID,
                                                "textures/entity/blood_spatter/spatter_6.png");
                        case 7 ->
                                ResourceLocation.fromNamespaceAndPath(Trauma.MODID,
                                                "textures/entity/blood_spatter/spatter_7.png");
                        default ->
                                ResourceLocation.fromNamespaceAndPath(Trauma.MODID,
                                                "textures/entity/blood_spatter/spatter_0.png");
                };
        }
}
