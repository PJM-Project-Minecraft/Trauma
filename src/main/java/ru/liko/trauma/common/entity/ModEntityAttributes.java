package ru.liko.trauma.common.entity;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import ru.liko.trauma.Trauma;
import ru.liko.trauma.bloodybits.registry.ModEntityTypes;

/**
 * Registers entity attributes for custom entities on the MOD event bus.
 */
@EventBusSubscriber(modid = Trauma.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ModEntityAttributes {

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntityTypes.MANNEQUIN.get(), MannequinEntity.createAttributes().build());
    }
}
