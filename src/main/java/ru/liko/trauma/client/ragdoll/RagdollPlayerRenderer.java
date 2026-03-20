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
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
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
import org.joml.Vector3f;
import ru.liko.trauma.Trauma;
import ru.liko.trauma.common.entity.MannequinEntity;
import ru.liko.trauma.ragdoll.RagdollPart;
import ru.liko.trauma.ragdoll.RagdollTransform;

import java.util.HashSet;
import java.util.Set;

/**
 * Client-side renderer that replaces the normal player model with ragdoll
 * parts.
 * Offset arrays and renderRagdollPart logic copied directly from
 * prototype-physics.
 */
@EventBusSubscriber(modid = Trauma.MODID, value = Dist.CLIENT)
public class RagdollPlayerRenderer {

        // Tracks which ragdolls were already rendered by onRenderPlayer/onRenderLiving
        // this frame to avoid double rendering in onRenderLevelStage.
        private static final Set<Integer> renderedThisFrame = new HashSet<>();

        // Lazily-created model for orphaned ragdolls (entity no longer exists after respawn)
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

                // Cache skin texture while entity is alive (for orphaned rendering after respawn)
                if (rag != null) {
                        rag.cacheSkinTexture(player.getSkin().texture());
                }

                if (rag == null || !rag.isActive())
                        return;
                event.setCanceled(true);
                renderedThisFrame.add(rag.playerEntityId);

                PlayerRenderer renderer = event.getRenderer();
                PlayerModel<AbstractClientPlayer> model = renderer.getModel();
                PoseStack poseStack = event.getPoseStack();
                MultiBufferSource buffer = event.getMultiBufferSource();
                float partial = event.getPartialTick();

                RagdollTransform torso = rag.getPartInterpolated(RagdollPart.TORSO, partial);
                if (torso == null)
                        return;
                RagdollTransform head = rag.getPartInterpolated(RagdollPart.HEAD, partial);
                RagdollTransform larm = rag.getPartInterpolated(RagdollPart.LEFT_ARM, partial);
                RagdollTransform rarm = rag.getPartInterpolated(RagdollPart.RIGHT_ARM, partial);
                RagdollTransform lleg = rag.getPartInterpolated(RagdollPart.LEFT_LEG, partial);
                RagdollTransform rleg = rag.getPartInterpolated(RagdollPart.RIGHT_LEG, partial);

