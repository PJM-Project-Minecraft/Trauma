package ru.liko.trauma.common.event;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import ru.liko.trauma.Trauma;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles projectile impacts. Caches the kinetic impulse (p = mass × velocity)
 * so that when the ragdoll is created a few ticks later it can apply a point
 * impulse to the nearest body part, making fall direction and speed depend on
 * where and how hard the projectile hit.
 */
@EventBusSubscriber(modid = Trauma.MODID)
public class DamageEventHandler {

    // ---------------------------------------------------------------
    // PendingImpact record — consumed once by PlayerRagdoll on entry
    // ---------------------------------------------------------------

    /**
     * Projectile impact snapshot: world-space hit position and momentum vector.
     * Momentum p = projectileMass × velocity (in Minecraft block/tick units,
     * scaled to physics units).
     */
    public record PendingImpact(Vec3 hitPos, Vec3 impulse) {}

    /** Impacts keyed by victim UUID; consumed once when the ragdoll activates. */
    private static final ConcurrentHashMap<UUID, PendingImpact> PENDING = new ConcurrentHashMap<>();

    /**
     * Retrieve and remove the stored impact for {@code uuid}.
     * Returns {@code null} if no impact was registered.
     */
    public static PendingImpact pollImpact(UUID uuid) {
        return PENDING.remove(uuid);
    }

    // ---------------------------------------------------------------
    // Estimated masses for common projectile types (in kg-equivalent)
    // These are tuned for feel, not real-world accuracy.
    // ---------------------------------------------------------------
    private static final float MASS_ARROW       = 0.05f;
    private static final float MASS_FIREBALL    = 2.0f;
    private static final float MASS_WITHER_SKULL = 3.0f;
    private static final float MASS_SNOWBALL    = 0.1f;
    private static final float MASS_EGG         = 0.08f;
    private static final float MASS_TRIDENT      = 0.25f;
    private static final float MASS_DEFAULT      = 0.15f;

    /** Physics scale factor: converts Minecraft momentum to Bullet impulse magnitude. */
    private static final float IMPULSE_SCALE = 30f;

    // ---------------------------------------------------------------
    // Event handler
    // ---------------------------------------------------------------

    @SubscribeEvent
    public static void onProjectileHit(ProjectileImpactEvent event) {
        if (event.getRayTraceResult().getType() != HitResult.Type.ENTITY) return;

        EntityHitResult hitResult = (EntityHitResult) event.getRayTraceResult();
        if (!(hitResult.getEntity() instanceof LivingEntity victim)) return;
        if (victim.level().isClientSide()) return;

        if (victim instanceof Player p && (p.isCreative() || p.isSpectator())) return;

        // Reset invulnerability so the projectile always deals damage even on rapid hits
        if (victim.invulnerableTime > 0) victim.invulnerableTime = 0;

        // --- Compute and cache the kinetic impulse ---
        Projectile proj = event.getProjectile();
        Vec3 vel = proj.getDeltaMovement();

        float mass = estimateMass(proj);
        // p = m × v, scaled to physics magnitude
        Vec3 impulse = vel.scale(mass * IMPULSE_SCALE);

        Vec3 hitPos = hitResult.getLocation();
        PENDING.put(victim.getUUID(), new PendingImpact(hitPos, impulse));
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private static float estimateMass(Projectile proj) {
        if (proj instanceof AbstractArrow) {
            // Velocity-based mass: power-2 arrow = more force than a lobbed arrow
            float speed = (float) proj.getDeltaMovement().length();
            return MASS_ARROW * (speed / 1.5f);
        }
        if (proj instanceof AbstractHurtingProjectile) {
            String name = proj.getType().toShortString();
            if (name.contains("wither")) return MASS_WITHER_SKULL;
            return MASS_FIREBALL;
        }
        if (proj instanceof ThrowableProjectile) {
            String name = proj.getType().toShortString();
            if (name.contains("trident")) return MASS_TRIDENT;
            if (name.contains("snowball")) return MASS_SNOWBALL;
            if (name.contains("egg"))     return MASS_EGG;
        }
        return MASS_DEFAULT;
    }
}
