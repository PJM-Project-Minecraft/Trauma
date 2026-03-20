package ru.liko.trauma.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import ru.liko.trauma.Trauma;
import ru.liko.trauma.client.input.ClientInputHandler;

/**
 * Анимация правой руки игрока при сдерживании кровотечения.
 * 
 * Подход:
 * 1. В RenderLivingEvent.Pre скрываем правую руку (rightArm.visible = false)
 * — вызывается ПОСЛЕ PlayerRenderer.setModelProperties(), значит не будет
 * перезаписано.
 * 2. В RenderLayer.render() рисуем руку с модифицированной ротацией.
 */
@EventBusSubscriber(modid = Trauma.MODID, value = Dist.CLIENT)
public class SuppressionArmLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    // Финальные значения ротации из in_hold_arm.animation.json (в радианах)
    private static final float TARGET_ROT_X = (float) Math.toRadians(-24.02393);
    private static final float TARGET_ROT_Y = (float) Math.toRadians(-48.20277);
    private static final float TARGET_ROT_Z = (float) Math.toRadians(-59.12537);

    private static final float LERP_SPEED = 0.15f;

    // Статические переменные для обмена состоянием между Pre-event и Layer
    private static float animProgress = 0f;
    private static boolean currentlySuppressing = false;

    public SuppressionArmLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    /**
     * Pre-event: скрываем правую руку до рендера модели, если игрок сдерживает.
     * PlayerRenderer.setModelProperties() вызывается ДО super.render() (где этот
     * event),
     * так что rightArm.visible = false НЕ будет перезаписано.
     */
    @SubscribeEvent
    public static void onPreRender(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player))
            return;
        if (player != Minecraft.getInstance().player)
            return;

        boolean isSuppressing = ClientInputHandler.isSuppressing();
        float target = isSuppressing ? 1f : 0f;
        animProgress = Mth.lerp(LERP_SPEED, animProgress, target);

        if (animProgress < 0.01f) {
            animProgress = 0f;
            currentlySuppressing = false;
            return;
        }

        currentlySuppressing = true;

        // Скрываем правую руку модели — она НЕ будет отрисована в renderToBuffer()
        var model = event.getRenderer().getModel();
        if (model instanceof PlayerModel<?> playerModel) {
            playerModel.rightArm.visible = false;
            playerModel.rightSleeve.visible = false;
        }
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
            AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
            float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {

        if (player != Minecraft.getInstance().player)
            return;

        var model = this.getParentModel();

        if (!currentlySuppressing) {
            // Если не сдерживаем — убедиться что рука видна (для нормального рендера)
            return;
        }

        // Сохраняем оригинальные значения
        float origXRot = model.rightArm.xRot;
        float origYRot = model.rightArm.yRot;
        float origZRot = model.rightArm.zRot;
        float origSleeveXRot = model.rightSleeve.xRot;
        float origSleeveYRot = model.rightSleeve.yRot;
        float origSleeveZRot = model.rightSleeve.zRot;

        // Interpolation between standard walking animation and fixed pose
        // сдерживания.
        // При animProgress=0 рука двигается нормально, при =1 рука замирает в позе из
        // анимации.
        model.rightArm.xRot = Mth.lerp(animProgress, origXRot, TARGET_ROT_X);
        model.rightArm.yRot = Mth.lerp(animProgress, origYRot, TARGET_ROT_Y);
        model.rightArm.zRot = Mth.lerp(animProgress, origZRot, TARGET_ROT_Z);
        model.rightSleeve.xRot = model.rightArm.xRot;
        model.rightSleeve.yRot = model.rightArm.yRot;
        model.rightSleeve.zRot = model.rightArm.zRot;

        // Делаем руку видимой и рисуем с новой ротацией
        model.rightArm.visible = true;
        model.rightSleeve.visible = true;

        var texture = player.getSkin().texture();
        var renderType = RenderType.entityTranslucent(texture);
        var vertexConsumer = buffer.getBuffer(renderType);
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);

        model.rightArm.render(poseStack, vertexConsumer, packedLight, overlay);
        model.rightSleeve.render(poseStack, vertexConsumer, packedLight, overlay);

        // Восстанавливаем оригинальные значения для других layers
        model.rightArm.xRot = origXRot;
        model.rightArm.yRot = origYRot;
        model.rightArm.zRot = origZRot;
        model.rightSleeve.xRot = origSleeveXRot;
        model.rightSleeve.yRot = origSleeveYRot;
        model.rightSleeve.zRot = origSleeveZRot;
    }
}
