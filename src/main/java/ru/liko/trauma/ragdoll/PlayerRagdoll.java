package ru.liko.trauma.ragdoll;

import com.bulletphysics.collision.dispatch.CollisionFlags;
import com.bulletphysics.collision.dispatch.CollisionObject;
import com.bulletphysics.collision.narrowphase.ManifoldPoint;
import com.bulletphysics.collision.narrowphase.PersistentManifold;
import com.bulletphysics.collision.shapes.BoxShape;
import com.bulletphysics.collision.shapes.CollisionShape;
import com.bulletphysics.dynamics.DiscreteDynamicsWorld;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.dynamics.constraintsolver.Generic6DofConstraint;
import com.bulletphysics.dynamics.constraintsolver.TypedConstraint;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.Transform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import ru.liko.trauma.network.RagdollEndPayload;
import ru.liko.trauma.network.RagdollStartPayload;
import ru.liko.trauma.network.RagdollUpdatePayload;

import javax.vecmath.Quat4f;
import javax.vecmath.Vector3d;
import javax.vecmath.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import javax.annotation.Nullable;

import ru.liko.trauma.Config;
import ru.liko.trauma.common.event.DamageEventHandler;

/**
 * Server-side ragdoll physics for a single player.
 * Two modes: NORMAL (no ragdoll — player moves freely) and RAGDOLL (full
 * physics simulation).
 */
public class PlayerRagdoll {

    public enum Mode {
        NORMAL, RAGDOLL, DEATH_RAGDOLL
    }

    private final UUID playerUUID;
    private final PhysicsWorld physicsWorld;
    private final DiscreteDynamicsWorld world;
    private Mode mode = Mode.NORMAL;

    // Ragdoll rigid bodies (6 parts: torso, head, lleg, rleg, larm, rarm)
    private final List<RigidBody> ragdollParts = new ArrayList<>(6);
    private final List<TypedConstraint> ragdollJoints = new ArrayList<>(5);

    // Static collision bodies from nearby blocks
    private final List<CollisionObject> localStaticCollision = new ArrayList<>();
    private BlockPos lastCollisionCenter = BlockPos.ZERO;
    private long lastCollisionRebuildGameTick = -1;

    /** Throttled entity↔torso teleport in RAGDOLL / DEATH_ENTITY_STATE ({@link Double#NaN} = no prior sample). */
    private double lastEntityTorsoSyncX = Double.NaN;
    private double lastEntityTorsoSyncY = Double.NaN;
    private double lastEntityTorsoSyncZ = Double.NaN;

    private int networkRagdollId;

    // Death ragdoll state
    private int deathTicksRemaining = -1;
    private boolean orphaned = false; // true when player logged out/respawned but ragdoll persists

    /**
     * Structural HP of physics corpse (death mode). When ≤0 the mesh is removed early without
     * waiting for the countdown timer.
     */
    private float corpseIntegrity = -1f;
    private int corpseBloodDripCounter;

    public PlayerRagdoll(ServerPlayer player, PhysicsWorld physicsWorld) {
        this.playerUUID = player.getUUID();
        this.physicsWorld = physicsWorld;
        this.world = physicsWorld.getDynamicsWorld();
        this.networkRagdollId = player.getId();
    }

    /**
     * Private constructor for creating from saved data (no player entity needed).
     */
    private PlayerRagdoll(PhysicsWorld physicsWorld, UUID uuid, int networkRagdollId) {
        this.playerUUID = uuid;
        this.physicsWorld = physicsWorld;
        this.world = physicsWorld.getDynamicsWorld();
        this.networkRagdollId = networkRagdollId;
    }

    /**
     * Create a death ragdoll from saved data (restoring after world load).
     * The ragdoll is created orphaned and in DEATH_RAGDOLL mode.
     */
    public static PlayerRagdoll createFromSavedData(
            PhysicsWorld physicsWorld, UUID uuid, int networkRagdollId,
            int deathTicksRemaining, RagdollTransform[] transforms, float corpseIntegritySaved) {
        PlayerRagdoll ragdoll = new PlayerRagdoll(physicsWorld, uuid, networkRagdollId);
        ragdoll.mode = Mode.DEATH_RAGDOLL;
        ragdoll.orphaned = true;
        ragdoll.deathTicksRemaining = deathTicksRemaining;
        float maxIntegrity = (float) Config.DEATH_CORPSE_INTEGRITY.get().doubleValue();
        ragdoll.corpseIntegrity = Float.isFinite(corpseIntegritySaved) && corpseIntegritySaved > 0f
                ? Math.min(corpseIntegritySaved, maxIntegrity * 2f) : maxIntegrity;
        ragdoll.createRagdollBodiesFromTransforms(transforms);
        return ragdoll;
    }

    private ServerPlayer getPlayerSafe() {
        return physicsWorld.getServerPlayer(playerUUID);
    }

    // ======================== MODE MANAGEMENT ========================

    public Mode getMode() {
        return mode;
    }

    /** Check if ragdoll is currently active (physics simulating). */
    public boolean isRagdollActive() {
        return mode == Mode.RAGDOLL || mode == Mode.DEATH_RAGDOLL;
    }

    public void setMode(Mode newMode) {
        if (newMode == this.mode)
            return;
        if (newMode == Mode.RAGDOLL) {
            enterRagdollMode();
        } else if (newMode == Mode.DEATH_RAGDOLL) {
            enterDeathRagdollMode();
        } else {
            exitRagdollMode();
        }
        this.mode = newMode;
    }

    public boolean isOrphaned() {
        return orphaned;
    }

    public void setOrphaned(boolean orphaned) {
        this.orphaned = orphaned;
    }

    public int getDeathTicksRemaining() {
        return deathTicksRemaining;
    }

    /** For persistence; valid only in {@link Mode#DEATH_RAGDOLL}. */
    public float getCorpseIntegritySnapshot() {
        return mode == Mode.DEATH_RAGDOLL ? corpseIntegrity : -1f;
    }

    /**
     * Apply damage to corpse integrity. When health falls to 0 the physics mesh is dissolved.
     *
     * @return {@code true} if corpse was destroyed this call
     */
    public boolean applyCorpseIntegrityLoss(float amount) {
        if (mode != Mode.DEATH_RAGDOLL || ragdollParts.isEmpty() || corpseIntegrity <= 0f)
            return false;
        if (amount <= 0f)
            return false;
        corpseIntegrity -= amount;
        if (corpseIntegrity <= 0f) {
            dissolveDeathCorpse();
            return true;
        }
        return false;
    }

    /** Remove corpse immediately — same outward effect as duration timer expiry or integrity depleted. */
    private void dissolveDeathCorpse() {
        endDeathCorpseSession();
    }

    private void endDeathCorpseSession() {
        cleanupBodiesAndNotify();
        mode = Mode.NORMAL;
        deathTicksRemaining = -1;
        corpseIntegrity = -1f;
        corpseBloodDripCounter = 0;
    }

