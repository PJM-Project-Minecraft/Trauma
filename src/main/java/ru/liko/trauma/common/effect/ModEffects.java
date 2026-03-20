package ru.liko.trauma.common.effect;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.liko.trauma.Trauma;

import java.util.function.Supplier;

public class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(BuiltInRegistries.MOB_EFFECT,
            Trauma.MODID);

    public static final Supplier<MobEffect> BLEEDING = EFFECTS.register("bleeding", BleedingEffect::new);
}
