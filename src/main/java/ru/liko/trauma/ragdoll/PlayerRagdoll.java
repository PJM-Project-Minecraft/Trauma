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

import ru.liko.trauma.Config;

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

    private int networkRagdollId;

    // Death ragdoll state
    private int deathTicksRemaining = -1;
    private boolean orphaned = false; // true when player logged out/respawned but ragdoll persists

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
            int deathTicksRemaining, RagdollTransform[] transforms) {
        PlayerRagdoll ragdoll = new PlayerRagdoll(physicsWorld, uuid, networkRagdollId);
        ragdoll.mode = Mode.DEATH_RAGDOLL;
        ragdoll.orphaned = true;
        ragdoll.deathTicksRemaining = deathTicksRemaining;
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

        if (ragdollParts.isEmpty()) {
            // Not already ragdolled — create fresh ragdoll bodies
            ServerPlayer player = getPlayerSafe();
            if (player == null)
                return;

            createRagdollBodies();

            // Send start packet to all players tracking this entity (and self) so others see the death ragdoll
            RagdollStartPayload payload = new RagdollStartPayload(player.getId(), networkRagdollId, playerUUID,
                    getRagdollTransforms());
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, payload);
        }
        // If bodies already exist (was in RAGDOLL mode), just continue — physics keeps
        // running
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
            // Player left — clean up ragdoll silently
            cleanupBodiesOnly();
            mode = Mode.NORMAL;
            return;
        }

        // Read ragdoll transforms and send to tracking clients
        RagdollTransform[] transforms = getRagdollTransforms();
        RagdollUpdatePayload payload = new RagdollUpdatePayload(networkRagdollId, System.currentTimeMillis(),
                transforms);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, payload);

        // Teleport player to torso position
        RigidBody torsoBody = ragdollParts.get(0);
        Transform torsoTransform = new Transform();
        torsoBody.getMotionState().getWorldTransform(torsoTransform);
        Vector3f pos = torsoTransform.origin;
        player.teleportTo(pos.x, pos.y, pos.z);

        // Continuously freeze player during ragdoll — prevents any movement fighting
        freezePlayer(player);

        // Clamp velocities to prevent physics explosion
        for (RigidBody r : ragdollParts) {
            Vector3f vel = new Vector3f();
            r.getLinearVelocity(vel);
            if (vel.y < -80f)
                vel.y = -80f;
            float speed = vel.length();
            if (speed > 90f) {
                vel.scale(90f / speed);
            }
            r.setLinearVelocity(vel);
        }

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
            // Time's up — remove the death ragdoll
            cleanupBodiesAndNotify();
            mode = Mode.NORMAL;
            return;
        }

        // Send updates to all players in the dimension
        RagdollTransform[] transforms = getRagdollTransforms();
        RagdollUpdatePayload payload = new RagdollUpdatePayload(networkRagdollId, System.currentTimeMillis(),
                transforms);
        ServerLevel level = physicsWorld.getLevel();
        for (ServerPlayer sp : level.players()) {
            PacketDistributor.sendToPlayer(sp, payload);
        }

        // Clamp velocities
        for (RigidBody r : ragdollParts) {
            Vector3f vel = new Vector3f();
            r.getLinearVelocity(vel);
            if (vel.y < -80f)
                vel.y = -80f;
            float speed = vel.length();
            if (speed > 90f) {
                vel.scale(90f / speed);
            }
            r.setLinearVelocity(vel);
        }

        applyFluidForces();
        updateLocalWorldCollisionFromTorso();
        correctInterpenetrations();
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

        // --- Torso ---
        CollisionShape torsoShape = new BoxShape(new Vector3f(0.25f, 0.4f, 0.15f));
        Transform torsoT = new Transform();
        torsoT.setIdentity();
        torsoT.setRotation(qq);
        torsoT.origin.set(torsoOrigin);
        RigidBody torsoBody = new RigidBody(makeInfo(8, torsoT, torsoShape));
        torsoBody.setLinearVelocity(motion);
        torsoBody.setRestitution(0.0f);
        torsoBody.setDamping(0.15f, 0.90f);
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
        RigidBody headBody = new RigidBody(makeInfo(4, headT, headShape));
        headBody.setLinearVelocity(motion);
        headBody.setCcdMotionThreshold(0.01f);
        headBody.setDamping(0.15f, 0.90f);
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
        RigidBody lLegBody = new RigidBody(makeInfo(6, lLegT, legShape));
        lLegBody.setLinearVelocity(motion);
        lLegBody.setCcdMotionThreshold(0.01f);
        lLegBody.setDamping(0.15f, 0.90f);
        lLegBody.setCcdSweptSphereRadius(0.35f);
        world.addRigidBody(lLegBody);
        ragdollParts.add(lLegBody);

        // --- Right Leg ---
        Vector3f rLegPos = worldOffset.apply(new Vector3f(0.1f, -0.75f, 0f));
        Transform rLegT = new Transform();
        rLegT.setIdentity();
        rLegT.origin.set(rLegPos);
        rLegT.setRotation(qq);
        RigidBody rLegBody = new RigidBody(makeInfo(6, rLegT, legShape));
        rLegBody.setLinearVelocity(motion);
        rLegBody.setCcdMotionThreshold(0.01f);
        rLegBody.setDamping(0.15f, 0.90f);
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
        RigidBody lArmBody = new RigidBody(makeInfo(4, lArmT, armShape));
        lArmBody.setLinearVelocity(motion);
        lArmBody.setCcdMotionThreshold(0.01f);
        lArmBody.setDamping(0.15f, 0.90f);
        lArmBody.setCcdSweptSphereRadius(0.3f);
        world.addRigidBody(lArmBody);
        ragdollParts.add(lArmBody);

        // --- Right Arm ---
        Vector3f rArmPos = worldOffset.apply(new Vector3f(0.4f, 0.05f, 0f));
        Transform rArmT = new Transform();
        rArmT.setIdentity();
        rArmT.origin.set(rArmPos);
        rArmT.setRotation(qq);
        RigidBody rArmBody = new RigidBody(makeInfo(4, rArmT, armShape));
        rArmBody.setLinearVelocity(motion);
        rArmBody.setCcdMotionThreshold(0.01f);
        rArmBody.setDamping(0.15f, 0.90f);
        rArmBody.setCcdSweptSphereRadius(0.3f);
        world.addRigidBody(rArmBody);
        ragdollParts.add(rArmBody);

        createRagdollJoints();
        updateLocalWorldCollision();
    }

    /**
     * Create ragdoll bodies from saved transform data (for restoring death ragdolls
     * on world load).
     * Creates 6 bodies at the specified positions/rotations with saved velocities.
     */
    private void createRagdollBodiesFromTransforms(RagdollTransform[] transforms) {
        if (transforms == null || transforms.length < 6)
            return;

        // Part shapes and masses match createRagdollBodies() exactly
        float[] masses = { 8f, 4f, 6f, 6f, 4f, 4f };
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
            body.setDamping(0.15f, 0.90f);
            body.setSleepingThresholds(0.1f, 0.1f);
            body.setCcdMotionThreshold(0.01f);
            body.setCcdSweptSphereRadius(ccdRadii[i]);
            world.addRigidBody(body);
            ragdollParts.add(body);
        }

        createRagdollJoints();
        updateLocalWorldCollisionFromTorso();
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

    /** Apply an explosion impulse to all ragdoll parts */
    public void applyExplosionImpulse(Vec3 explosionPos, float strength) {
        for (RigidBody body : ragdollParts) {
            Transform t = new Transform();
            body.getMotionState().getWorldTransform(t);
            Vector3f dir = new Vector3f(
                    t.origin.x - (float) explosionPos.x,
                    t.origin.y - (float) explosionPos.y,
                    t.origin.z - (float) explosionPos.z);
            float dist = dir.length() + 0.001f;
            dir.normalize();

            float falloff = Mth.clamp(1f - (dist / 6f), 0f, 1f);
            float impulseMag = strength * falloff * 20f;
            Vector3f impulse = new Vector3f(dir);
            impulse.scale(impulseMag);
            body.applyCentralImpulse(impulse);
        }
    }

    /** Apply a directional impulse to all parts (e.g. from damage knockback) */
    public void applyKnockbackImpulse(Vec3 direction, float strength) {
        Vector3f impulse = new Vector3f((float) direction.x * strength, (float) direction.y * strength,
                (float) direction.z * strength);
        for (RigidBody body : ragdollParts) {
            body.activate(true);
            body.applyCentralImpulse(impulse);
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
        ServerPlayer player = getPlayerSafe();
        if (player == null)
            return;

        BlockPos center = player.getOnPos();
        // Only rebuild if player moved significantly (performance optimization)
        if (center.distManhattan(lastCollisionCenter) < 2)
            return;
        lastCollisionCenter = center;

        // Clean up previous
        for (CollisionObject c : localStaticCollision)
            world.removeCollisionObject(c);
        localStaticCollision.clear();

        int radius = 3;
        for (int dx = -radius; dx <= radius; dx++)
            for (int dy = -radius; dy <= radius; dy++)
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos bpos = center.offset(dx, dy, dz);
                    BlockState state = player.level().getBlockState(bpos);
                    if (state.isAir() || state.getFluidState().isSource())
                        continue;
                    if (isCompletelySurrounded(bpos, player.level()))
                        continue;

                    VoxelShape shape = state.getCollisionShape(player.level(), bpos);
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
     * World collision update for death ragdolls — uses torso position instead of
     * player.
     */
    private void updateLocalWorldCollisionFromTorso() {
        if (ragdollParts.isEmpty())
            return;
        Level level = physicsWorld.getLevel();

        Transform torsoT = new Transform();
        ragdollParts.get(0).getMotionState().getWorldTransform(torsoT);
        BlockPos center = BlockPos.containing(torsoT.origin.x, torsoT.origin.y, torsoT.origin.z);

        if (center.distManhattan(lastCollisionCenter) < 2)
            return;
        lastCollisionCenter = center;

        for (CollisionObject c : localStaticCollision)
            world.removeCollisionObject(c);
        localStaticCollision.clear();

        int radius = 3;
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

    // ======================== INTERPENETRATION CORRECTION ========================

    private void correctInterpenetrations() {
        for (PersistentManifold manifold : physicsWorld.getDispatcher().getInternalManifoldPointer()) {
            if (manifold == null)
                continue;
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
                    if (a.getInvMass() > 0)
                        a.translate(normal);
                    normal.scale(-1);
                    if (b.getInvMass() > 0)
                        b.translate(normal);
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
    }

    private RigidBodyConstructionInfo makeInfo(float mass, Transform startTransform, CollisionShape shape) {
        Vector3f inertia = new Vector3f();
        if (mass > 0f) {
            shape.calculateLocalInertia(mass, inertia);
        }
        DefaultMotionState motionState = new DefaultMotionState(startTransform);
        RigidBodyConstructionInfo info = new RigidBodyConstructionInfo(mass, motionState, shape, inertia);
        info.linearDamping = 0.15f;
        info.angularDamping = 0.90f;
        info.restitution = 0.0f;
        info.friction = 1.2f;
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
