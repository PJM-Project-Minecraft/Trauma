package ru.liko.trauma.bloodybits.client.render.layer;

import ru.liko.trauma.bloodybits.client.model.EntityInjuries;
import ru.liko.trauma.bloodybits.config.ClientConfig;
import ru.liko.trauma.bloodybits.utils.BloodyBitsUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.HexFormat;

@OnlyIn(Dist.CLIENT)
public class InjuryLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private static final float MAX_RGB_COLOR_VALUE = 255.0F;

    public InjuryLayer(LivingEntityRenderer<T, M> renderer) {
        super(renderer);
    }

    @Override
    public void render(@NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int pPackedLight,
            @NotNull T livingEntity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
            float netHeadYaw, float headPitch) {
        if (ClientConfig.showEntityDamage() && livingEntity.isAlive()
                && livingEntity.getHealth() < livingEntity.getMaxHealth()) {
            int entityId = livingEntity.getId();

            if (BloodyBitsUtils.INJURED_ENTITIES.containsKey(entityId)) {
                EntityInjuries entityInjuries = BloodyBitsUtils.INJURED_ENTITIES.get(entityId);

                if (entityInjuries.appliedSmallInjuries != null && !entityInjuries.appliedSmallInjuries.isEmpty()) {
                    for (var smallInjury : entityInjuries.appliedSmallInjuries.entrySet()) {
                        this.renderDamageLayerToBuffer(smallInjury.getValue(), smallInjury.getKey(), livingEntity,
                                bufferSource, poseStack, partialTicks, pPackedLight);
                    }
                }

                if (entityInjuries.appliedMediumInjuries != null && !entityInjuries.appliedMediumInjuries.isEmpty()) {
                    for (var mediumInjury : entityInjuries.appliedMediumInjuries.entrySet()) {
                        this.renderDamageLayerToBuffer(mediumInjury.getValue(), mediumInjury.getKey(), livingEntity,
                                bufferSource, poseStack, partialTicks, pPackedLight);
                    }
                }

                if (entityInjuries.appliedLargeInjuries != null && !entityInjuries.appliedLargeInjuries.isEmpty()) {
                    for (var largeInjury : entityInjuries.appliedLargeInjuries.entrySet()) {
                        this.renderDamageLayerToBuffer(largeInjury.getValue(), largeInjury.getKey(), livingEntity,
                                bufferSource, poseStack, partialTicks, pPackedLight);
                    }
                }
            }
        }
    }

    private void renderDamageLayerToBuffer(String injuryType, ResourceLocation damageLayerTexture, T entity,
            MultiBufferSource buffer, PoseStack poseStack, float pPartialTicks, int pPackedLight) {
        VertexConsumer customVertexConsumer = buffer.getBuffer(RenderType.entityTranslucent(damageLayerTexture));

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        boolean isEntityVisible = !entity.isInvisible();
        boolean canPlayerSeeInvisibleEntity = false;
        if (player != null) {
            canPlayerSeeInvisibleEntity = !isEntityVisible && !entity.isInvisibleTo(player);
        }

        String damageHexColor = "";
        switch (injuryType) {
            case "bleed" -> {
                String entityName = (entity instanceof Player) ? "player" : entity.getEncodeId();
                entityName = (entityName == null) ? "" : entityName;
                damageHexColor = BloodyBitsUtils.getEntityDamageHexColor(entityName);
            }
            case "burn" -> damageHexColor = ClientConfig.getBurnDamageColor();
        }

        if (damageHexColor != null && !damageHexColor.isBlank()) {
            float redDamage = HexFormat.fromHexDigits(damageHexColor, 1, 3) / MAX_RGB_COLOR_VALUE;
            float greenDamage = HexFormat.fromHexDigits(damageHexColor, 3, 5) / MAX_RGB_COLOR_VALUE;
            float blueDamage = HexFormat.fromHexDigits(damageHexColor.substring(5)) / MAX_RGB_COLOR_VALUE;

            int packedColor = (255 << 24)
                    | ((int) (redDamage * 255) << 16)
                    | ((int) (greenDamage * 255) << 8)
                    | (int) (blueDamage * 255);
            this.getParentModel().renderToBuffer(poseStack, customVertexConsumer, pPackedLight,
                    OverlayTexture.NO_OVERLAY,
                    packedColor
            );
        }
    }
}
