package ru.liko.trauma.common.event;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import ru.liko.trauma.Trauma;
import ru.liko.trauma.common.effect.ModEffects;

/**
 * Блокирует регенерацию HP пока у игрока активен эффект кровотечения.
 * Пока идёт кровотечение — здоровье не восстанавливается.
 */
@EventBusSubscriber(modid = Trauma.MODID)
public class HealBlockHandler {

    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();

        if (entity instanceof Player player && !player.isCreative() && !player.isSpectator()) {
            Holder<MobEffect> bleedingHolder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(ModEffects.BLEEDING.get());

            if (player.hasEffect(bleedingHolder)) {
                event.setCanceled(true);
            }
        }
    }
}