    public int getNetworkRagdollId() {
        return networkRagdollId;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    // Allows changing the network ID when the ragdoll is orphaned
    public void setNetworkRagdollId(int newId) {
        this.networkRagdollId = newId;
    }

    private void enterRagdollMode() {
        ServerPlayer player = getPlayerSafe();
        if (player == null)
            return;

        createRagdollBodies();
        applyPendingProjectileImpact(player);

        // Freeze player immediately — prevent movement/gravity fighting the ragdoll
        freezePlayer(player);

        // Send start packet to all clients
        RagdollStartPayload payload = new RagdollStartPayload(player.getId(), networkRagdollId, playerUUID, getRagdollTransforms());
        PacketDistributor.sendToAllPlayers(payload);
    }

    /**
     * Enter death ragdoll mode. If already in RAGDOLL mode, just switch; otherwise
     * create bodies.
     * The death ragdoll persists for a configurable duration and doesn't freeze the
     * player.
     */
    private void enterDeathRagdollMode() {
        deathTicksRemaining = Config.DEATH_RAGDOLL_DURATION.get() * 20; // seconds → ticks

        corpseIntegrity = (float) Config.DEATH_CORPSE_INTEGRITY.get().doubleValue();
        corpseBloodDripCounter = 0;

        if (ragdollParts.isEmpty()) {
            // Not already ragdolled — create fresh ragdoll bodies
            ServerPlayer player = getPlayerSafe();
            if (player == null)
                return;

            createRagdollBodies();
            applyPendingProjectileImpact(player);

            // Send start packet to all players tracking this entity (and self) so others see the death ragdoll
            RagdollStartPayload payload = new RagdollStartPayload(player.getId(), networkRagdollId, playerUUID,
                    getRagdollTransforms());
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, payload);

            // Immediately snap entity to ragdoll position and shrink hitbox
            applyDeathEntityState(player);
        } else {
            // Was already in RAGDOLL mode before death — bodies exist, apply death entity state
            ServerPlayer player = getPlayerSafe();
            if (player != null) {
                resetEntityTorsoSyncThrottle();
                applyDeathEntityState(player);
            }
        }
    }

    /**
     * Moves the dead player entity to the ragdoll torso centre and sets the
     * SLEEPING pose so that the hitbox (0.2×0.2) no longer floats at the
     * standing-death position. Called once on death and then every physics tick.
     * <p>
     * {@code teleportTo} is throttled: small torso jitter no longer retriggers full
     * entity reposition every tick ({@linkplain #TORSO_ENTITY_TELEPORT_EPSILON_SQ}),
     * with a periodic resync every {@value #TORSO_ENTITY_RESYNC_TICKS} ticks so
     * long-term drift stays bounded.
     */
    private void applyDeathEntityState(ServerPlayer player) {
        if (ragdollParts.isEmpty())
            return;

        Transform torsoT = new Transform();
        ragdollParts.get(0).getMotionState().getWorldTransform(torsoT);

        syncEntityXYZToTorsoIfNeeded(player, torsoT.origin.x, torsoT.origin.y, torsoT.origin.z);

        // Shrink hitbox to SLEEPING (0.2 × 0.2) so mobs don't target the standing position
        if (player.getPose() != Pose.SLEEPING) {
            player.setPose(Pose.SLEEPING);
            player.refreshDimensions();
        }
    }

    private void exitRagdollMode() {
        ServerPlayer player = getPlayerSafe();

        // Capture final torso position before removing bodies
        Vec3 finalPos = null;
        if (!ragdollParts.isEmpty()) {
            Transform torsoTransform = new Transform();
            ragdollParts.get(0).getMotionState().getWorldTransform(torsoTransform);
            finalPos = new Vec3(torsoTransform.origin.x, torsoTransform.origin.y, torsoTransform.origin.z);
        }

        // Remove all ragdoll bodies and joints
        for (TypedConstraint c : ragdollJoints)
            world.removeConstraint(c);
        for (RigidBody r : ragdollParts)
            world.removeRigidBody(r);
        ragdollJoints.clear();
        ragdollParts.clear();

        // Send end packet to all clients
        RagdollEndPayload payload = new RagdollEndPayload(networkRagdollId);
        PacketDistributor.sendToAllPlayers(payload);

        // Unfreeze player and teleport to ragdoll's last position
        if (player != null) {
            player.setNoGravity(false);
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true; // force client to accept server position
            if (finalPos != null) {
                player.teleportTo(finalPos.x, finalPos.y, finalPos.z);
            }
        }
    }

    /**
     * Send current ragdoll state to a specific player.
     * Used when a player starts tracking this ragdolled player (late join / enters
     * render distance).
     */
    public void sendStateTo(ServerPlayer target) {
        if ((mode != Mode.RAGDOLL && mode != Mode.DEATH_RAGDOLL) || ragdollParts.isEmpty())
            return;

        RagdollStartPayload payload = new RagdollStartPayload(
                networkRagdollId, networkRagdollId, playerUUID, getRagdollTransforms());
        PacketDistributor.sendToPlayer(target, payload);
    }

    /**
     * Freeze a player during ragdoll mode:
     * - Zero out all movement
     * - Disable gravity (physics simulation handles it)
     * - Force client to accept server position
     */
    private void freezePlayer(ServerPlayer player) {
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true; // forces the client to accept the server's position/velocity
        player.setNoGravity(true);
        player.setSprinting(false);
        player.stopUsingItem();
    }

    /**
     * Reset torso↔player {@link ServerPlayer#teleportTo} debounce after creating/removing ragdoll
     * bodies so the next sample always syncs immediately.
     */
    private void resetEntityTorsoSyncThrottle() {
        lastEntityTorsoSyncX = Double.NaN;
    }

    /** Throttled {@code teleportTo} following the simulated torso rigid body centre. */
    private void syncEntityXYZToTorsoIfNeeded(ServerPlayer player, double x, double y, double z) {
        long tick = physicsWorld.getLevel().getGameTime();

        boolean firstSample = Double.isNaN(lastEntityTorsoSyncX);
        boolean movedEnough = false;
        if (!firstSample) {
            double dx = x - lastEntityTorsoSyncX;
            double dy = y - lastEntityTorsoSyncY;
            double dz = z - lastEntityTorsoSyncZ;
            movedEnough = dx * dx + dy * dy + dz * dz > TORSO_ENTITY_TELEPORT_EPSILON_SQ;
        }

        boolean periodicResync = !firstSample && tick % TORSO_ENTITY_RESYNC_TICKS == 0L;

        if (!firstSample && !movedEnough && !periodicResync) return;

        player.teleportTo(x, y, z);
        lastEntityTorsoSyncX = x;
        lastEntityTorsoSyncY = y;
        lastEntityTorsoSyncZ = z;
    }

    // ======================== TICK / AFTER STEP ========================

