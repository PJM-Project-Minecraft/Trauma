package ru.liko.trauma.client.ragdoll;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.joml.Quaternionf;
import ru.liko.trauma.Trauma;
import ru.liko.trauma.common.entity.MannequinEntity;
import ru.liko.trauma.ragdoll.RagdollPart;
import ru.liko.trauma.ragdoll.RagdollTransform;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client-side renderer that replaces the normal player model with ragdoll
 * skin parts only (no armor or accessory overlays).
 */
@EventBusSubscriber(modid = Trauma.MODID, value = Dist.CLIENT)
public class RagdollPlayerRenderer {

    /** Ragdolls already rendered by onRenderPlayer/onRenderLiving — skip in onRenderLevelStage. */
    private static final Set<Integer> renderedThisFrame = new HashSet<>();

    /** Humanoid model used for orphaned ragdolls (entity gone after respawn). */
    private static HumanoidModel<?> orphanModel = null;

    private static HumanoidModel<?> getOrphanModel() {
        if (orphanModel == null) {
            orphanModel = new HumanoidModel<>(
                    Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER));
        }
        return orphanModel;
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        AbstractClientPlayer player = (AbstractClientPlayer) event.getEntity();
        ClientRagdollManager.ClientRagdoll rag = ClientRagdollManager.get(player.getId());

        if (rag != null) {
            rag.cachePlayerUUID(player.getUUID());
            rag.cacheSkinTexture(player.getSkin().texture());
        }

        if (rag == null || !rag.isActive()) return;

        PlayerRenderer renderer = event.getRenderer();
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource buffer = event.getMultiBufferSource();
        float partial = event.getPartialTick();
        int light = event.getPackedLight();

        RagdollTransform torso = rag.getPartInterpolated(RagdollPart.TORSO, partial);
        if (torso == null) return;

        event.setCanceled(true);
        renderedThisFrame.add(rag.playerEntityId);

        RagdollTransform head = rag.getPartInterpolated(RagdollPart.HEAD, partial);
        RagdollTransform larm = rag.getPartInterpolated(RagdollPart.LEFT_ARM, partial);
        RagdollTransform rarm = rag.getPartInterpolated(RagdollPart.RIGHT_ARM, partial);
        RagdollTransform lleg = rag.getPartInterpolated(RagdollPart.LEFT_LEG, partial);
        RagdollTransform rleg = rag.getPartInterpolated(RagdollPart.RIGHT_LEG, partial);

        ResourceLocation skin = player.getSkin().texture();
        Vec3 camPos = new Vec3(
                Mth.lerp(partial, player.xo, player.getX()),
                Mth.lerp(partial, player.yo, player.getY()),
                Mth.lerp(partial, player.zo, player.getZ()));

        VertexConsumer skinVc = buffer.getBuffer(RenderType.entityTranslucent(skin));