                ResourceLocation skin = player.getSkin().texture();
                VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(skin));

                double lerpX = Mth.lerp(partial, player.xo, player.getX());
                double lerpY = Mth.lerp(partial, player.yo, player.getY());
                double lerpZ = Mth.lerp(partial, player.zo, player.getZ());
                Vec3 camPos = new Vec3(lerpX, lerpY, lerpZ);

                renderRagdollPart(poseStack, vc, model.body, torso, camPos, RagdollPart.TORSO, event.getPackedLight());
                renderRagdollPart(poseStack, vc, model.head, head, camPos, RagdollPart.HEAD, event.getPackedLight());
                renderRagdollPart(poseStack, vc, model.leftLeg, lleg, camPos, RagdollPart.LEFT_LEG,
                                event.getPackedLight());
                renderRagdollPart(poseStack, vc, model.rightLeg, rleg, camPos, RagdollPart.RIGHT_LEG,
                                event.getPackedLight());
                renderRagdollPart(poseStack, vc, model.leftArm, larm, camPos, RagdollPart.LEFT_ARM,
                                event.getPackedLight());
                renderRagdollPart(poseStack, vc, model.rightArm, rarm, camPos, RagdollPart.RIGHT_ARM,
                                event.getPackedLight());
        }

        @SuppressWarnings("unchecked")
        @SubscribeEvent
        public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
                LivingEntity entity = event.getEntity();
                if (!(entity instanceof MannequinEntity))
                        return;

                ClientRagdollManager.ClientRagdoll rag = ClientRagdollManager.get(entity.getId());
                if (rag == null || !rag.isActive())
                        return;
                event.setCanceled(true);
                renderedThisFrame.add(rag.playerEntityId);

                PoseStack poseStack = event.getPoseStack();
                MultiBufferSource buffer = event.getMultiBufferSource();
                float partial = event.getPartialTick();

                RagdollTransform torso = rag.getPartInterpolated(RagdollPart.TORSO, partial);
                if (torso == null)
                        return;
                RagdollTransform head = rag.getPartInterpolated(RagdollPart.HEAD, partial);
                RagdollTransform larm = rag.getPartInterpolated(RagdollPart.LEFT_ARM, partial);
                RagdollTransform rarm = rag.getPartInterpolated(RagdollPart.RIGHT_ARM, partial);
                RagdollTransform lleg = rag.getPartInterpolated(RagdollPart.LEFT_LEG, partial);
                RagdollTransform rleg = rag.getPartInterpolated(RagdollPart.RIGHT_LEG, partial);

                ResourceLocation skin = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");
                VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(skin));

                double lerpX = Mth.lerp(partial, entity.xo, entity.getX());
                double lerpY = Mth.lerp(partial, entity.yo, entity.getY());
                double lerpZ = Mth.lerp(partial, entity.zo, entity.getZ());
                Vec3 camPos = new Vec3(lerpX, lerpY, lerpZ);

                if (event.getRenderer().getModel() instanceof HumanoidModel<?> model) {
                        renderRagdollPart(poseStack, vc, model.body, torso, camPos, RagdollPart.TORSO,
                                        event.getPackedLight());
                        renderRagdollPart(poseStack, vc, model.head, head, camPos, RagdollPart.HEAD,
                                        event.getPackedLight());
                        renderRagdollPart(poseStack, vc, model.leftLeg, lleg, camPos, RagdollPart.LEFT_LEG,
                                        event.getPackedLight());
                        renderRagdollPart(poseStack, vc, model.rightLeg, rleg, camPos, RagdollPart.RIGHT_LEG,
                                        event.getPackedLight());
                        renderRagdollPart(poseStack, vc, model.leftArm, larm, camPos, RagdollPart.LEFT_ARM,
                                        event.getPackedLight());
                        renderRagdollPart(poseStack, vc, model.rightArm, rarm, camPos, RagdollPart.RIGHT_ARM,
                                        event.getPackedLight());
                }
        }

        /**
         * Render orphaned ragdolls (death ragdolls whose entity no longer exists after respawn).
         * These still receive updates from the server on the old entity ID.
         */
        @SubscribeEvent
        public static void onRenderLevelStage(RenderLevelStageEvent event) {
                if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

                Minecraft mc = Minecraft.getInstance();
                if (mc.level == null) return;

                Vec3 cameraPos = event.getCamera().getPosition();
                float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);

                for (ClientRagdollManager.ClientRagdoll rag : ClientRagdollManager.getAll()) {
                        if (!rag.isActive()) continue;

                        // Already rendered by onRenderPlayer or onRenderLiving this frame
                        if (renderedThisFrame.contains(rag.playerEntityId)) continue;

                        // Entity exists and is alive — will be rendered by onRenderPlayer/onRenderLiving
                        Entity existingEntity = mc.level.getEntity(rag.playerEntityId);
                        if (existingEntity instanceof LivingEntity living && living.isAlive())
                                continue;

                        // Render: entity is missing (orphaned) OR entity is dead and its render event didn't fire
                        renderOrphanedRagdoll(rag, event.getPoseStack(), cameraPos, partial);
                }
                renderedThisFrame.clear();
        }

        /**
         * Render a death ragdoll that has no associated entity (after player respawn).
         */
        private static void renderOrphanedRagdoll(
                        ClientRagdollManager.ClientRagdoll rag,
                        PoseStack poseStack,
                        Vec3 cameraPos,
                        float partial) {
                HumanoidModel<?> model = getOrphanModel();

                ResourceLocation skin = rag.getCachedSkinTexture();
                if (skin == null) {
                        // Try to resolve skin from UUID if we know who this player was
                        java.util.UUID uuid = rag.getCachedPlayerUUID();
                        if (uuid != null && Minecraft.getInstance().level != null) {
                                for (net.minecraft.world.entity.player.Player p : Minecraft.getInstance().level.players()) {
                                        if (p.getUUID().equals(uuid) && p instanceof net.minecraft.client.player.AbstractClientPlayer acp) {
                                                skin = acp.getSkin().texture();
                                                rag.cacheSkinTexture(skin);
                                                break;
                                        }
                                }
                        }
                }
                if (skin == null) {
                        skin = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");
                }

                RagdollTransform torso = rag.getPartInterpolated(RagdollPart.TORSO, partial);
                if (torso == null) return;

                // Compute light at ragdoll torso position
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

        /**
         * Render a single ragdoll body part based on its physical transform position
         * and rotation.
         */
        public static void renderRagdollPart(
                        PoseStack poseStack,
                        VertexConsumer vertexConsumer,
                        ModelPart part,
                        RagdollTransform transform,
                        Vec3 camPos,
                        RagdollPart ragdollPart,
                        int light) {
                if (transform == null)
                        return;

                poseStack.pushPose();

                // Translate to exact world position of the body part
                poseStack.translate(transform.position.x - camPos.x, transform.position.y - camPos.y,
                                transform.position.z - camPos.z);

                // Save original part transform state
                float oldX = part.x;
                float oldY = part.y;
                float oldZ = part.z;
                float oldXRot = part.xRot;
                float oldYRot = part.yRot;
                float oldZRot = part.zRot;

                // Apply physics rotation
                Quaternionf q = new Quaternionf(
                                transform.rotation.x, transform.rotation.y, transform.rotation.z, transform.rotation.w);
                poseStack.mulPose(q);

                // Physics bodies are centered. We need to reset the model part's pivot
                // and adjust its position so that its geometric center matches (0,0,0).
                part.setPos(0, 0, 0);
                part.xRot = 0;
                part.yRot = 0;
                part.zRot = 0;

                // The physics model is rotated 180 degrees Z because Minecraft characters are
                // rendered upside down internally initially
                q.identity();
                q.rotateZ((float) Math.PI);
                poseStack.mulPose(q);

                // Shift model part based on its bounding box center relative to its pivot
                // HumanoidModel parts typically pivot from the top (neck/shoulders/waist), but
                // physics meshes are centered.
                // We revert the part geometric offset before rendering so it lines up with the
                // physics center.
                // Note: in this flipped space (after PI Z rotation), +Y is DOWN, +X is LEFT.
                switch (ragdollPart) {
                        case HEAD:
                                poseStack.translate(0, 4.0f / 16.0f, 0);
                                break;
                        case TORSO:
                                poseStack.translate(0, -6.0f / 16.0f, 0);
                                break;
                        case LEFT_ARM:
                                poseStack.translate(-1.0f / 16.0f, -4.0f / 16.0f, 0);
                                break;
                        case RIGHT_ARM:
                                poseStack.translate(1.0f / 16.0f, -4.0f / 16.0f, 0);
                                break;
                        case LEFT_LEG:
                        case RIGHT_LEG:
                                poseStack.translate(0, -6.0f / 16.0f, 0);
                                break;
                }

                part.render(poseStack, vertexConsumer, light, OverlayTexture.NO_OVERLAY);
                poseStack.popPose();

                // Restore original part transform state
                part.x = oldX;
                part.y = oldY;
                part.z = oldZ;
                part.xRot = oldXRot;
                part.yRot = oldYRot;
                part.zRot = oldZRot;
        }
}
