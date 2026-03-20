package ru.liko.trauma.common.event;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import ru.liko.trauma.Config;
import ru.liko.trauma.Trauma;
import ru.liko.trauma.common.capability.ModAttachments;
import ru.liko.trauma.common.effect.ModEffects;
import ru.liko.trauma.common.system.TraumaData;

@EventBusSubscriber(modid = Trauma.MODID)
public class PlayerTickEventHandler {

    public static final java.util.Map<java.util.UUID, Boolean> SUPPRESSION_MAP = new java.util.concurrent.ConcurrentHashMap<>();

    // We use a ResourceLocation ID for NeoForge 1.21 attribute modifiers instead of
    // UUID
    private static final ResourceLocation HEALTH_PENALTY_ID = ResourceLocation.fromNamespaceAndPath(Trauma.MODID,
            "blood_loss_health_penalty");

    // Short duration so effect expires in ~3 ticks after condition gone = instant
    // feel
    private static final int EFFECT_REFRESH_DURATION = 3;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !player.isAlive())
            return;

        // Skip logic for creative and spectator players:
        // Blood volume and bleed strength are PRESERVED, but all active effects
        // and modifiers are stripped so creative mode is unaffected.
        // When switching back to survival, everything resumes automatically.
        if (player.isCreative() || player.isSpectator()) {
            Holder<MobEffect> bleedingHolder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(ModEffects.BLEEDING.get());
            if (player.hasEffect(bleedingHolder)) {
                player.removeEffect(bleedingHolder);
            }
            // Remove health penalty modifier
            AttributeInstance maxHealthAttr = player.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealthAttr != null) {
                maxHealthAttr.removeModifier(HEALTH_PENALTY_ID);
            }
            SUPPRESSION_MAP.remove(player.getUUID());
            return;
        }

        TraumaData data = player.getData(ModAttachments.TRAUMA_DATA);
        boolean isSuppressing = SUPPRESSION_MAP.getOrDefault(player.getUUID(), false);

        // --- TICK TIMER (BLUR FADE) ---
        // ~0.012/tick → max blur (1.0) fades in ~7 seconds
        if (data.blurIntensity() > 0) {
            data = data.withBlur(Math.max(0f, data.blurIntensity() - 0.012f));
            player.setData(ModAttachments.TRAUMA_DATA, data);
        }

        // =============================================
        // FAST PATH: runs every tick for instant response
        // Applies/refreshes effects with short duration so they expire ~instantly when
        // the condition is no longer true
        // =============================================

        // --- SUPPRESSION SLOWNESS (every tick) ---
        if (isSuppressing) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EFFECT_REFRESH_DURATION, 1, false,
                    false, false));
        }

        // --- DEBUFFS (every tick, short duration) ---
        if (Config.DEBUFFS_ENABLED.get() && data.bloodVolume() < 2500f) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EFFECT_REFRESH_DURATION,
                    (data.bloodVolume() < 1000f) ? 1 : 0, false, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EFFECT_REFRESH_DURATION,
                    (data.bloodVolume() < 1000f) ? 1 : 0, false, false, false));
            if (data.bloodVolume() < 1500f) {
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, EFFECT_REFRESH_DURATION, 0, false,
                        false, false));
            }
        }

        // --- LEG FRACTURE & DISLOCATION SLOWNESS (every tick) ---
        if (data.legFracture() > 0) {
            int slownessLevel = data.hasSplint() ? 0 : (data.legFracture() > 50 ? 2 : 1);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EFFECT_REFRESH_DURATION, slownessLevel, false, false, false));
        } else if (data.legDislocation() > 0) {
            int slownessLevel = data.legDislocation() > 50 ? 2 : 1;
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EFFECT_REFRESH_DURATION, slownessLevel, false, false, false));
        }

        // =============================================
        // HEAVY PATH: runs once per second (every 20 ticks)
        // Calculations: blood regen, health penalty, sync
        // =============================================
        if (player.tickCount % 20 == 0) {
            Holder<MobEffect> bleedingHolder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(ModEffects.BLEEDING.get());
            boolean isActuallyBleeding = player.hasEffect(bleedingHolder);
            int actualBleedStrength = isActuallyBleeding ? player.getEffect(bleedingHolder).getAmplifier() + 1 : 0;

            // If bleedStrength is saved but no effect is active (e.g. returning from
            // creative),
            // re-apply the bleeding effect
            if (data.bleedStrength() > 0 && !isActuallyBleeding) {
                int amp = data.bleedStrength() - 1; // amplifier is 0-based
                player.addEffect(new MobEffectInstance(bleedingHolder, -1, amp, false, false, false));
            } else if (data.bleedStrength() != actualBleedStrength) {
                data = data.withBleedStrength(actualBleedStrength);
                player.setData(ModAttachments.TRAUMA_DATA, data);
            }

            // Passive Regen (Только если нет кровотечения и полное здоровье)
            if (actualBleedStrength == 0 && player.getHealth() >= player.getMaxHealth()
                    && data.bloodVolume() < TraumaData.MAX_BLOOD) {
                float regenBlood = Math.min(data.bloodVolume() + 10f, TraumaData.MAX_BLOOD);
                data = data.withBlood(regenBlood);
                player.setData(ModAttachments.TRAUMA_DATA, data);
            }

            // --- SYSTEM MODES ---
            Config.BloodSystemMode mode = Config.BLOOD_SYSTEM_MODE.get();
            AttributeInstance maxHealthAttr = player.getAttribute(Attributes.MAX_HEALTH);

            if (mode == Config.BloodSystemMode.BLOOD_AND_HEALTH) {
                // The less blood, the less health (Up to -80% max health)
                if (maxHealthAttr != null) {
                    float bloodPercent = Math.clamp(data.bloodVolume() / TraumaData.MAX_BLOOD, 0f, 1f);
                    double penalty = -16.0 * (1.0 - bloodPercent); // max 16 health penalty (-8 hearts)

                    net.minecraft.world.entity.ai.attributes.AttributeModifier existingModifier = maxHealthAttr
                            .getModifier(HEALTH_PENALTY_ID);

                    // If penalty is minimal, remove completely
                    if (penalty > -0.5) {
                        if (existingModifier != null) {
                            maxHealthAttr.removeModifier(HEALTH_PENALTY_ID);
                        }
                    } else {
                        // If penalty is significantly different from existing, update it
                        if (existingModifier == null || Math.abs(existingModifier.amount() - penalty) > 0.5) {
                            maxHealthAttr.removeModifier(HEALTH_PENALTY_ID);
                            maxHealthAttr.addTransientModifier(new AttributeModifier(HEALTH_PENALTY_ID, penalty,
                                    AttributeModifier.Operation.ADD_VALUE));
                        }
                    }

                    // We don't forcibly set player health here. Minecraft native attribute system
                    // will automatically clamp the current health if it exceeds max health.
                    // Forcing setHealth(player.getMaxHealth()) here was causing the client to think
                    // it
                    // took ghost damage every second.
                }

                if (data.bloodVolume() <= 0) {
                    player.hurt(player.damageSources().magic(), 1.0f); // Fast damage if zero blood
                }

            } else if (mode == Config.BloodSystemMode.BLOOD_ONLY) {
                // Death at 0 blood, no health penalty
                if (maxHealthAttr != null)
                    maxHealthAttr.removeModifier(HEALTH_PENALTY_ID);

                if (data.bloodVolume() <= 0) {
                    player.hurt(player.damageSources().magic(), 1000f); // Instant death
                }
            } else {
                // DISABLED
                if (maxHealthAttr != null)
                    maxHealthAttr.removeModifier(HEALTH_PENALTY_ID);
            }

            // --- LEG FRACTURE HEALING (every 20 ticks) ---
            if (data.legFracture() > 0) {
                float healRate = data.hasSplint() ? 0.5f : 0.1f;
                float newFracture = Math.max(0, data.legFracture() - healRate);
                boolean newSplint = newFracture > 0 && data.hasSplint();
                data = data.withLegFracture(newFracture).withSplint(newSplint);
                player.setData(ModAttachments.TRAUMA_DATA, data);
            }

            // --- LEG DISLOCATION HEALING (every 20 ticks) ---
            // Dislocation doesn't heal passively as fast, but maybe a tiny bit or not at all until fixed.
            // Let's make it heal very slowly or randomly fix itself.
            if (data.legDislocation() > 0) {
                if (Math.random() < 0.01f) { // 1% chance every second to slightly reduce
                    float newDislocation = Math.max(0, data.legDislocation() - 5f);
                    data = data.withLegDislocation(newDislocation);
                    player.setData(ModAttachments.TRAUMA_DATA, data);
                }
            }

            SyncEventHandler.syncData(player);
        }
    }
}
