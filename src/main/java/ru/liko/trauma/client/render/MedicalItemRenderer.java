package ru.liko.trauma.client.render;

import ru.liko.trauma.common.item.MedicalItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class MedicalItemRenderer extends GeoItemRenderer<MedicalItem> {
    public MedicalItemRenderer() {
        super(new MedicalItemModel());
    }
}
