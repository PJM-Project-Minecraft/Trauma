package ru.liko.trauma.bloodybits.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.liko.trauma.Trauma;
import ru.liko.trauma.bloodybits.entity.BloodSprayEntity;
import ru.liko.trauma.common.entity.MannequinEntity;

import java.util.function.Supplier;

public class ModEntityTypes {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE,
            Trauma.MODID);

    public static final Supplier<EntityType<BloodSprayEntity>> BLOOD_SPRAY = ENTITY_TYPES.register("blood_spray",
            () -> EntityType.Builder
                    .of((EntityType.EntityFactory<BloodSprayEntity>) BloodSprayEntity::new, MobCategory.MISC)
                    .fireImmune()
                    .immuneTo(Blocks.POWDER_SNOW)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(4)
                    .updateInterval(20)
                    .build("blood_spray"));

    public static final Supplier<EntityType<MannequinEntity>> MANNEQUIN = ENTITY_TYPES.register("mannequin",
            () -> EntityType.Builder
                    .of((EntityType.EntityFactory<MannequinEntity>) MannequinEntity::new, MobCategory.MISC)
                    .fireImmune()
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .build("mannequin"));
}
