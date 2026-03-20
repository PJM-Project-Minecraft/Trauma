package ru.liko.trauma.bloodybits.events;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import ru.liko.trauma.Trauma;
import ru.liko.trauma.bloodybits.config.CommonConfig;
import ru.liko.trauma.bloodybits.entity.BloodSprayEntity;
import ru.liko.trauma.bloodybits.registry.ModEntityTypes;
import ru.liko.trauma.bloodybits.utils.BloodyBitsUtils;

@EventBusSubscriber(modid = Trauma.MODID)
public class BloodyBitsEvents {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void bloodOnEntityDamage(LivingDamageEvent.Post event) {
        LivingEntity entity = event.getEntity();
        if (entity != null) {
            String entityName = (entity instanceof Player) ? "player" : entity.getEncodeId();
            entityName = (entityName == null) ? "" : entityName;

            if (!entity.level().isClientSide() && !CommonConfig.blackListEntities().contains(entityName)
                    && !CommonConfig.blackListDamageSources().contains(event.getSource().type().msgId())) {
                int maxDamage = (int) Math.min(20, event.getNewDamage());
                createBloodSpray(entity, event.getSource(), maxDamage, false);
            }
        }
    }

    @SubscribeEvent
    public static void entityBleedWhenDamaged(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity entity) {
            boolean hasTraumaBleeding = false;
            int bleedAmplifier = 0;
            try {
                var bleedingHolder = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                        .wrapAsHolder(ru.liko.trauma.common.effect.ModEffects.BLEEDING.get());
                hasTraumaBleeding = entity.hasEffect(bleedingHolder);
                if (hasTraumaBleeding) {
                    bleedAmplifier = entity.getEffect(bleedingHolder).getAmplifier() + 1;
                }
            } catch (Exception e) {
            }

            boolean lowHealth = CommonConfig.bleedWhenDamaged() && (entity.getHealth() / entity.getMaxHealth()) <= 0.5;

            if ((hasTraumaBleeding || lowHealth) && !entity.level().isClientSide() && !entity.isDeadOrDying()) {
                String entityName = (entity instanceof Player) ? "player" : entity.getEncodeId();
                entityName = (entityName == null) ? "" : entityName;

                if (!CommonConfig.blackListEntities().contains(entityName)) {
                    // Optimization & "Не часто": reduce bleed drop frequency.
                    // If they have the bleeding effect, scale it with the amplifier.
                    // e.g. level 1 = 80 ticks (4s), level 3 = 40 ticks (2s), level 5 = 20 ticks
                    // (1s, max).
                    int bleedMod = Math.max(20, 100 - (bleedAmplifier * 20));
                    int mod = hasTraumaBleeding ? bleedMod
                            : Math.max(100, (int) ((entity.getHealth() / entity.getMaxHealth()) * 1000));

                    if (entity.tickCount % mod == 0) {
                        createBloodSpray(entity, entity.damageSources().genericKill(), 1, true);
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void creeperExplosionEvent(ExplosionEvent.Detonate event) {
        Entity entity = event.getExplosion().getDirectSourceEntity();

        if (entity instanceof LivingEntity livEnt) {
            DamageSource source = entity.damageSources().explosion(event.getExplosion().getDirectSourceEntity(),
                    event.getExplosion().getIndirectSourceEntity());
            createBloodSpray(livEnt, source, 15, false);
        }
    }

    private static void createBloodSpray(LivingEntity entity, DamageSource damageSource, int damageAmount,
            boolean isBleedingDamage) {
        if (entity != null && damageSource != null) {
            String entityName = (entity instanceof Player) ? "player" : entity.getEncodeId();
            entityName = (entityName == null) ? "" : entityName;

            if (!entity.level().isClientSide() && !CommonConfig.blackListEntities().contains(entityName)
                    && !CommonConfig.blackListDamageSources().contains(damageSource.type().msgId())) {

                // Hardcap blood spray to max 10 particles per hit to prevent lag spikes on huge
                // damage
                int maxParticles = Math.min(damageAmount, 10);
                for (int i = 0; i < maxParticles; i++) {
                    if (BloodyBitsUtils.BLOOD_SPRAY_ENTITIES.size() >= CommonConfig.maxSpatters()) {
                        BloodSprayEntity oldest = BloodyBitsUtils.BLOOD_SPRAY_ENTITIES.pollFirst();
                        if (oldest != null) {
                            oldest.discard();
                        }
                    }

                    BloodSprayEntity bloodSprayEntity = new BloodSprayEntity(ModEntityTypes.BLOOD_SPRAY.get(), entity,
                            entity.level());
                    BloodyBitsUtils.BLOOD_SPRAY_ENTITIES.add(bloodSprayEntity);
                    Vec3 sourceDirection;
                    if (isBleedingDamage) {
                        // Bleeding drops under the character with a small spread
                        sourceDirection = new Vec3(0, -1.0, 0).normalize();
                    } else if (damageSource.getDirectEntity() != null
                            && damageSource.getDirectEntity() != damageSource.getEntity()) {
                        // Projectile: splatter follows the projectile's trajectory
                        Vec3 movement = damageSource.getDirectEntity().getDeltaMovement();
                        if (movement.lengthSqr() > 0.01) {
                            sourceDirection = movement.normalize();
                        } else {
                            sourceDirection = damageSource.getDirectEntity().getLookAngle();
                        }
                    } else if (damageSource.getEntity() != null) {
                        // Melee: splatter follows the attacker's hit direction
                        sourceDirection = damageSource.getEntity().getLookAngle();
                    } else {
                        // Generic damage: random outward
                        sourceDirection = new Vec3((Math.random() - 0.5), Math.random(), (Math.random() - 0.5))
                                .normalize();
                    }

                    // Apply a realistic cone-shaped spread
                    double spread = isBleedingDamage ? 0.3 : 0.6; // The higher the value, the wider the cone
                    double xAngle = sourceDirection.x + (Math.random() - 0.5) * spread;
                    double yAngle = sourceDirection.y + (Math.random() - 0.5) * spread;
                    double zAngle = sourceDirection.z + (Math.random() - 0.5) * spread;

                    Vec3 finalDir = new Vec3(xAngle, yAngle, zAngle).normalize();

                    // Adjust initial speed. Cap the damage factor so high damage doesn't cause
                    // glitchy light-speed blood
                    double speedMultiplier = isBleedingDamage ? (0.1 + Math.random() * 0.1)
                            : (0.2 + (Math.random() * 0.4)
                                    + (Math.min(damageAmount, 30) * 0.02 * CommonConfig.bloodSprayDistance()));

                    bloodSprayEntity.setDeltaMovement(finalDir.scale(speedMultiplier));
                    entity.level().addFreshEntity(bloodSprayEntity);
                }
            }
        }
    }
}
