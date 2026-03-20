package ru.liko.trauma.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import ru.liko.trauma.Trauma;

@EventBusSubscriber(modid = Trauma.MODID, value = Dist.CLIENT)
public class DebugHitboxRenderer {

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<LivingEntity, ?> event) {
        Minecraft mc = Minecraft.getInstance();

        // Only render if F3+B hitboxes are enabled
        if (!mc.getEntityRenderDispatcher().shouldRenderHitBoxes()) {
            return;
        }

        LivingEntity entity = event.getEntity();
        if (entity.isInvisible()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        VertexConsumer vertexConsumer = event.getMultiBufferSource().getBuffer(RenderType.lines());

        // In RenderLivingEvent.Post, the PoseStack is centered at the entity's feet
        // (interpolated)
        // So local 0,0,0 is the center of the bottom face of its bounding box.
        float width = entity.getBbWidth();
        float height = entity.getBbHeight();

        // Local box relative to poseStack 0,0,0
        double minX = -width / 2.0;
        double minY = 0.0;
        double minZ = -width / 2.0;

        double maxX = width / 2.0;
        double maxY = height;
        double maxZ = width / 2.0;

        double totalHeight = height;

        // --- MATH REPLICATING HitZoneCalculator ---
        boolean isCrouching = entity.isCrouching();
        double headThreshold = isCrouching ? 0.75 : 0.80;
        double torsoThreshold = isCrouching ? 0.40 : 0.45;

        double headY = minY + totalHeight * headThreshold;
        double legsY = minY + totalHeight * torsoThreshold;

        // Render Head (Red)
        AABB headBox = new AABB(minX, headY, minZ, maxX, maxY, maxZ);
        LevelRenderer.renderLineBox(poseStack, vertexConsumer, headBox, 1.0f, 0.0f, 0.0f, 1.0f);

        // Render Torso (Green)
        AABB torsoBox = new AABB(minX, legsY, minZ, maxX, headY, maxZ);
        LevelRenderer.renderLineBox(poseStack, vertexConsumer, torsoBox, 0.0f, 1.0f, 0.0f, 1.0f);

        // Render Legs (Blue)
        AABB legsBox = new AABB(minX, minY, minZ, maxX, legsY, maxZ);
        LevelRenderer.renderLineBox(poseStack, vertexConsumer, legsBox, 0.0f, 0.0f, 1.0f, 1.0f);
    }
}
