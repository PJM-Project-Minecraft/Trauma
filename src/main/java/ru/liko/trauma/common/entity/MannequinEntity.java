package ru.liko.trauma.common.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ru.liko.trauma.ragdoll.PhysicsWorld;
import ru.liko.trauma.ragdoll.PlayerRagdoll;
import ru.liko.trauma.ragdoll.RagdollPart;

import javax.annotation.Nullable;

/**
 * A mannequin entity for testing ragdoll physics.
 * Right-click to toggle ragdoll mode.
 * Hit it to apply impulse while in ragdoll mode.
 * Sneak + right-click to remove it.
 */
public class MannequinEntity extends Mob {

    private static final EntityDataAccessor<Boolean> DATA_RAGDOLL_ACTIVE =
            SynchedEntityData.defineId(MannequinEntity.class, EntityDataSerializers.BOOLEAN);

    // Server-side ragdoll reference (uses player ragdoll system via a fake player UUID approach)
    private MannequinRagdoll mannequinRagdoll;

    public MannequinEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.setNoGravity(false);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_RAGDOLL_ACTIVE, false);
    }

    public boolean isRagdollActive() {
        return this.entityData.get(DATA_RAGDOLL_ACTIVE);
    }

    public void setRagdollActive(boolean active) {
        this.entityData.set(DATA_RAGDOLL_ACTIVE, active);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide && hand == InteractionHand.MAIN_HAND) {
            if (player.isShiftKeyDown()) {
                // Sneak + right-click: remove mannequin
                if (mannequinRagdoll != null) {
                    mannequinRagdoll.destroy();
                    mannequinRagdoll = null;
                }
                this.discard();
                return InteractionResult.SUCCESS;
            }

            // Toggle ragdoll mode
            if (isRagdollActive()) {
                deactivateRagdoll();
            } else {
                activateRagdoll();
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide) return false;

        if (isRagdollActive() && mannequinRagdoll != null) {
            // Apply impulse from damage direction
            Entity attacker = source.getEntity();
            if (attacker != null) {
                Vec3 dir = this.position().subtract(attacker.position()).normalize();
                mannequinRagdoll.applyKnockbackImpulse(dir, amount * 3f);

                // Also apply upward + random force
                mannequinRagdoll.applyRandomVelocity(amount * 2f, amount, amount * 2f);
            }
            return true;
        }

        boolean result = super.hurt(source, amount);
        // Activate ragdoll on death
        if (result && this.isDeadOrDying() && !isRagdollActive()) {
            activateRagdoll();
        }
        return result;
    }

    @Override
    public void kill() {
        // /kill command: forcefully remove the mannequin and clean up ragdoll
        if (mannequinRagdoll != null) {
            if (this.level() instanceof ServerLevel serverLevel) {
                PhysicsWorld.get(serverLevel).unregisterMannequinRagdoll(mannequinRagdoll);
            }
            mannequinRagdoll.destroy();
            mannequinRagdoll = null;
        }
        setRagdollActive(false);
        this.discard();
    }

    @Override
    protected void tickDeath() {
        // Don't call super — prevents death animation and entity removal
        // Mannequin stays as ragdoll when dead.
        // Physics stepping + network updates are handled by PhysicsWorld.step() → afterStep()
    }

    @Override
    public void tick() {
        super.tick();
        // Ragdoll physics and network updates are handled by PhysicsWorld.step() → afterStep()
        // (NOT called here to avoid stutter from sending pre-step positions)
    }

    private void activateRagdoll() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        setRagdollActive(true);
        mannequinRagdoll = new MannequinRagdoll(this, serverLevel);
        mannequinRagdoll.enterRagdollMode();

        // Register with PhysicsWorld so afterStep() is called AFTER physics step (fixes stutter)
        PhysicsWorld.get(serverLevel).registerMannequinRagdoll(mannequinRagdoll);

        // Keep entity interactable (don't setInvisible) — ragdoll renderer cancels normal rendering
        this.setNoGravity(true);
    }

    private void deactivateRagdoll() {
        setRagdollActive(false);
        if (mannequinRagdoll != null) {
            // Unregister from PhysicsWorld first
            if (this.level() instanceof ServerLevel serverLevel) {
                PhysicsWorld.get(serverLevel).unregisterMannequinRagdoll(mannequinRagdoll);
            }
            Vec3 ragdollPos = mannequinRagdoll.getTorsoPosition();
            if (ragdollPos != null) {
                this.teleportTo(ragdollPos.x, ragdollPos.y, ragdollPos.z);
            }
            mannequinRagdoll.destroy();
            mannequinRagdoll = null;
        }
        this.setNoGravity(false);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (mannequinRagdoll != null) {
            if (this.level() instanceof ServerLevel serverLevel) {
                PhysicsWorld.get(serverLevel).unregisterMannequinRagdoll(mannequinRagdoll);
            }
            mannequinRagdoll.destroy();
            mannequinRagdoll = null;
        }
        super.remove(reason);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("RagdollActive", isRagdollActive());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // Don't restore ragdoll state on load — ragdolls are transient
        setRagdollActive(false);
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distSq) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return !isRagdollActive();
    }

    @Override
    protected void registerGoals() {
        // No AI goals — mannequin is stationary
    }

    @Nullable
    public MannequinRagdoll getMannequinRagdoll() {
        return mannequinRagdoll;
    }
}
