package ru.liko.trauma.compat.tacz;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import ru.liko.trauma.common.capability.ModAttachments;
import ru.liko.trauma.common.effect.ModEffects;
import ru.liko.trauma.common.system.HitZone;
import ru.liko.trauma.common.system.HitZoneCalculator;
import ru.liko.trauma.common.system.TraumaData;

public class TaczCompat {

    public static void register() {
        NeoForge.EVENT_BUS.register(TaczCompat.class);
    }

    @SubscribeEvent
    public static void onGunHurtPre(EntityHurtByGunEvent.Pre event) {
        if (event.getHurtEntity() instanceof LivingEntity victim && !victim.level().isClientSide()) {
            // TaCZ bullets don't always trigger ProjectileImpactEvent, so we calculate the hit zone here.
            HitZone zone = HitZone.TORSO; // Default fallback
            
            if (event.isHeadShot()) {
                zone = HitZone.HEAD;
            } else if (event.getBullet() != null) {
                Vec3 hitPos = event.getBullet().position();
                zone = HitZoneCalculator.determineHitZone(victim, hitPos);
                // If TaCZ didn't consider it a headshot but our calculator does, trust TaCZ
                if (zone == HitZone.HEAD) {
                    zone = HitZone.TORSO;
                }
            }

            // Store recent zone hit in attachment so DamageEventHandler can use it for multipliers
            TraumaData data = victim.getData(ModAttachments.TRAUMA_DATA);
            victim.setData(ModAttachments.TRAUMA_DATA, data.withRecentHitZone(zone));
        }
    }

    @SubscribeEvent
    public static void onGunHurtPost(EntityHurtByGunEvent.Post event) {
        if (event.getHurtEntity() instanceof LivingEntity victim && !victim.level().isClientSide()) {
            if (victim instanceof Player p && (p.isCreative() || p.isSpectator())) {
                return;
            }

            float damage = event.getBaseAmount();
            boolean isHeadshot = event.isHeadShot();

            // Apply bleeding if it's not a headshot and damage > 0
            if (damage > 0 && !isHeadshot) {
                TraumaData data = victim.getData(ModAttachments.TRAUMA_DATA);
                
                Holder<net.minecraft.world.effect.MobEffect> bleedingHolder = BuiltInRegistries.MOB_EFFECT
                        .wrapAsHolder(ModEffects.BLEEDING.get());
                MobEffectInstance existing = victim.getEffect(bleedingHolder);
                
                int newAmp = (existing != null) ? Math.min(existing.getAmplifier() + 1, 2) : 0;

                victim.addEffect(new MobEffectInstance(bleedingHolder, -1, newAmp, false, false, false));
                victim.setData(ModAttachments.TRAUMA_DATA,
                        data.withBleedStrength(newAmp + 1));
            }
        }
    }
}
