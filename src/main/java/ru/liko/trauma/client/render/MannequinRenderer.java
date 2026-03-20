package ru.liko.trauma.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import ru.liko.trauma.Trauma;
import ru.liko.trauma.common.entity.MannequinEntity;

/**
 * Renderer for the MannequinEntity.
 * Uses the standard humanoid model. When ragdoll is active,
 * the entity is set invisible and RagdollPlayerRenderer handles drawing.
 */
public class MannequinRenderer extends MobRenderer<MannequinEntity, HumanoidModel<MannequinEntity>> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            Trauma.MODID, "textures/entity/mannequin.png");

    // Fallback to default Steve skin if custom texture doesn't exist
    private static final ResourceLocation FALLBACK_TEXTURE = ResourceLocation.withDefaultNamespace(
            "textures/entity/player/wide/steve.png");

    public MannequinRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(MannequinEntity entity) {
        // Use Steve skin as fallback until custom mannequin texture is created
        return FALLBACK_TEXTURE;
    }
}