        renderRagdollPart(poseStack, skinVc, model.body, torso, camPos, RagdollPart.TORSO, light);
        renderRagdollPart(poseStack, skinVc, model.head, head, camPos, RagdollPart.HEAD, light);
        renderRagdollPart(poseStack, skinVc, model.leftLeg, lleg, camPos, RagdollPart.LEFT_LEG, light);
        renderRagdollPart(poseStack, skinVc, model.rightLeg, rleg, camPos, RagdollPart.RIGHT_LEG, light);
        renderRagdollPart(poseStack, skinVc, model.leftArm, larm, camPos, RagdollPart.LEFT_ARM, light);
        renderRagdollPart(poseStack, skinVc, model.rightArm, rarm, camPos, RagdollPart.RIGHT_ARM, light);
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof MannequinEntity)) return;

        ClientRagdollManager.ClientRagdoll rag = ClientRagdollManager.get(entity.getId());
        if (rag == null || !rag.isActive()) return;

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource buffer = event.getMultiBufferSource();
        float partial = event.getPartialTick();
        int light = event.getPackedLight();

        RagdollTransform torso = rag.getPartInterpolated(RagdollPart.TORSO, partial);
        if (torso == null) return;

        event.setCanceled(true);
        renderedThisFrame.add(rag.playerEntityId);

        RagdollTransform head = rag.getPartInterpolated(RagdollPart.HEAD, partial);
        RagdollTransform larm = rag.getPartInterpolated(RagdollPart.LEFT_ARM, partial);
        RagdollTransform rarm = rag.getPartInterpolated(RagdollPart.RIGHT_ARM, partial);
        RagdollTransform lleg = rag.getPartInterpolated(RagdollPart.LEFT_LEG, partial);
        RagdollTransform rleg = rag.getPartInterpolated(RagdollPart.RIGHT_LEG, partial);

        ResourceLocation skin = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");
        Vec3 camPos = new Vec3(
                Mth.lerp(partial, entity.xo, entity.getX()),
                Mth.lerp(partial, entity.yo, entity.getY()),
                Mth.lerp(partial, entity.zo, entity.getZ()));

        if (event.getRenderer().getModel() instanceof HumanoidModel<?> model) {
            VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(skin));
            renderRagdollPart(poseStack, vc, model.body, torso, camPos, RagdollPart.TORSO, light);
            renderRagdollPart(poseStack, vc, model.head, head, camPos, RagdollPart.HEAD, light);
            renderRagdollPart(poseStack, vc, model.leftLeg, lleg, camPos, RagdollPart.LEFT_LEG, light);
            renderRagdollPart(poseStack, vc, model.rightLeg, rleg, camPos, RagdollPart.RIGHT_LEG, light);
            renderRagdollPart(poseStack, vc, model.leftArm, larm, camPos, RagdollPart.LEFT_ARM, light);
            renderRagdollPart(poseStack, vc, model.rightArm, rarm, camPos, RagdollPart.RIGHT_ARM, light);
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Vec3 cameraPos = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);

        List<Integer> staleIds = new ArrayList<>();
        for (ClientRagdollManager.ClientRagdoll rag : ClientRagdollManager.getAll()) {
            if (!rag.isActive()) {
                if (rag.hasReceivedUpdate()) staleIds.add(rag.playerEntityId);
                continue;
            }
            if (renderedThisFrame.contains(rag.playerEntityId)) continue;

            Entity existingEntity = mc.level.getEntity(rag.playerEntityId);
            if (existingEntity instanceof LivingEntity living && living.isAlive()) continue;

            renderOrphanedRagdoll(rag, event.getPoseStack(), cameraPos, partial);
        }
        renderedThisFrame.clear();
        for (int staleId : staleIds) ClientRagdollManager.remove(staleId);
    }

    private static void renderOrphanedRagdoll(
            ClientRagdollManager.ClientRagdoll rag,
            PoseStack poseStack,
            Vec3 cameraPos,
            float partial) {
        HumanoidModel<?> model = getOrphanModel();

        ResourceLocation skin = ClientRagdollManager.resolveSkinFromUUID(rag.getCachedPlayerUUID());
        if (skin != null) {
            rag.cacheSkinTexture(skin);
        } else {
            skin = rag.getCachedSkinTexture();
        }
        if (skin == null) {
            skin = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");
        }

        RagdollTransform torso = rag.getPartInterpolated(RagdollPart.TORSO, partial);
        if (torso == null) return;

        BlockPos lightPos = BlockPos.containing(torso.position.x, torso.position.y, torso.position.z);
        int light = LevelRenderer.getLightColor(Minecraft.getInstance().level, lightPos);

        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucent(skin));

        renderRagdollPart(poseStack, vc, model.body, torso, cameraPos, RagdollPart.TORSO, light);
        renderRagdollPart(poseStack, vc, model.head,
                rag.getPartInterpolated(RagdollPart.HEAD, partial), cameraPos, RagdollPart.HEAD, light);
        renderRagdollPart(poseStack, vc, model.leftLeg,
                rag.getPartInterpolated(RagdollPart.LEFT_LEG, partial), cameraPos, RagdollPart.LEFT_LEG, light);
        renderRagdollPart(poseStack, vc, model.rightLeg,
                rag.getPartInterpolated(RagdollPart.RIGHT_LEG, partial), cameraPos, RagdollPart.RIGHT_LEG, light);
        renderRagdollPart(poseStack, vc, model.leftArm,
                rag.getPartInterpolated(RagdollPart.LEFT_ARM, partial), cameraPos, RagdollPart.LEFT_ARM, light);
        renderRagdollPart(poseStack, vc, model.rightArm,
                rag.getPartInterpolated(RagdollPart.RIGHT_ARM, partial), cameraPos, RagdollPart.RIGHT_ARM, light);

        bufferSource.endBatch();
    }

    private static void applyRagdollPartTransform(
            PoseStack poseStack,
            RagdollTransform transform,
            Vec3 camPos,
            RagdollPart ragdollPart) {
        poseStack.translate(
                transform.position.x - camPos.x,
                transform.position.y - camPos.y,
                transform.position.z - camPos.z);

        Quaternionf q = new Quaternionf(
                transform.rotation.x, transform.rotation.y,
                transform.rotation.z, transform.rotation.w);
        poseStack.mulPose(q);

        q.identity();
        q.rotateZ((float) Math.PI);
        poseStack.mulPose(q);

        switch (ragdollPart) {
            case HEAD -> poseStack.translate(0, 4.0f / 16.0f, 0);
            case TORSO -> poseStack.translate(0, -6.0f / 16.0f, 0);
            case LEFT_ARM -> poseStack.translate(-1.0f / 16.0f, -4.0f / 16.0f, 0);
            case RIGHT_ARM -> poseStack.translate(1.0f / 16.0f, -4.0f / 16.0f, 0);
            case LEFT_LEG, RIGHT_LEG -> poseStack.translate(0, -6.0f / 16.0f, 0);
        }
    }

    /**
     * Render a single ragdoll body part (skin layer only).
     */
    public static void renderRagdollPart(
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            ModelPart part,
            @Nullable RagdollTransform transform,
            Vec3 camPos,
            RagdollPart ragdollPart,
            int light) {
        if (transform == null) return;

        float oldX = part.x, oldY = part.y, oldZ = part.z;
        float oldXRot = part.xRot, oldYRot = part.yRot, oldZRot = part.zRot;

        poseStack.pushPose();
        applyRagdollPartTransform(poseStack, transform, camPos, ragdollPart);

        part.setPos(0, 0, 0);
        part.xRot = 0;
        part.yRot = 0;
        part.zRot = 0;
        part.render(poseStack, vertexConsumer, light, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();

        part.x = oldX;
        part.y = oldY;
        part.z = oldZ;
        part.xRot = oldXRot;
        part.yRot = oldYRot;
        part.zRot = oldZRot;
    }
}
