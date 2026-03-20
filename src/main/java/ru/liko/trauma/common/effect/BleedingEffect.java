package ru.liko.trauma.common.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import ru.liko.trauma.common.capability.ModAttachments;
import ru.liko.trauma.common.system.TraumaData;

public class BleedingEffect extends MobEffect {

    public BleedingEffect() {
        super(MobEffectCategory.HARMFUL, 0x8A0303); // Темно-красный цвет
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        // Каждый тик = постоянно
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide() && entity instanceof Player player) {
            TraumaData data = player.getData(ModAttachments.TRAUMA_DATA);

            // Drain amount based on Config + amplifier
            double baseDrain = 1.0;
            switch (amplifier) {
                case 0:
                    baseDrain = ru.liko.trauma.Config.LIGHT_BLEED_DRAIN.get();
                    break;
                case 1:
                    baseDrain = ru.liko.trauma.Config.HEAVY_BLEED_DRAIN.get();
                    break;
                default:
                    baseDrain = ru.liko.trauma.Config.SEVERE_BLEED_DRAIN.get();
                    break;
            }
            // The config value is per second. Effect ticks every tick (20 times per second)
            float tickDrain = (float) (baseDrain / 20.0f);

            if (ru.liko.trauma.common.event.PlayerTickEventHandler.SUPPRESSION_MAP.getOrDefault(player.getUUID(),
                    false)) {
                tickDrain *= 0.5f; // Slow down bleeding by 50% if suppressed
            }
            float newBlood = data.bloodVolume() - tickDrain;
            if (newBlood < 0)
                newBlood = 0;

            player.setData(ModAttachments.TRAUMA_DATA, data.withBlood(newBlood).withBleedStrength(amplifier + 1));
        }
        return true;
    }
}
