package ru.liko.trauma.client.ragdoll;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.liko.trauma.Trauma;
import ru.liko.trauma.ragdoll.RagdollPart;
import ru.liko.trauma.ragdoll.RagdollTransform;

import java.util.Map;

/**
 * Renders debug wireframe boxes for each ragdoll part when hitbox rendering is enabled (F3+B).
 */
@EventBusSubscriber(modid = Trauma.MODID, value = Dist.CLIENT)
public class RagdollDebugRenderer {

    private static final Minecraft mc = Minecraft.getInstance();

    @SubscribeEvent
    public static void onRenderWorld(RenderLevelStageEvent event) {
        if (!mc.getEntityRenderDispatcher().shouldRenderHitBoxes()) return;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        PoseStack poseStack = event.getPoseStack();
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lines());

        RenderSystem.disableCull();

        for (ClientRagdollManager.ClientRagdoll rag : ClientRagdollManager.getAll()) {
            if (rag == null || !rag.isActive()) continue;

            for (Map.Entry<RagdollPart, RagdollTransform> entry : rag.getAllPartsInterpolated(partial).entrySet()) {
                RagdollPart part = entry.getKey();
                RagdollTransform t = entry.getValue();
                if (t == null) continue;

                Vector3f pos = new Vector3f(t.position.x, t.position.y, t.position.z);
                Quaternionf rot = new Quaternionf(t.rotation.x, t.rotation.y, t.rotation.z, t.rotation.w);

                drawDebugBox(poseStack, buffer, camPos, pos, rot, part.getHalfExtents(), part.ordinal());
            }
        }

        bufferSource.endBatch(RenderType.lines());
        RenderSystem.enableCull();
    }

    private static void drawDebugBox(PoseStack poseStack, VertexConsumer buffer, Vec3 camPos,
                                     Vector3f pos, Quaternionf rotation, Vector3f halfExtents, int colorIndex) {
        poseStack.pushPose();
        poseStack.translate(pos.x - camPos.x, pos.y - camPos.y, pos.z - camPos.z);
        poseStack.mulPose(rotation);

        float[][] colors = {
                {1, 0, 0},    // red - torso
                {0, 1, 0},    // green - head
                {0, 0, 1},    // blue - left leg
                {1, 1, 0},    // yellow - right leg
                {1, 0, 1},    // magenta - left arm
                {0, 1, 1}     // cyan - right arm
        };
        float[] c = colors[colorIndex % colors.length];

        Vector3f[] corners = {
                new Vector3f(-halfExtents.x, -halfExtents.y, -halfExtents.z),
                new Vector3f(-halfExtents.x, -halfExtents.y, halfExtents.z),
                new Vector3f(-halfExtents.x, halfExtents.y, -halfExtents.z),
                new Vector3f(-halfExtents.x, halfExtents.y, halfExtents.z),
                new Vector3f(halfExtents.x, -halfExtents.y, -halfExtents.z),
                new Vector3f(halfExtents.x, -halfExtents.y, halfExtents.z),
                new Vector3f(halfExtents.x, halfExtents.y, -halfExtents.z),
                new Vector3f(halfExtents.x, halfExtents.y, halfExtents.z)
        };

        int[][] edges = {
                {0, 1}, {0, 2}, {1, 3}, {2, 3},
                {4, 5}, {4, 6}, {5, 7}, {6, 7},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };

        var matrix = poseStack.last().pose();
        for (int[] e : edges) {
            Vector3f a = corners[e[0]];
            Vector3f b = corners[e[1]];
            buffer.addVertex(matrix, a.x, a.y, a.z).setColor(c[0], c[1], c[2], 1f).setNormal(0, 1, 0);
            buffer.addVertex(matrix, b.x, b.y, b.z).setColor(c[0], c[1], c[2], 1f).setNormal(0, 1, 0);
        }

        poseStack.popPose();
    }
}
