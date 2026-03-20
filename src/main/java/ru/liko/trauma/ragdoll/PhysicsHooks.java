package ru.liko.trauma.ragdoll;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import ru.liko.trauma.Config;
import ru.liko.trauma.Trauma;
import ru.liko.trauma.common.entity.MannequinEntity;
import ru.liko.trauma.common.entity.MannequinRagdoll;

/**
 * Hooks physics into Minecraft's event system.
 * Steps the physics world each server tick, manages player lifecycle,
 * handles multiplayer synchronization (tracking), and blocks actions during
 * ragdoll.
 */
@EventBusSubscriber(modid = Trauma.MODID)
public class PhysicsHooks {

    // ======================== CORE TICK ========================

    @SubscribeEvent
    public static void onServerTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PhysicsWorld physicsWorld = PhysicsWorld.get(level);

            // Restore saved death ragdolls on first tick after world load
            if (!physicsWorld.hasRestoredSavedData()) {
                physicsWorld.restoreFromSavedData(level);
            }

            physicsWorld.step(1f / 20f); // 20 TPS

            // Ensure every player in this level has a ragdoll entry
            for (ServerPlayer sp : level.players()) {
                physicsWorld.addPlayer(sp);
            }
        }
    }

    // ======================== PLAYER LIFECYCLE ========================

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            PhysicsWorld pw = PhysicsWorld.get(sp.serverLevel());
            pw.addPlayer(sp);

            // Send all existing orphaned ragdolls to the newly joined player
            for (PlayerRagdoll dr : pw.getOrphanedDeathRagdolls()) {
                dr.sendStateTo(sp);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            PhysicsWorld pw = PhysicsWorld.get(sp.serverLevel());
            PlayerRagdoll pr = pw.getPlayerRagdoll(sp);
            if (pr != null && pr.getMode() == PlayerRagdoll.Mode.DEATH_RAGDOLL) {
                // Don't destroy death ragdoll on logout — orphan it
                pw.orphanDeathRagdoll(sp.getUUID());
            } else {
                pw.removePlayer(sp);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!Config.DEATH_RAGDOLL_ENABLED.get()) {
                // Death ragdoll disabled — just clean up any active ragdoll
                PhysicsWorld world = PhysicsWorld.get(player.serverLevel());
                PlayerRagdoll ragdoll = world.getPlayerRagdoll(player);
                if (ragdoll != null && ragdoll.isRagdollActive()) {
                    ragdoll.setMode(PlayerRagdoll.Mode.NORMAL);
                }
                return;
            }

            PhysicsWorld world = PhysicsWorld.get(player.serverLevel());
            world.addPlayer(player); // ensure entry exists
            PlayerRagdoll ragdoll = world.getPlayerRagdoll(player);
            if (ragdoll != null) {
                // Transition to DEATH_RAGDOLL mode (starts physics if not already active)
                ragdoll.setMode(PlayerRagdoll.Mode.DEATH_RAGDOLL);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            PhysicsWorld pw = PhysicsWorld.get(sp.serverLevel());
            // If existing ragdoll is a death ragdoll — orphan it instead of destroying
            PlayerRagdoll existing = pw.getPlayerRagdoll(sp);
            if (existing != null && existing.getMode() == PlayerRagdoll.Mode.DEATH_RAGDOLL) {
                pw.orphanDeathRagdoll(sp.getUUID());
            }
            pw.addPlayer(sp);

            // Send all orphaned ragdolls to the respawned player (they likely missed the
            // broadcast while dead/transitioning)
            for (PlayerRagdoll dr : pw.getOrphanedDeathRagdolls()) {
                dr.sendStateTo(sp);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            // The old dimension's PhysicsWorld will clean up via reconcilePlayers()
            // The new dimension gets a fresh ragdoll entry
            PhysicsWorld pw = PhysicsWorld.get(sp.serverLevel());
            pw.addPlayer(sp);

            // Send orphaned ragdolls in the new dimension
            for (PlayerRagdoll dr : pw.getOrphanedDeathRagdolls()) {
                dr.sendStateTo(sp);
            }
        }
    }

    // ======================== MULTIPLAYER TRACKING SYNC ========================

    /**
     * When a player starts tracking another entity (enters render distance),
     * send ragdoll state if that entity is currently ragdolled.
     * This ensures late-joining players or players entering render distance
     * see active ragdolls immediately.
     */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer trackingPlayer))
            return;

        Entity target = event.getTarget();

        // Case 1: Tracked entity is a player with active ragdoll
        if (target instanceof ServerPlayer trackedPlayer) {
            PhysicsWorld physicsWorld = PhysicsWorld.get(trackedPlayer.serverLevel());
            PlayerRagdoll ragdoll = physicsWorld.getPlayerRagdoll(trackedPlayer);
            if (ragdoll != null && ragdoll.isRagdollActive()) {
                ragdoll.sendStateTo(trackingPlayer);
            }
        }

        // Case 2: Tracked entity is a mannequin with active ragdoll
        if (target instanceof MannequinEntity mannequin && mannequin.isRagdollActive()) {
            MannequinRagdoll mannequinRagdoll = mannequin.getMannequinRagdoll();
            if (mannequinRagdoll != null) {
                mannequinRagdoll.sendStateTo(trackingPlayer);
            }
        }
    }

    // ======================== ACTION BLOCKING DURING RAGDOLL
    // ========================

    /**
     * Prevent ragdolled players from attacking entities.
     */
    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent event) {
        if (isPlayerRagdolled(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * Prevent ragdolled players from interacting — must use concrete subclasses.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isPlayerRagdolled(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isPlayerRagdolled(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isPlayerRagdolled(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (isPlayerRagdolled(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (isPlayerRagdolled(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * Prevent ragdolled players from breaking blocks.
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (isPlayerRagdolled(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    // ======================== EXTERNAL FORCES ========================

    /**
     * Apply explosion impulse to nearby ragdolls (both player and death ragdolls).
     */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel))
            return;
        if (!Config.DEATH_RAGDOLL_REACT_TO_FORCES.get())
            return;

        PhysicsWorld pw = PhysicsWorld.get(serverLevel);
        Vec3 center = event.getExplosion().center();
        float radius = event.getExplosion().radius();

        for (PlayerRagdoll ragdoll : pw.getAllActivePlayerRagdolls()) {
            if (!ragdoll.hasBodies())
                continue;
            Vec3 torso = ragdoll.getTorsoPosition();
            if (torso == null)
                continue;

            double distance = torso.distanceTo(center);
            if (distance < radius * 2.5) {
                ragdoll.applyExplosionImpulse(center, radius);
            }
        }
    }

    /**
     * Apply damage impulse to death ragdolls when the dead player entity takes
     * damage
     * (e.g., from projectiles, fire, cacti).
     */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player))
            return;
        if (!player.isDeadOrDying())
            return;
        if (!Config.DEATH_RAGDOLL_REACT_TO_FORCES.get())
            return;

        PhysicsWorld pw = PhysicsWorld.get(player.serverLevel());
        PlayerRagdoll ragdoll = pw.getPlayerRagdoll(player);
        if (ragdoll == null || ragdoll.getMode() != PlayerRagdoll.Mode.DEATH_RAGDOLL)
            return;
        if (!ragdoll.hasBodies())
            return;

        float amount = event.getNewDamage();
        if (amount <= 0f)
            return;

        // Determine knockback direction from damage source
        Entity source = event.getSource().getEntity();
        if (source != null) {
            Vec3 torso = ragdoll.getTorsoPosition();
            Vec3 ragdollPos = torso != null ? torso : player.position();
            Vec3 direction = ragdollPos.subtract(source.position()).normalize();
            ragdoll.applyKnockbackImpulse(direction, Math.min(amount * 0.5f, 10f));
        } else {
            // Random upward push for directionless damage
            ragdoll.applyKnockbackImpulse(new Vec3(0, 1, 0), Math.min(amount * 0.3f, 6f));
        }
    }

    // ======================== SERVER SHUTDOWN ========================

    /**
     * Save death ragdolls and clean up all physics worlds on server stop.
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // Save death ragdolls before cleanup
        for (PhysicsWorld pw : PhysicsWorld.getAll()) {
            pw.saveDeathRagdolls(pw.getLevel());
        }
        PhysicsWorld.cleanupAll();
    }

    /**
     * Clean up physics world when a dimension is unloaded.
     */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PhysicsWorld pw = PhysicsWorld.get(level);
            pw.saveDeathRagdolls(level);
            PhysicsWorld.remove(level);
        }
    }

    // ======================== UTILITY ========================

    /**
     * Check if a player is currently in ragdoll mode on the server.
     * Returns false for non-server players or null input.
     */
    public static boolean isPlayerRagdolled(Player player) {
        if (!(player instanceof ServerPlayer sp))
            return false;
        PhysicsWorld physicsWorld = PhysicsWorld.get(sp.serverLevel());
        PlayerRagdoll ragdoll = physicsWorld.getPlayerRagdoll(sp);
        return ragdoll != null && ragdoll.isRagdollActive();
    }

    /**
     * Check if a player is ragdolled OR has an active death ragdoll.
     * Useful for input blocking during death.
     */
    public static boolean isPlayerOrDeathRagdolled(Player player) {
        if (!(player instanceof ServerPlayer sp))
            return false;
        PhysicsWorld physicsWorld = PhysicsWorld.get(sp.serverLevel());
        PlayerRagdoll ragdoll = physicsWorld.getPlayerRagdoll(sp);
        return ragdoll != null && ragdoll.isRagdollActive();
    }
}
