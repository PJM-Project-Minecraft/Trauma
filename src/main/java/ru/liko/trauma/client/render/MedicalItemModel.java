package ru.liko.trauma.client.render;

import net.minecraft.resources.ResourceLocation;
import ru.liko.trauma.Trauma;
import ru.liko.trauma.common.item.MedicalItem;
import software.bernie.geckolib.model.GeoModel;

public class MedicalItemModel extends GeoModel<MedicalItem> {

    @Override
    public ResourceLocation getModelResource(MedicalItem item) {
        return ResourceLocation.fromNamespaceAndPath(Trauma.MODID, "geo/" + item.getGeoId() + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(MedicalItem item) {
        return ResourceLocation.fromNamespaceAndPath(Trauma.MODID, "textures/item/" + item.getGeoId() + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(MedicalItem item) {
        return ResourceLocation.fromNamespaceAndPath(Trauma.MODID, "animation/in_hold_arm.animation.json");
    }
}