    public void afterStep() {
        if (mode == Mode.DEATH_RAGDOLL) {
            afterStepDeathRagdoll();
            return;
        }
        if (mode != Mode.RAGDOLL)
            return;
        if (ragdollParts.isEmpty())
            return;

        ServerPlayer player = getPlayerSafe();
        if (player == null) {
            // Player left (dimension change / disconnect between ticks).
            // Send end packet so clients remove their ragdoll state, then clean up.
            try {
                PacketDistributor.sendToAllPlayers(new RagdollEndPayload(networkRagdollId));
            } catch (Exception ignored) {}
            cleanupBodiesOnly();
            mode = Mode.NORMAL;
            return;
        }

        // Read ragdoll transforms and send to tracking clients.
        // Use game-tick time (deterministic, no wall-clock skew under server lag).
        RagdollTransform[] transforms = getRagdollTransforms();
        long gameTimeMs = physicsWorld.getLevel().getGameTime() * 50L;
        RagdollUpdatePayload payload = new RagdollUpdatePayload(networkRagdollId, gameTimeMs, transforms);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, payload);

        // Teleport player to torso (throttled — {@code teleportTo} is heavyweight on MC server)
        RigidBody torsoBody = ragdollParts.get(0);
        Transform torsoTransform = new Transform();
        torsoBody.getMotionState().getWorldTransform(torsoTransform);
        Vector3f pos = torsoTransform.origin;
        syncEntityXYZToTorsoIfNeeded(player, pos.x, pos.y, pos.z);

        // Continuously freeze player during ragdoll — prevents any movement fighting
        freezePlayer(player);

        // Clamp velocities to prevent physics explosion
        clampAllLinearVelocities((float) Config.RAGDOLL_MAX_LINEAR_SPEED.get().doubleValue());

