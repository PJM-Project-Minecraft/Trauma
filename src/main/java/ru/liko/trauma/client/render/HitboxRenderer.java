package ru.liko.trauma.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import ru.liko.trauma.Trauma;
import ru.liko.trauma.common.command.TraumaCommands;

@EventBusSubscriber(modid = Trauma.MODID, value = Dist.CLIENT)
public class HitboxRenderer {

        @SubscribeEvent
        public static void onRenderLiving(RenderLivingEvent.Post<LivingEntity, ?> event) {
                if (!TraumaCommands.HITBOX_DEBUG_MODE)
                        return;

                LivingEntity entity = event.getEntity();
                PoseStack poseStack = event.getPoseStack();
                MultiBufferSource bufferSource = event.getMultiBufferSource();

                float width = entity.getBbWidth();
                float height = entity.getBbHeight();

                // Local bounding box for the entity (centered on X/Z, from 0 to height on Y)
                AABB localBox = new AABB(-width / 2, 0, -width / 2, width / 2, height, width / 2);

                VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());

                // Head (Red) - Top 20% (0.80 to 1.0)
                AABB headBox = new AABB(localBox.minX, localBox.minY + height * 0.80, localBox.minZ,
                                localBox.maxX, localBox.maxY, localBox.maxZ);
                LevelRenderer.renderLineBox(poseStack, consumer, headBox, 1.0F, 0.0F, 0.0F, 1.0F);

                // Chest/Torso (Blue) - 0.45 to 0.80
                AABB torsoBox = new AABB(localBox.minX, localBox.minY + height * 0.45, localBox.minZ,
                                localBox.maxX, localBox.minY + height * 0.80, localBox.maxZ);
                LevelRenderer.renderLineBox(poseStack, consumer, torsoBox, 0.0F, 0.0F, 1.0F, 1.0F);

                // Limbs/Legs (Green) - 0.0 to 0.45
                AABB limbsBox = new AABB(localBox.minX, localBox.minY, localBox.minZ,
                                localBox.maxX, localBox.minY + height * 0.45, localBox.maxZ);
                LevelRenderer.renderLineBox(poseStack, consumer, limbsBox, 0.0F, 1.0F, 0.0F, 1.0F);
        }
}
