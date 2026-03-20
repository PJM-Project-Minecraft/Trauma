package ru.liko.trauma.common.event;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.DamageTypeTags;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import ru.liko.trauma.Trauma;
import ru.liko.trauma.common.capability.ModAttachments;
import ru.liko.trauma.common.effect.ModEffects;
import ru.liko.trauma.common.system.HitZone;
import ru.liko.trauma.common.system.HitZoneCalculator;
import ru.liko.trauma.common.system.TraumaData;
import ru.liko.trauma.common.util.ToolHelper;

@EventBusSubscriber(modid = Trauma.MODID)
public class DamageEventHandler {

    @SubscribeEvent
    public static void onProjectileHit(ProjectileImpactEvent event) {
        if (event.getRayTraceResult().getType() == HitResult.Type.ENTITY) {
            EntityHitResult hitResult = (EntityHitResult) event.getRayTraceResult();

            if (hitResult.getEntity() instanceof net.minecraft.world.entity.LivingEntity victim
                    && !victim.level().isClientSide()) {
                if (victim instanceof Player p && (p.isCreative() || p.isSpectator()))
                    return;

                Vec3 hitPos = hitResult.getLocation();
                HitZone zone = HitZoneCalculator.determineHitZone(victim, hitPos);

                // Сброс фреймов неуязвимости чтобы стрела не отскакивала
                // (особенно актуально для попаданий в голову)
                if (victim.invulnerableTime > 0) {
                    victim.invulnerableTime = 0;
                }

                // Store recent zone hit in attachment
                TraumaData data = victim.getData(ModAttachments.TRAUMA_DATA);
                victim.setData(ModAttachments.TRAUMA_DATA, data.withRecentHitZone(zone));
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.LivingEntity victim
                && !victim.level().isClientSide()) {
            if (victim instanceof Player p && (p.isCreative() || p.isSpectator()))
                return;

            TraumaData data = victim.getData(ModAttachments.TRAUMA_DATA);
            HitZone zone = data.getRecentHitZone();

            float baseDamage = event.getNewDamage();
            float finalDamage = baseDamage;

            if (zone != null) {
                finalDamage = baseDamage * zone.getMultiplier();
                event.setNewDamage(finalDamage);
            }

            // --- BLUR ON HIT (any damage) ---
            // Base 0.2 ensures even the smallest hit is clearly visible;
            // +0.08 per damage point stacks up to the 1.0 cap
            float addedBlur = finalDamage * 0.08f + 0.2f;
            data = data.withBlur(Math.min(1.0f, data.blurIntensity() + addedBlur));
            // ------------------------------------

            // --- LEG BREAKING & DISLOCATION ---
            boolean brokeLeg = false;
            if (event.getSource().is(DamageTypeTags.IS_FALL)) {
                // Fall damage is roughly distance - 3. So finalDamage 1.0 is ~4 blocks fall.
                // Let's use the actual fall distance if possible, but we only have damage here.
                // We can estimate distance: distance = damage + 3 (roughly, without jump boost/feather falling).
                float estimatedDistance = finalDamage + 3.0f;
                double dislocThreshold = ru.liko.trauma.Config.FALL_DISTANCE_DISLOCATION.get();
                double fracThreshold = ru.liko.trauma.Config.FALL_DISTANCE_FRACTURE.get();

                if (estimatedDistance >= fracThreshold) {
                    data = data.withLegFracture(Math.max(data.legFracture(), 30 + (finalDamage / 10) * 70));
                    brokeLeg = true;
                } else if (estimatedDistance >= dislocThreshold) {
                    data = data.withLegDislocation(Math.max(data.legDislocation(), 30 + (finalDamage / 10) * 70));
                    brokeLeg = true;
                }
            } else if (zone == HitZone.LIMBS && finalDamage > 10.0f) {
                if (Math.random() < 0.5f) {
                    if (Math.random() < 0.5f) {
                        data = data.withLegFracture(Math.max(data.legFracture(), 30 + (finalDamage / 10) * 70));
                    } else {
                        data = data.withLegDislocation(Math.max(data.legDislocation(), 30 + (finalDamage / 10) * 70));
                    }
                    brokeLeg = true;
                }
            }

            if (brokeLeg) {
                victim.level().playSound(null, victim.blockPosition(), ru.liko.trauma.common.sound.ModSounds.BROKEN_BONE.get(), net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
            }
            // --------------------

            if (zone != HitZone.HEAD && finalDamage > 0) {

                // --- CHECK FOR CUTTING ITEMS ---
                boolean canCauseBleed = false;

                if (event.getSource().is(DamageTypeTags.IS_PROJECTILE)) {
                    canCauseBleed = true;
                } else if (event.getSource().getEntity() instanceof LivingEntity attacker) {
                    ItemStack heldItem = attacker.getMainHandItem();
                    if (ToolHelper.isCuttingTool(heldItem)) {
                        canCauseBleed = true;
                    }
                }

                if (canCauseBleed) {
                    // Check if already bleeding (effect active)
                    Holder<net.minecraft.world.effect.MobEffect> bleedingHolder = BuiltInRegistries.MOB_EFFECT
                            .wrapAsHolder(ModEffects.BLEEDING.get());
                    MobEffectInstance existing = victim.getEffect(bleedingHolder);
                    // Add bleeding (lasts 'infinite' roughly, until bandaged) -> 60 minutes
                    // Cap at 2 (Level 3)
                    int newAmp = (existing != null) ? Math.min(existing.getAmplifier() + 1, 2) : 0;

                    victim.addEffect(new MobEffectInstance(bleedingHolder, -1, newAmp, false, false, false));
                    victim.setData(ModAttachments.TRAUMA_DATA,
                            data.withRecentHitZone(null).withBleedStrength(newAmp + 1));
                } else {
                    // Just update data with pain/contusion
                    victim.setData(ModAttachments.TRAUMA_DATA, data.withRecentHitZone(null));
                }
            } else {
                // Reset recent hitting zone to avoid misinterpreting future non-projectile
                // damage, but keep pain/contusion
                victim.setData(ModAttachments.TRAUMA_DATA, data.withRecentHitZone(null));
            }
        }
    }
}