        applyFluidForces();
        updateLocalWorldCollision();
        correctInterpenetrations();
    }

    /**
     * afterStep logic for DEATH_RAGDOLL mode.
     * Physics continues running, updates sent to nearby players, timer counts down.
     * Does NOT freeze or teleport any player entity.
     */
    private void afterStepDeathRagdoll() {
        if (ragdollParts.isEmpty()) {
            for (CollisionObject co : localStaticCollision)
                world.removeCollisionObject(co);
            localStaticCollision.clear();
            mode = Mode.NORMAL;
            return;
        }

        // Countdown timer
        deathTicksRemaining--;
        if (deathTicksRemaining <= 0) {
            endDeathCorpseSession();
            return;
        }

        // Только пока сущность игрока реально «мертва». После принудительного respawn
        // (другой мод / PlayerList.respawn) UUID совпадает, но игрок уже жив в этом же
        // или другом измерении — иначе каждый тик телепортировали бы живого к телу трупа
        // и ломали загрузку чанков / позицию клиента.
        if (!orphaned) {
            ServerPlayer dyingPlayer = getPlayerSafe();
            if (dyingPlayer != null && dyingPlayer.isDeadOrDying()) {
                applyDeathEntityState(dyingPlayer);
            }
        }

        // Send updates to all players in the dimension (game-tick timestamp)
        RagdollTransform[] transforms = getRagdollTransforms();
        ServerLevel level = physicsWorld.getLevel();
        long gameTimeMs = level.getGameTime() * 50L;
        RagdollUpdatePayload payload = new RagdollUpdatePayload(networkRagdollId, gameTimeMs, transforms);
        for (ServerPlayer sp : level.players()) {
            PacketDistributor.sendToPlayer(sp, payload);
        }

        // Clamp velocities
        clampAllLinearVelocities(getDeathCorpseMaxSpeed());

        applyFluidForces();
        updateLocalWorldCollisionFromTorso();
        correctInterpenetrations();

        corpseBloodDripCounter++;
        if (Config.RAGDOLL_DEATH_CORPSE_BLOOD_DRIP.get()
                && corpseBloodDripCounter >= Config.RAGDOLL_DEATH_CORPSE_BLOOD_DRIP_INTERVAL.get()) {
            corpseBloodDripCounter = 0;
            RagdollPart[] parts = RagdollPart.values();
            RagdollPart pick = parts[level.random.nextInt(parts.length)];
            RagdollTransform tr = pickPartTransformForBleed(pick, transforms);
            if (tr != null) {
                RagdollCorpseBlood.trySpawnSlowDrip(
                        level,
                        new Vec3(tr.position.x, tr.position.y, tr.position.z),
                        level.random);
            }
        }
    }

    /** Match {@linkplain #corpseBloodDripCounter} pacing with latest physics snapshot array. */
    @Nullable
    private static RagdollTransform pickPartTransformForBleed(RagdollPart part, RagdollTransform[] frame) {
        if (frame == null || part.index >= frame.length)
            return null;
        RagdollTransform t = frame[part.index];
        return t;
    }

    /**
     * Clean up bodies and send RagdollEndPayload. Used by death ragdoll timer
     * expiry.
     */
    private void cleanupBodiesAndNotify() {
        try {
            RagdollEndPayload endPayload = new RagdollEndPayload(networkRagdollId);
            for (ServerPlayer sp : physicsWorld.getLevel().players()) {
                PacketDistributor.sendToPlayer(sp, endPayload);
            }
        } catch (Exception ignored) {
        }
        cleanupBodiesOnly();
    }

    // ======================== RAGDOLL BODY CREATION ========================

    private void createRagdollBodies() {
        ServerPlayer player = getPlayerSafe();
        if (player == null)
            return;

        Vec3 plM = player.getDeltaMovement();
        Vector3f motion = new Vector3f((float) plM.x * 20, (float) plM.y * 20, (float) plM.z * 20);

        float xRot = 0;
        if (player.getPose() == Pose.SWIMMING) {
            xRot = 90;
        }

        Vector3d pos = new Vector3d(player.getX(), player.getY(), player.getZ());
        Quaternionf q = new Quaternionf().rotateXYZ(
                (float) Math.toRadians(xRot),
                (float) Math.toRadians(180 - player.getYRot()),
                0f);
        Quat4f qq = new Quat4f(q.x, q.y, q.z, q.w);

        Vector3f torsoOrigin = new Vector3f((float) pos.x, (float) pos.y + 1.3f, (float) pos.z);

        // Helper to rotate a local offset into world space
        Function<Vector3f, Vector3f> worldOffset = (local) -> {
            Vector3f result = new Vector3f(local);
            org.joml.Vector3f temp = new org.joml.Vector3f(result.x, result.y, result.z);
            q.transform(temp);
            result = new Vector3f(temp.x, temp.y, temp.z);
            result.add(torsoOrigin);
            return result;
        };

        // --- Torso (heaviest: ~40% of body mass) ---
        CollisionShape torsoShape = new BoxShape(new Vector3f(0.25f, 0.4f, 0.15f));
        Transform torsoT = new Transform();
        torsoT.setIdentity();
        torsoT.setRotation(qq);
        torsoT.origin.set(torsoOrigin);
        RigidBody torsoBody = new RigidBody(makeInfo(MASS_TORSO, torsoT, torsoShape));
        torsoBody.setLinearVelocity(motion);
        torsoBody.setRestitution(0.0f);
        torsoBody.setDamping(LINEAR_DAMPING, ANGULAR_DAMPING);
        torsoBody.setSleepingThresholds(0.1f, 0.1f);
        torsoBody.setCcdMotionThreshold(0.01f);
        torsoBody.setCcdSweptSphereRadius(0.4f);
        world.addRigidBody(torsoBody);
        ragdollParts.add(torsoBody);

        // --- Head ---
        CollisionShape headShape = new BoxShape(new Vector3f(0.2f, 0.2f, 0.2f));
        Vector3f headPos = worldOffset.apply(new Vector3f(0f, 0.55f, 0f));
        Transform headT = new Transform();
        headT.setIdentity();
        headT.origin.set(headPos);
        headT.setRotation(qq);
        RigidBody headBody = new RigidBody(makeInfo(MASS_HEAD, headT, headShape));
        headBody.setLinearVelocity(motion);
        headBody.setCcdMotionThreshold(0.01f);
        headBody.setDamping(LINEAR_DAMPING, ANGULAR_DAMPING);
        headBody.setCcdSweptSphereRadius(0.25f);
        world.addRigidBody(headBody);
        ragdollParts.add(headBody);

        // --- Left Leg ---
        CollisionShape legShape = new BoxShape(new Vector3f(0.15f, 0.45f, 0.15f));
        Vector3f lLegPos = worldOffset.apply(new Vector3f(-0.1f, -0.75f, 0f));
        Transform lLegT = new Transform();
        lLegT.setIdentity();
        lLegT.origin.set(lLegPos);
        lLegT.setRotation(qq);
        RigidBody lLegBody = new RigidBody(makeInfo(MASS_LEG, lLegT, legShape));
        lLegBody.setLinearVelocity(motion);
        lLegBody.setCcdMotionThreshold(0.01f);
        lLegBody.setDamping(LINEAR_DAMPING, ANGULAR_DAMPING);
        lLegBody.setCcdSweptSphereRadius(0.35f);
        world.addRigidBody(lLegBody);
        ragdollParts.add(lLegBody);

        // --- Right Leg ---
        Vector3f rLegPos = worldOffset.apply(new Vector3f(0.1f, -0.75f, 0f));
        Transform rLegT = new Transform();
        rLegT.setIdentity();
        rLegT.origin.set(rLegPos);
        rLegT.setRotation(qq);
        RigidBody rLegBody = new RigidBody(makeInfo(MASS_LEG, rLegT, legShape));
        rLegBody.setLinearVelocity(motion);
        rLegBody.setCcdMotionThreshold(0.01f);
        rLegBody.setDamping(LINEAR_DAMPING, ANGULAR_DAMPING);
        rLegBody.setCcdSweptSphereRadius(0.35f);
        world.addRigidBody(rLegBody);
        ragdollParts.add(rLegBody);

        // --- Left Arm ---
        CollisionShape armShape = new BoxShape(new Vector3f(0.1f, 0.35f, 0.1f));
        Vector3f lArmPos = worldOffset.apply(new Vector3f(-0.4f, 0.05f, 0f));
        Transform lArmT = new Transform();
        lArmT.setIdentity();
        lArmT.origin.set(lArmPos);
        lArmT.setRotation(qq);
        RigidBody lArmBody = new RigidBody(makeInfo(MASS_ARM, lArmT, armShape));
        lArmBody.setLinearVelocity(motion);
        lArmBody.setCcdMotionThreshold(0.01f);
        lArmBody.setDamping(LINEAR_DAMPING, ANGULAR_DAMPING);
        lArmBody.setCcdSweptSphereRadius(0.3f);
        world.addRigidBody(lArmBody);
        ragdollParts.add(lArmBody);

        // --- Right Arm ---
        Vector3f rArmPos = worldOffset.apply(new Vector3f(0.4f, 0.05f, 0f));
        Transform rArmT = new Transform();
        rArmT.setIdentity();
        rArmT.origin.set(rArmPos);
        rArmT.setRotation(qq);
        RigidBody rArmBody = new RigidBody(makeInfo(MASS_ARM, rArmT, armShape));
        rArmBody.setLinearVelocity(motion);
        rArmBody.setCcdMotionThreshold(0.01f);
        rArmBody.setDamping(LINEAR_DAMPING, ANGULAR_DAMPING);
        rArmBody.setCcdSweptSphereRadius(0.3f);
        world.addRigidBody(rArmBody);
        ragdollParts.add(rArmBody);

        createRagdollJoints();
        updateLocalWorldCollision();
        resetEntityTorsoSyncThrottle();
    }

    /**
     * Create ragdoll bodies from saved transform data (for restoring death ragdolls
     * on world load).
     * Creates 6 bodies at the specified positions/rotations with saved velocities.
     */
    private void createRagdollBodiesFromTransforms(RagdollTransform[] transforms) {
        if (transforms == null || transforms.length < 6)
            return;

        // Part shapes and masses must match createRagdollBodies() exactly.
        float[] masses = { MASS_TORSO, MASS_HEAD, MASS_LEG, MASS_LEG, MASS_ARM, MASS_ARM };
        CollisionShape[] shapes = {
                new BoxShape(new Vector3f(0.25f, 0.4f, 0.15f)), // torso
                new BoxShape(new Vector3f(0.2f, 0.2f, 0.2f)), // head
                new BoxShape(new Vector3f(0.15f, 0.45f, 0.15f)), // left leg
                new BoxShape(new Vector3f(0.15f, 0.45f, 0.15f)), // right leg
                new BoxShape(new Vector3f(0.1f, 0.35f, 0.1f)), // left arm
                new BoxShape(new Vector3f(0.1f, 0.35f, 0.1f)) // right arm
        };
        float[] ccdRadii = { 0.4f, 0.25f, 0.35f, 0.35f, 0.3f, 0.3f };

        for (int i = 0; i < 6; i++) {
            RagdollTransform rt = transforms[i];
            Transform bodyT = new Transform();
            bodyT.setIdentity();
            bodyT.origin.set(rt.position.x, rt.position.y, rt.position.z);
            bodyT.setRotation(new Quat4f(rt.rotation.x, rt.rotation.y, rt.rotation.z, rt.rotation.w));

            RigidBody body = new RigidBody(makeInfo(masses[i], bodyT, shapes[i]));
            body.setLinearVelocity(new Vector3f(rt.velocity.x, rt.velocity.y, rt.velocity.z));
            body.setRestitution(0.0f);
            body.setDamping(LINEAR_DAMPING, ANGULAR_DAMPING);
            body.setSleepingThresholds(0.1f, 0.1f);
            body.setCcdMotionThreshold(0.01f);
            body.setCcdSweptSphereRadius(ccdRadii[i]);
            world.addRigidBody(body);
            ragdollParts.add(body);
        }

        createRagdollJoints();
        updateLocalWorldCollisionFromTorso();
        resetEntityTorsoSyncThrottle();
    }

    // ======================== JOINTS ========================

    private void createRagdollJoints() {
        if (ragdollParts.size() < 6)
            return;

        RigidBody torso = ragdollParts.get(RagdollPart.TORSO.index);
        RigidBody head = ragdollParts.get(RagdollPart.HEAD.index);
        RigidBody lLeg = ragdollParts.get(RagdollPart.LEFT_LEG.index);
        RigidBody rLeg = ragdollParts.get(RagdollPart.RIGHT_LEG.index);
        RigidBody lArm = ragdollParts.get(RagdollPart.LEFT_ARM.index);
        RigidBody rArm = ragdollParts.get(RagdollPart.RIGHT_ARM.index);

        Transform tTorso = new Transform();
        torso.getMotionState().getWorldTransform(tTorso);
        Transform tHead = new Transform();
        head.getMotionState().getWorldTransform(tHead);
        Transform tLLeg = new Transform();
        lLeg.getMotionState().getWorldTransform(tLLeg);
        Transform tRLeg = new Transform();
        rLeg.getMotionState().getWorldTransform(tRLeg);
        Transform tLArm = new Transform();
        lArm.getMotionState().getWorldTransform(tLArm);
        Transform tRArm = new Transform();
        rArm.getMotionState().getWorldTransform(tRArm);

        Function<Vector3f, Vector3f> torsoLocalToWorld = (local) -> {
            Quat4f trot = tTorso.getRotation(new Quat4f());
            Vector3f out = rotateVecByQuat(trot, local);
            out.add(tTorso.origin);
            return out;
        };

        // --- Head <-> Torso ---
        Vector3f torsoTopWorld = torsoLocalToWorld.apply(new Vector3f(0f, 0.4f, 0f));
        Quat4f hrot = tHead.getRotation(new Quat4f());
        Vector3f headBottomWorld = rotateVecByQuat(hrot, new Vector3f(0f, -0.2f, 0f));
        headBottomWorld.add(tHead.origin);
        Vector3f headAnchor = midpoint(torsoTopWorld, headBottomWorld);
        ragdollJoints.add(createJointAtWorldAnchor(torso, head, headAnchor,
                new Vector3f(0, 0, 0), new Vector3f(0, 0, 0),
                new Vector3f(rad(-30), rad(-20), rad(-30)),
                new Vector3f(rad(30), rad(50), rad(30))));

        // --- Left Leg <-> Torso ---
        Vector3f torsoLeftHip = torsoLocalToWorld.apply(new Vector3f(-0.1f, -0.55f, 0f));
        Quat4f lrot = tLLeg.getRotation(new Quat4f());
        Vector3f legTopWorld = rotateVecByQuat(lrot, new Vector3f(0f, 0.45f, 0f));
        legTopWorld.add(tLLeg.origin);
        ragdollJoints.add(createJointAtWorldAnchor(torso, lLeg, midpoint(torsoLeftHip, legTopWorld),
                new Vector3f(-0.05f, -0.05f, -0.05f), new Vector3f(0.05f, 0.05f, 0.05f),
                new Vector3f(rad(-40), 0f, rad(-10)),
                new Vector3f(rad(80), 0f, rad(10))));

        // --- Right Leg <-> Torso ---
        Vector3f torsoRightHip = torsoLocalToWorld.apply(new Vector3f(0.1f, -0.55f, 0f));
        Quat4f rrot = tRLeg.getRotation(new Quat4f());
        Vector3f rLegTopWorld = rotateVecByQuat(rrot, new Vector3f(0f, 0.45f, 0f));
        rLegTopWorld.add(tRLeg.origin);
        ragdollJoints.add(createJointAtWorldAnchor(torso, rLeg, midpoint(torsoRightHip, rLegTopWorld),
                new Vector3f(-0.05f, -0.05f, -0.05f), new Vector3f(0.05f, 0.05f, 0.05f),
                new Vector3f(rad(-40), 0f, rad(-10)),
                new Vector3f(rad(80), 0f, rad(10))));

        // --- Left Arm <-> Torso ---
        Vector3f torsoLeftShoulder = torsoLocalToWorld.apply(new Vector3f(-0.35f, 0.05f, 0f));
        Quat4f larot = tLArm.getRotation(new Quat4f());
        Vector3f lArmTopWorld = rotateVecByQuat(larot, new Vector3f(0f, 0.35f, 0f));
        lArmTopWorld.add(tLArm.origin);
        ragdollJoints.add(createJointAtWorldAnchor(torso, lArm, midpoint(torsoLeftShoulder, lArmTopWorld),
                new Vector3f(-0.02f, -0.02f, -0.02f), new Vector3f(0.02f, 0.02f, 0.02f),
                new Vector3f(rad(-80), rad(-30), rad(-40)),
                new Vector3f(rad(80), rad(30), rad(40))));

        // --- Right Arm <-> Torso ---
        Vector3f torsoRightShoulder = torsoLocalToWorld.apply(new Vector3f(0.35f, 0.05f, 0f));
        Quat4f rarot = tRArm.getRotation(new Quat4f());
        Vector3f rArmTopWorld = rotateVecByQuat(rarot, new Vector3f(0f, 0.35f, 0f));
        rArmTopWorld.add(tRArm.origin);
        ragdollJoints.add(createJointAtWorldAnchor(torso, rArm, midpoint(torsoRightShoulder, rArmTopWorld),
                new Vector3f(-0.02f, -0.02f, -0.02f), new Vector3f(0.02f, 0.02f, 0.02f),
                new Vector3f(rad(-80), rad(-30), rad(-40)),
                new Vector3f(rad(80), rad(30), rad(40))));
    }

    // ======================== FORCES / IMPULSES ========================

    /**
     * Apply an explosion impulse to all ragdoll parts.
     * Lighter parts receive proportionally more impulse (scaled by inverse mass).
     */
    public void applyExplosionImpulse(Vec3 explosionPos, float strength) {
        double scale = Config.RAGDOLL_EXPLOSION_IMPULSE_SCALE.get();
        float cap = (float) Config.RAGDOLL_EXPLOSION_MAX_IMPULSE.get().doubleValue();
        for (int i = 0; i < ragdollParts.size(); i++) {
            RigidBody body = ragdollParts.get(i);
            Transform t = new Transform();
            body.getMotionState().getWorldTransform(t);
            Vector3f dir = new Vector3f(
                    t.origin.x - (float) explosionPos.x,
                    t.origin.y - (float) explosionPos.y,
                    t.origin.z - (float) explosionPos.z);
            float dist = dir.length() + 0.001f;
            dir.normalize();

            float falloff = Mth.clamp(1f - (dist / 6f), 0f, 1f);
            float massScale = MASS_TORSO / PART_MASSES[i];
            float impulseMag = (float) (strength * falloff * 7.5 * massScale * scale);
            if (impulseMag > cap)
                impulseMag = cap;
            Vector3f impulse = new Vector3f(dir);
            impulse.scale(impulseMag);
            body.activate(true);
            body.applyCentralImpulse(impulse);
        }
    }

    /**
     * Apply a directional knockback impulse (melee/sword/generic).
     * Lighter parts receive more impulse so arms fly further than the torso.
     */
    public void applyKnockbackImpulse(Vec3 direction, float strength) {
        float capped = Math.min(strength, (float) Config.RAGDOLL_KNOCKBACK_STRENGTH_CAP.get().doubleValue());
        for (int i = 0; i < ragdollParts.size(); i++) {
            RigidBody body = ragdollParts.get(i);
            float massScale = MASS_TORSO / PART_MASSES[i];
            float s = capped * massScale;
            Vector3f impulse = new Vector3f(
                    (float) direction.x * s,
                    (float) direction.y * s,
                    (float) direction.z * s);
            body.activate(true);
            body.applyCentralImpulse(impulse);
        }
    }
    /**
     * Apply a point impulse from a projectile impact to the nearest body part.
     *
     * @param hitPos   world-space hit position (from EntityHitResult)
     * @param impulse  momentum vector p = mass × velocity (in physics units)
     */
    public void applyProjectileImpact(Vec3 hitPos, Vec3 impulse) {
        if (ragdollParts.isEmpty()) return;

        // Find closest body part to the impact position
        int closestIdx = 0;
        float minDist  = Float.MAX_VALUE;
        for (int i = 0; i < ragdollParts.size(); i++) {
            Transform t = new Transform();
            ragdollParts.get(i).getMotionState().getWorldTransform(t);
            float dx = t.origin.x - (float) hitPos.x;
            float dy = t.origin.y - (float) hitPos.y;
            float dz = t.origin.z - (float) hitPos.z;
            float d  = dx * dx + dy * dy + dz * dz;
            if (d < minDist) { minDist = d; closestIdx = i; }
        }

        RigidBody target = ragdollParts.get(closestIdx);
        Vector3f imp = new Vector3f((float) impulse.x, (float) impulse.y, (float) impulse.z);

        // Scale impulse by inverse mass so lighter parts react more violently.
        float massScale = MASS_TORSO / PART_MASSES[closestIdx];
        imp.scale(massScale);

        target.activate(true);
        // Point-of-impact torque: offset of hit relative to body centre → spin
        Transform bodyT = new Transform();
        target.getMotionState().getWorldTransform(bodyT);
        Vector3f rel = new Vector3f(
                (float) hitPos.x - bodyT.origin.x,
                (float) hitPos.y - bodyT.origin.y,
                (float) hitPos.z - bodyT.origin.z);
        target.applyImpulse(imp, rel);

        // Spread ~30 % to neighboring parts for a more natural chain reaction
        float spillFrac = 0.3f;
        for (int i = 0; i < ragdollParts.size(); i++) {
            if (i == closestIdx) continue;
            float neighborScale = (MASS_TORSO / PART_MASSES[i]) * spillFrac;
            Vector3f spill = new Vector3f((float) impulse.x, (float) impulse.y, (float) impulse.z);
            spill.scale(neighborScale);
            ragdollParts.get(i).activate(true);
            ragdollParts.get(i).applyCentralImpulse(spill);
        }
    }

    /** Apply random velocity and torque to all parts (for death, etc.) */
    public void applyRandomVelocity(float maxX, float maxY, float maxZ) {
        java.util.Random rand = new java.util.Random();
        for (RigidBody body : ragdollParts) {
            Vector3f impulse = new Vector3f(
                    (rand.nextFloat() - 0.5f) * maxX,
                    (rand.nextFloat() * 5f) + maxY,
                    (rand.nextFloat() - 0.5f) * maxZ);
            body.applyCentralImpulse(impulse);

            Vector3f torque = new Vector3f(
                    (rand.nextFloat() - 0.5f) * 10f,
                    (rand.nextFloat() - 0.5f) * 10f,
                    (rand.nextFloat() - 0.5f) * 10f);
            body.applyTorqueImpulse(torque);
        }
    }

    /** Apply impulse to a specific body part */
    public void applyImpulseToPart(RagdollPart part, Vec3 vel) {
        if (part.index < ragdollParts.size()) {
            Vector3f impulse = new Vector3f((float) vel.x, (float) vel.y, (float) vel.z);
            ragdollParts.get(part.index).applyCentralImpulse(impulse);
        }
    }

    private float getDeathCorpseMaxSpeed() {
        return (float) Config.RAGDOLL_DEATH_CORPSE_MAX_LINEAR_SPEED.get().doubleValue();
    }

    private void clampAllLinearVelocities(float maxSpeed) {
        if (ragdollParts.isEmpty())
            return;
        for (RigidBody r : ragdollParts) {
            Vector3f vel = new Vector3f();
            r.getLinearVelocity(vel);
            float speed = vel.length();
            if (speed > maxSpeed && speed > 1e-4f)
                vel.scale(maxSpeed / speed);
            r.setLinearVelocity(vel);
        }
    }

    // ======================== FLUID FORCES ========================

    private void applyFluidForces() {
        Level level = physicsWorld.getLevel();
        BlockPos checkPos;

        if (mode == Mode.DEATH_RAGDOLL || orphaned) {
            // Death ragdoll: check fluid at torso position
            if (ragdollParts.isEmpty())
                return;
            Transform t = new Transform();
            ragdollParts.get(0).getMotionState().getWorldTransform(t);
            checkPos = BlockPos.containing(t.origin.x, t.origin.y, t.origin.z);
            if (!level.getFluidState(checkPos).isSource())
                return;
        } else {
            ServerPlayer player = getPlayerSafe();
            if (player == null || !player.isInWater())
                return;
            checkPos = player.blockPosition();
        }

        Vec3 flow = level.getFluidState(checkPos).getFlow(level, checkPos);
        Vector3f waterFlow = new Vector3f((float) flow.x, (float) flow.y, (float) flow.z);
        waterFlow.scale(5f);

        for (RigidBody body : ragdollParts) {
            Vector3f vel = new Vector3f();
            body.getLinearVelocity(vel);
            Vector3f drag = new Vector3f(vel);
            drag.scale(-2.7f);
            Vector3f net = new Vector3f(waterFlow);
            net.add(drag);
            body.applyCentralImpulse(net);
        }
    }

    // ======================== WORLD COLLISION ========================

    public void updateLocalWorldCollision() {
        if (ragdollParts.isEmpty())
            return;
        rebuildLocalStaticBlockCollision(physicsWorld.getLevel(), torsoBlockCenter());
    }

    /** Block centre enclosing the torso rigid body — consistent for live ragdoll + death corpse. */
    private BlockPos torsoBlockCenter() {
        Transform torsoT = new Transform();
        ragdollParts.get(0).getMotionState().getWorldTransform(torsoT);
        return BlockPos.containing(torsoT.origin.x, torsoT.origin.y, torsoT.origin.z);
    }

    private void updateLocalWorldCollisionFromTorso() {
        if (ragdollParts.isEmpty())
            return;
        rebuildLocalStaticBlockCollision(physicsWorld.getLevel(), torsoBlockCenter());
    }

    /** Re-scan a 7×7×7 box of block AABBs into Bullet static Rigids if throttle allows. */
    private void rebuildLocalStaticBlockCollision(Level level, BlockPos center) {
        long gameTick = physicsWorld.getLevel().getGameTime();

        int mdist =
                Math.abs(center.getX() - lastCollisionCenter.getX())
                + Math.abs(center.getY() - lastCollisionCenter.getY())
                + Math.abs(center.getZ() - lastCollisionCenter.getZ());

        boolean neverBuiltYet = lastCollisionRebuildGameTick < 0;
        boolean movedFar = mdist >= COLLISION_CENTER_MANHATTAN_THRESHOLD;

        // Small movements (< threshold): throttle full rebuild frequency; drift ≥ 2 Manhattan
        // triggers a refresh once the cooldown has elapsed — catches slow sliding ragdolls.
        if (!neverBuiltYet && !movedFar) {
            long elapsed = gameTick - lastCollisionRebuildGameTick;
            if (elapsed < COLLISION_REBUILD_MIN_INTERVAL_TICKS || mdist < 2)
                return;
        }

        lastCollisionCenter      = center;
        lastCollisionRebuildGameTick = gameTick;

        for (CollisionObject c : localStaticCollision)
            world.removeCollisionObject(c);
        localStaticCollision.clear();

        int radius = COLLISION_CUBE_HALF_EXTENT_BLOCKS;
        for (int dx = -radius; dx <= radius; dx++)
            for (int dy = -radius; dy <= radius; dy++)
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos bpos = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(bpos);
                    if (state.isAir() || state.getFluidState().isSource())
                        continue;
                    if (isCompletelySurrounded(bpos, level))
                        continue;

                    VoxelShape shape = state.getCollisionShape(level, bpos);
                    if (shape.isEmpty())
                        continue;

                    for (AABB box : shape.toAabbs()) {
                        Vector3f halfExtents = new Vector3f(
                                (float) (box.getXsize() / 2),
                                (float) (box.getYsize() / 2),
                                (float) (box.getZsize() / 2));
                        CollisionShape cs = new BoxShape(halfExtents);

                        Transform t = new Transform();
                        t.setIdentity();
                        t.origin.set(
                                (float) (bpos.getX() + box.minX + box.getXsize() / 2),
                                (float) (bpos.getY() + box.minY + box.getYsize() / 2),
                                (float) (bpos.getZ() + box.minZ + box.getZsize() / 2));

                        RigidBody rb = new RigidBody(
                                new RigidBodyConstructionInfo(0f, new DefaultMotionState(t), cs, new Vector3f()));
                        rb.setCollisionFlags(rb.getCollisionFlags() | CollisionFlags.STATIC_OBJECT);
                        world.addRigidBody(rb);
                        localStaticCollision.add(rb);
                    }
                }
    }

    private boolean isCompletelySurrounded(BlockPos pos, Level level) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighbor);
            if (neighborState.isAir() ||
                    neighborState.getFluidState().isSource() ||
                    neighborState.getCollisionShape(level, neighbor).isEmpty() ||
                    neighborState.canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Correct deep penetrations involving <strong>only this ragdoll's</strong> rigid bodies.
     * The shared Bullet dispatcher retains manifolds for every simulated object in this
     * dimension {@linkplain PhysicsWorld} — iterating all contacts was O(world contacts)
     * and could move unrelated bodies.
     *
     * <p>Instrumentation / profiling hotspots (Spark, JFR, async profiler): correlate lock time
     * with stack samples for {@link DiscreteDynamicsWorld#stepSimulation(float, int, float)},
     * {@link CollisionDispatcher#getInternalManifoldPointer()}, calls into
     * {@link #rebuildLocalStaticBlockCollision} and repeated {@link ServerPlayer#teleportTo}.
     */
    private void correctInterpenetrations() {
        Level lvl = physicsWorld.getLevel();

        /* Shared Bullet dispatcher holds manifolds for <i>every</i> ragdoll + stray body in the
           dimension — only touch contacts involving OUR parts. Alternate ticks globally. */
        if ((lvl.getGameTime() & 1L) != 0L) return;

        int fixesApplied = 0;

        manifoldLoop:
        for (PersistentManifold manifold : physicsWorld.getDispatcher().getInternalManifoldPointer()) {
            if (manifold == null)
                continue;

            CollisionObject co0 = (CollisionObject) manifold.getBody0();
            CollisionObject co1 = (CollisionObject) manifold.getBody1();
            boolean touchesThis = ragdollParts.contains(co0) || ragdollParts.contains(co1);
            if (!touchesThis) continue;

            int numContacts = manifold.getNumContacts();
            for (int i = 0; i < numContacts; i++) {
                ManifoldPoint point = manifold.getContactPoint(i);
                if (point.getDistance() < -0.1f) {
                    RigidBody a = (RigidBody) manifold.getBody0();
                    RigidBody b = (RigidBody) manifold.getBody1();

                    // Soft correction: limit max push-out to 0.3 blocks per step
                    float correction = Math.min(0.3f, 2.0f * -point.getDistance());
                    Vector3f normal = new Vector3f(point.normalWorldOnB);
                    normal.scale(correction);
                    if (a.getInvMass() > 0) a.translate(normal);
                    normal.scale(-1);
                    if (b.getInvMass() > 0) b.translate(normal);

                    if (++fixesApplied >= INTERPENETRATION_MAX_FIXES_PER_RAGDOLL_PER_TICK)
                        break manifoldLoop;
                }
            }
        }
    }

    // ======================== TRANSFORM READING ========================

    public RagdollTransform[] getRagdollTransforms() {
        RagdollTransform[] out = new RagdollTransform[ragdollParts.size()];
        for (int i = 0; i < ragdollParts.size(); i++) {
            Transform t = new Transform();
            ragdollParts.get(i).getMotionState().getWorldTransform(t);
            Quat4f rot = t.getRotation(new Quat4f());
            Vector3f vel = new Vector3f();
            ragdollParts.get(i).getLinearVelocity(vel);
            out[i] = new RagdollTransform(i, t.origin.x, t.origin.y, t.origin.z,
                    rot.x, rot.y, rot.z, rot.w, vel.x, vel.y, vel.z);
        }
        return out;
    }

    /** Get world-space torso position, or null if no ragdoll bodies. */
    public Vec3 getTorsoPosition() {
        if (ragdollParts.isEmpty())
            return null;
        Transform t = new Transform();
        ragdollParts.get(0).getMotionState().getWorldTransform(t);
        return new Vec3(t.origin.x, t.origin.y, t.origin.z);
    }

    /** Check if this ragdoll has physics bodies currently simulating. */
    public boolean hasBodies() {
        return !ragdollParts.isEmpty();
    }

    // ======================== UTILITY ========================

    public boolean hasBody(CollisionObject obj) {
        return ragdollParts.contains(obj);
    }

    public ServerPlayer getPlayer() {
        return getPlayerSafe();
    }

    public RagdollPart identifyPart(RigidBody rb) {
        int idx = ragdollParts.indexOf(rb);
        return RagdollPart.byIndex(idx);
    }

    /**
     * Check the projectile-impact cache for this player and apply the stored impact
     * to the freshly-created ragdoll bodies. Called immediately after
     * {@link #createRagdollBodies()}.
     */
    private void applyPendingProjectileImpact(ServerPlayer player) {
        DamageEventHandler.PendingImpact impact = DamageEventHandler.pollImpact(player.getUUID());
        if (impact != null) {
            applyProjectileImpact(impact.hitPos(), impact.impulse());
        }
    }

    /**
     * Full cleanup — removes physics bodies AND sends RagdollEndPayload if ragdoll
     * was active.
     * Call this when removing the ragdoll entirely (player disconnect, dimension
     * change, etc.)
     */
    public void destroy() {
        // If ragdoll was active (any mode), notify all clients to remove it
        if (mode == Mode.RAGDOLL || mode == Mode.DEATH_RAGDOLL) {
            try {
                RagdollEndPayload payload = new RagdollEndPayload(networkRagdollId);
                PacketDistributor.sendToAllPlayers(payload);
            } catch (Exception ignored) {
                // May fail during server shutdown — safe to ignore
            }

            // Restore player state (unfreeze) — only for live ragdolls
            if (mode == Mode.RAGDOLL) {
                ServerPlayer player = getPlayerSafe();
                if (player != null) {
                    player.setNoGravity(false);
                    player.setDeltaMovement(Vec3.ZERO);
                }
            }
        }

        cleanupBodiesOnly();
        mode = Mode.NORMAL;
    }

    /** Remove physics bodies without sending network packets. Internal use only. */
    private void cleanupBodiesOnly() {
        for (TypedConstraint c : ragdollJoints)
            world.removeConstraint(c);
        for (RigidBody r : ragdollParts)
            world.removeRigidBody(r);
        for (CollisionObject co : localStaticCollision)
            world.removeCollisionObject(co);
        ragdollJoints.clear();
        ragdollParts.clear();
        localStaticCollision.clear();
        lastCollisionCenter           = BlockPos.ZERO;
        lastCollisionRebuildGameTick  = -1;
        corpseIntegrity               = -1f;
        corpseBloodDripCounter        = 0;
        resetEntityTorsoSyncThrottle();
    }

    /** Half-size of collision cube (in blocks); total volume spans {@code [-n..+n]^3 inclusive}. */
    private static final int COLLISION_CUBE_HALF_EXTENT_BLOCKS = 3;

    /** Manhattan torso movement that forces an immediate static-mesh rebuild. */
    private static final int COLLISION_CENTER_MANHATTAN_THRESHOLD = 5;

    /**
     * Minimum ticks between collisions mesh rebuild when the torso has not crossed
     * {@linkplain #COLLISION_CENTER_MANHATTAN_THRESHOLD}.
     */
    private static final long COLLISION_REBUILD_MIN_INTERVAL_TICKS = 6;

    /**
     * Maximum contact corrections per ragdoll per tick (dispatcher stores manifolds for the
     * whole dimension-level physics world).
     */
    private static final int INTERPENETRATION_MAX_FIXES_PER_RAGDOLL_PER_TICK = 40;

    /** Squared distance so micro physics jitter skips {@link ServerPlayer#teleportTo}. */
    private static final double TORSO_ENTITY_TELEPORT_EPSILON_SQ = 0.065 * 0.065;

    /** Periodic resync even below epsilon so eventual drift / anti-cheat state stays sane. */
    private static final int TORSO_ENTITY_RESYNC_TICKS = 5;

    // ======================== BODY MASS / DAMPING CONSTANTS ========================
    // Realistic relative masses (sum ≈ 32 kg-equivalent).
    // Torso is the heaviest single segment; arms are lightest (fly furthest on hit).
    private static final float MASS_TORSO = 16f;
    private static final float MASS_HEAD  =  3f;
    private static final float MASS_LEG   =  5.5f;
    private static final float MASS_ARM   =  2.5f;
    /** Per-part mass array indexed by RagdollPart.index. */
    static final float[] PART_MASSES = { MASS_TORSO, MASS_HEAD, MASS_LEG, MASS_LEG, MASS_ARM, MASS_ARM };

    // Lower linear damping → more realistic airborne momentum.
    // Higher angular damping → slower spin to avoid dizzying tumbles.
    private static final float LINEAR_DAMPING  = 0.05f;
    private static final float ANGULAR_DAMPING = 0.80f;

    private RigidBodyConstructionInfo makeInfo(float mass, Transform startTransform, CollisionShape shape) {
        Vector3f inertia = new Vector3f();
        if (mass > 0f) {
            shape.calculateLocalInertia(mass, inertia);
        }
        DefaultMotionState motionState = new DefaultMotionState(startTransform);
        RigidBodyConstructionInfo info = new RigidBodyConstructionInfo(mass, motionState, shape, inertia);
        info.linearDamping  = LINEAR_DAMPING;
        info.angularDamping = ANGULAR_DAMPING;
        info.restitution    = 0.0f;
        info.friction       = 1.2f;
        info.additionalDamping = true;
        return info;
    }

    private Generic6DofConstraint createJointAtWorldAnchor(RigidBody a, RigidBody b, Vector3f worldAnchor,
            Vector3f linearLower, Vector3f linearUpper,
            Vector3f angularLower, Vector3f angularUpper) {
        Transform ta = new Transform();
        a.getMotionState().getWorldTransform(ta);
        Transform tb = new Transform();
        b.getMotionState().getWorldTransform(tb);

        Vector3f localA_origin = worldPointToLocal(ta, worldAnchor);
        Vector3f localB_origin = worldPointToLocal(tb, worldAnchor);

        Transform localA = new Transform();
        localA.setIdentity();
        localA.origin.set(localA_origin);
        Transform localB = new Transform();
        localB.setIdentity();
        localB.origin.set(localB_origin);

        Generic6DofConstraint joint = new Generic6DofConstraint(a, b, localA, localB, true);
        joint.setLinearLowerLimit(linearLower);
        joint.setLinearUpperLimit(linearUpper);
        joint.setAngularLowerLimit(angularLower);
        joint.setAngularUpperLimit(angularUpper);

        a.activate();
        b.activate();
        world.addConstraint(joint, true);
        return joint;
    }

    // --- Math helpers ---

    private static float rad(float degrees) {
        return (float) Math.toRadians(degrees);
    }

    private static Vector3f midpoint(Vector3f a, Vector3f b) {
        return new Vector3f((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f, (a.z + b.z) * 0.5f);
    }

    private Vector3f rotateVecByQuat(Quat4f q, Vector3f v) {
        Vector3f qvec = new Vector3f(q.x, q.y, q.z);
        Vector3f t = new Vector3f();
        t.x = 2f * (qvec.y * v.z - qvec.z * v.y);
        t.y = 2f * (qvec.z * v.x - qvec.x * v.z);
        t.z = 2f * (qvec.x * v.y - qvec.y * v.x);

        Vector3f result = new Vector3f(v);
        Vector3f qwt = new Vector3f(t);
        qwt.scale(q.w);
        result.add(qwt);

        Vector3f cross = new Vector3f();
        cross.x = qvec.y * t.z - qvec.z * t.y;
        cross.y = qvec.z * t.x - qvec.x * t.z;
        cross.z = qvec.x * t.y - qvec.y * t.x;
        result.add(cross);
        return result;
    }

    private Vector3f worldPointToLocal(Transform bodyWorldTransform, Vector3f worldPoint) {
        Vector3f delta = new Vector3f(worldPoint);
        delta.sub(bodyWorldTransform.origin);
        Quat4f rot = bodyWorldTransform.getRotation(new Quat4f());
        Quat4f qc = new Quat4f(-rot.x, -rot.y, -rot.z, rot.w);
        return rotateVecByQuat(qc, delta);
    }
}
