package ru.liko.trauma.common.entity;

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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
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
import ru.liko.trauma.ragdoll.PhysicsWorld;
import ru.liko.trauma.ragdoll.RagdollPart;
import ru.liko.trauma.ragdoll.RagdollTransform;

import javax.vecmath.Quat4f;
import javax.vecmath.Vector3d;
import javax.vecmath.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Physics ragdoll for MannequinEntity.
 * Reuses the same JBullet physics world and sends the same network packets
 * as player ragdolls so the client renderer works for both.
 */
public class MannequinRagdoll {

    private final MannequinEntity entity;
    private final ServerLevel level;
    private final DiscreteDynamicsWorld world;
    private final PhysicsWorld physicsWorld;

    private final List<RigidBody> ragdollParts = new ArrayList<>(6);
    private final List<TypedConstraint> ragdollJoints = new ArrayList<>(5);
    private final List<CollisionObject> localStaticCollision = new ArrayList<>();
    private BlockPos lastCollisionCenter = BlockPos.ZERO;

    public MannequinRagdoll(MannequinEntity entity, ServerLevel level) {
        this.entity = entity;
        this.level = level;
        this.physicsWorld = PhysicsWorld.get(level);
        this.world = physicsWorld.getDynamicsWorld();
    }

    // ======================== MODE ========================

    public void enterRagdollMode() {
        createRagdollBodies();

        RagdollStartPayload payload = new RagdollStartPayload(entity.getId(), entity.getId(), java.util.UUID.randomUUID(), getRagdollTransforms());
        PacketDistributor.sendToAllPlayers(payload);
    }

    /**
     * Send current ragdoll state to a specific player (for late-tracking sync).
     */
    public void sendStateTo(net.minecraft.server.level.ServerPlayer target) {
        if (ragdollParts.isEmpty()) return;
        RagdollStartPayload payload = new RagdollStartPayload(
                entity.getId(), entity.getId(), java.util.UUID.randomUUID(), getRagdollTransforms()
        );
        PacketDistributor.sendToPlayer(target, payload);
    }

    // ======================== TICK / STEP ========================

    /**
     * Called AFTER PhysicsWorld.stepSimulation() — sends updates with post-step positions.
     * This fixes the stutter caused by tick() sending pre-step (stale) positions.
     */
    public void afterStep() {
        if (ragdollParts.isEmpty()) return;

        RagdollTransform[] transforms = getRagdollTransforms();
        RagdollUpdatePayload payload = new RagdollUpdatePayload(entity.getId(), System.currentTimeMillis(), transforms);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);

        // Teleport entity to torso position (same as prototype — no Y offset)
        Vec3 torsoPos = getTorsoPosition();
        if (torsoPos != null) {
            entity.teleportTo(torsoPos.x, torsoPos.y, torsoPos.z);
        }

        // Clamp velocities
        for (RigidBody r : ragdollParts) {
            Vector3f vel = new Vector3f();
            r.getLinearVelocity(vel);
            if (vel.y < -80f) vel.y = -80f;
            float speed = vel.length();
            if (speed > 90f) {
                vel.scale(90f / speed);
            }
            r.setLinearVelocity(vel);
        }

        updateLocalWorldCollision();
        correctInterpenetrations();
    }

    /** @deprecated Use PhysicsWorld registration + afterStep() instead */
    @Deprecated
    public void tick() {
        afterStep();
    }

    public boolean isActive() {
        return !ragdollParts.isEmpty();
    }

    // ======================== BODY CREATION ========================

    private void createRagdollBodies() {
        float yRot = entity.getYRot();

        Vector3d pos = new Vector3d(entity.getX(), entity.getY(), entity.getZ());
        Quaternionf q = new Quaternionf().rotateXYZ(0f, (float) Math.toRadians(180 - yRot), 0f);
        Quat4f qq = new Quat4f(q.x, q.y, q.z, q.w);

        Vector3f torsoOrigin = new Vector3f((float) pos.x, (float) pos.y + 1.3f, (float) pos.z);

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
        com.bulletphysics.linearmath.Transform torsoT = new com.bulletphysics.linearmath.Transform();
        torsoT.setIdentity();
        torsoT.setRotation(qq);
        torsoT.origin.set(torsoOrigin);
        RigidBody torsoBody = new RigidBody(makeInfo(8, torsoT, torsoShape));
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
        com.bulletphysics.linearmath.Transform headT = new com.bulletphysics.linearmath.Transform();
        headT.setIdentity();
        headT.origin.set(headPos);
        headT.setRotation(qq);
        RigidBody headBody = new RigidBody(makeInfo(4, headT, headShape));
        headBody.setCcdMotionThreshold(0.01f);
        headBody.setDamping(0.15f, 0.90f);
        headBody.setCcdSweptSphereRadius(0.25f);
        world.addRigidBody(headBody);
        ragdollParts.add(headBody);

        // --- Left Leg ---
        CollisionShape legShape = new BoxShape(new Vector3f(0.15f, 0.45f, 0.15f));
        Vector3f lLegPos = worldOffset.apply(new Vector3f(-0.1f, -0.75f, 0f));
        com.bulletphysics.linearmath.Transform lLegT = new com.bulletphysics.linearmath.Transform();
        lLegT.setIdentity();
        lLegT.origin.set(lLegPos);
        lLegT.setRotation(qq);
        RigidBody lLegBody = new RigidBody(makeInfo(6, lLegT, legShape));
        lLegBody.setCcdMotionThreshold(0.01f);
        lLegBody.setDamping(0.15f, 0.90f);
        lLegBody.setCcdSweptSphereRadius(0.35f);
        world.addRigidBody(lLegBody);
        ragdollParts.add(lLegBody);

        // --- Right Leg ---
        Vector3f rLegPos = worldOffset.apply(new Vector3f(0.1f, -0.75f, 0f));
        com.bulletphysics.linearmath.Transform rLegT = new com.bulletphysics.linearmath.Transform();
        rLegT.setIdentity();
        rLegT.origin.set(rLegPos);
        rLegT.setRotation(qq);
        RigidBody rLegBody = new RigidBody(makeInfo(6, rLegT, legShape));
        rLegBody.setCcdMotionThreshold(0.01f);
        rLegBody.setDamping(0.15f, 0.90f);
        rLegBody.setCcdSweptSphereRadius(0.35f);
        world.addRigidBody(rLegBody);
        ragdollParts.add(rLegBody);

        // --- Left Arm ---
        CollisionShape armShape = new BoxShape(new Vector3f(0.1f, 0.35f, 0.1f));
        Vector3f lArmPos = worldOffset.apply(new Vector3f(-0.4f, 0.05f, 0f));
        com.bulletphysics.linearmath.Transform lArmT = new com.bulletphysics.linearmath.Transform();
        lArmT.setIdentity();
        lArmT.origin.set(lArmPos);
        lArmT.setRotation(qq);
        RigidBody lArmBody = new RigidBody(makeInfo(4, lArmT, armShape));
        lArmBody.setCcdMotionThreshold(0.01f);
        lArmBody.setDamping(0.15f, 0.90f);
        lArmBody.setCcdSweptSphereRadius(0.3f);
        world.addRigidBody(lArmBody);
        ragdollParts.add(lArmBody);

        // --- Right Arm ---
        Vector3f rArmPos = worldOffset.apply(new Vector3f(0.4f, 0.05f, 0f));
        com.bulletphysics.linearmath.Transform rArmT = new com.bulletphysics.linearmath.Transform();
        rArmT.setIdentity();
        rArmT.origin.set(rArmPos);
        rArmT.setRotation(qq);
        RigidBody rArmBody = new RigidBody(makeInfo(4, rArmT, armShape));
        rArmBody.setCcdMotionThreshold(0.01f);
        rArmBody.setDamping(0.15f, 0.90f);
        rArmBody.setCcdSweptSphereRadius(0.3f);
        world.addRigidBody(rArmBody);
        ragdollParts.add(rArmBody);

        createRagdollJoints();
        updateLocalWorldCollision();
    }

    // ======================== JOINTS ========================

    private void createRagdollJoints() {
        if (ragdollParts.size() < 6) return;

        RigidBody torso = ragdollParts.get(0);
        RigidBody head = ragdollParts.get(1);
        RigidBody lLeg = ragdollParts.get(2);
        RigidBody rLeg = ragdollParts.get(3);
        RigidBody lArm = ragdollParts.get(4);
        RigidBody rArm = ragdollParts.get(5);

        com.bulletphysics.linearmath.Transform tT = new com.bulletphysics.linearmath.Transform();
        torso.getMotionState().getWorldTransform(tT);
        com.bulletphysics.linearmath.Transform tH = new com.bulletphysics.linearmath.Transform();
        head.getMotionState().getWorldTransform(tH);
        com.bulletphysics.linearmath.Transform tLL = new com.bulletphysics.linearmath.Transform();
        lLeg.getMotionState().getWorldTransform(tLL);
        com.bulletphysics.linearmath.Transform tRL = new com.bulletphysics.linearmath.Transform();
        rLeg.getMotionState().getWorldTransform(tRL);
        com.bulletphysics.linearmath.Transform tLA = new com.bulletphysics.linearmath.Transform();
        lArm.getMotionState().getWorldTransform(tLA);
        com.bulletphysics.linearmath.Transform tRA = new com.bulletphysics.linearmath.Transform();
        rArm.getMotionState().getWorldTransform(tRA);

        Function<Vector3f, Vector3f> torsoLocalToWorld = (local) -> {
            Quat4f trot = tT.getRotation(new Quat4f());
            Vector3f out = rotateVecByQuat(trot, local);
            out.add(tT.origin);
            return out;
        };

        // Head <-> Torso
        Vector3f torsoTop = torsoLocalToWorld.apply(new Vector3f(0f, 0.4f, 0f));
        Quat4f hrot = tH.getRotation(new Quat4f());
        Vector3f headBot = rotateVecByQuat(hrot, new Vector3f(0f, -0.2f, 0f));
        headBot.add(tH.origin);
        ragdollJoints.add(createJoint(torso, head, midpoint(torsoTop, headBot),
                new Vector3f(0, 0, 0), new Vector3f(0, 0, 0),
                new Vector3f(rad(-30), rad(-20), rad(-30)), new Vector3f(rad(30), rad(50), rad(30))));

        // Left Leg <-> Torso
        Vector3f leftHip = torsoLocalToWorld.apply(new Vector3f(-0.1f, -0.55f, 0f));
        Quat4f llrot = tLL.getRotation(new Quat4f());
        Vector3f llTop = rotateVecByQuat(llrot, new Vector3f(0f, 0.45f, 0f));
        llTop.add(tLL.origin);
        ragdollJoints.add(createJoint(torso, lLeg, midpoint(leftHip, llTop),
                new Vector3f(-0.05f, -0.05f, -0.05f), new Vector3f(0.05f, 0.05f, 0.05f),
                new Vector3f(rad(-40), 0f, rad(-10)), new Vector3f(rad(80), 0f, rad(10))));

        // Right Leg <-> Torso
        Vector3f rightHip = torsoLocalToWorld.apply(new Vector3f(0.1f, -0.55f, 0f));
        Quat4f rlrot = tRL.getRotation(new Quat4f());
        Vector3f rlTop = rotateVecByQuat(rlrot, new Vector3f(0f, 0.45f, 0f));
        rlTop.add(tRL.origin);
        ragdollJoints.add(createJoint(torso, rLeg, midpoint(rightHip, rlTop),
                new Vector3f(-0.05f, -0.05f, -0.05f), new Vector3f(0.05f, 0.05f, 0.05f),
                new Vector3f(rad(-40), 0f, rad(-10)), new Vector3f(rad(80), 0f, rad(10))));

        // Left Arm <-> Torso
        Vector3f leftShoulder = torsoLocalToWorld.apply(new Vector3f(-0.35f, 0.05f, 0f));
        Quat4f larot = tLA.getRotation(new Quat4f());
        Vector3f laTop = rotateVecByQuat(larot, new Vector3f(0f, 0.35f, 0f));
        laTop.add(tLA.origin);
        ragdollJoints.add(createJoint(torso, lArm, midpoint(leftShoulder, laTop),
                new Vector3f(-0.02f, -0.02f, -0.02f), new Vector3f(0.02f, 0.02f, 0.02f),
                new Vector3f(rad(-80), rad(-30), rad(-40)), new Vector3f(rad(80), rad(30), rad(40))));

        // Right Arm <-> Torso
        Vector3f rightShoulder = torsoLocalToWorld.apply(new Vector3f(0.35f, 0.05f, 0f));
        Quat4f rarot = tRA.getRotation(new Quat4f());
        Vector3f raTop = rotateVecByQuat(rarot, new Vector3f(0f, 0.35f, 0f));
        raTop.add(tRA.origin);
        ragdollJoints.add(createJoint(torso, rArm, midpoint(rightShoulder, raTop),
                new Vector3f(-0.02f, -0.02f, -0.02f), new Vector3f(0.02f, 0.02f, 0.02f),
                new Vector3f(rad(-80), rad(-30), rad(-40)), new Vector3f(rad(80), rad(30), rad(40))));
    }

    // ======================== FORCES ========================

    public void applyKnockbackImpulse(Vec3 direction, float strength) {
        Vector3f impulse = new Vector3f((float) direction.x * strength, (float) direction.y * strength, (float) direction.z * strength);
        for (RigidBody body : ragdollParts) {
            body.activate(true);
            body.applyCentralImpulse(impulse);
        }
    }

    public void applyRandomVelocity(float maxX, float maxY, float maxZ) {
        java.util.Random rand = new java.util.Random();
        for (RigidBody body : ragdollParts) {
            body.activate(true);
            Vector3f impulse = new Vector3f(
                    (rand.nextFloat() - 0.5f) * maxX,
                    (rand.nextFloat() * 5f) + maxY,
                    (rand.nextFloat() - 0.5f) * maxZ
            );
            body.applyCentralImpulse(impulse);
            Vector3f torque = new Vector3f(
                    (rand.nextFloat() - 0.5f) * 10f,
                    (rand.nextFloat() - 0.5f) * 10f,
                    (rand.nextFloat() - 0.5f) * 10f
            );
            body.applyTorqueImpulse(torque);
        }
    }

    // ======================== WORLD COLLISION ========================

    public void updateLocalWorldCollision() {
        BlockPos center = entity.getOnPos();
        // Only rebuild if entity moved significantly (performance optimization)
        if (center.distManhattan(lastCollisionCenter) < 2) return;
        lastCollisionCenter = center;

        for (CollisionObject c : localStaticCollision) world.removeCollisionObject(c);
        localStaticCollision.clear();

        int radius = 3;
        for (int dx = -radius; dx <= radius; dx++)
            for (int dy = -radius; dy <= radius; dy++)
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos bpos = center.offset(dx, dy, dz);
                    BlockState state = entity.level().getBlockState(bpos);
                    if (state.isAir() || state.getFluidState().isSource()) continue;
                    if (isCompletelySurrounded(bpos, entity.level())) continue;

                    VoxelShape shape = state.getCollisionShape(entity.level(), bpos);
                    if (shape.isEmpty()) continue;

                    for (AABB box : shape.toAabbs()) {
                        Vector3f halfExtents = new Vector3f(
                                (float) (box.getXsize() / 2),
                                (float) (box.getYsize() / 2),
                                (float) (box.getZsize() / 2)
                        );
                        CollisionShape cs = new BoxShape(halfExtents);

                        com.bulletphysics.linearmath.Transform t = new com.bulletphysics.linearmath.Transform();
                        t.setIdentity();
                        t.origin.set(
                                (float) (bpos.getX() + box.minX + box.getXsize() / 2),
                                (float) (bpos.getY() + box.minY + box.getYsize() / 2),
                                (float) (bpos.getZ() + box.minZ + box.getZsize() / 2)
                        );

                        RigidBody rb = new RigidBody(new RigidBodyConstructionInfo(0f, new DefaultMotionState(t), cs, new Vector3f()));
                        rb.setCollisionFlags(rb.getCollisionFlags() | CollisionFlags.STATIC_OBJECT);
                        world.addRigidBody(rb);
                        localStaticCollision.add(rb);
                    }
                }
    }

    private boolean isCompletelySurrounded(BlockPos pos, Level lvl) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            BlockState ns = lvl.getBlockState(neighbor);
            if (ns.isAir() || ns.getFluidState().isSource() || ns.getCollisionShape(lvl, neighbor).isEmpty() || ns.canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    private void correctInterpenetrations() {
        for (PersistentManifold manifold : physicsWorld.getDispatcher().getInternalManifoldPointer()) {
            if (manifold == null) continue;
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
                }
            }
        }
    }

    // ======================== TRANSFORMS ========================

    public RagdollTransform[] getRagdollTransforms() {
        RagdollTransform[] out = new RagdollTransform[ragdollParts.size()];
        for (int i = 0; i < ragdollParts.size(); i++) {
            com.bulletphysics.linearmath.Transform t = new com.bulletphysics.linearmath.Transform();
            ragdollParts.get(i).getMotionState().getWorldTransform(t);
            Quat4f rot = t.getRotation(new Quat4f());
            Vector3f vel = new Vector3f();
            ragdollParts.get(i).getLinearVelocity(vel);
            out[i] = new RagdollTransform(i, t.origin.x, t.origin.y, t.origin.z,
                    rot.x, rot.y, rot.z, rot.w, vel.x, vel.y, vel.z);
        }
        return out;
    }

    public Vec3 getTorsoPosition() {
        if (ragdollParts.isEmpty()) return null;
        com.bulletphysics.linearmath.Transform t = new com.bulletphysics.linearmath.Transform();
        ragdollParts.get(0).getMotionState().getWorldTransform(t);
        return new Vec3(t.origin.x, t.origin.y, t.origin.z);
    }

    // ======================== CLEANUP ========================

    public void destroy() {
        for (TypedConstraint c : ragdollJoints) world.removeConstraint(c);
        for (RigidBody r : ragdollParts) world.removeRigidBody(r);
        for (CollisionObject co : localStaticCollision) world.removeCollisionObject(co);
        ragdollJoints.clear();
        ragdollParts.clear();
        localStaticCollision.clear();

        RagdollEndPayload payload = new RagdollEndPayload(entity.getId());
        PacketDistributor.sendToAllPlayers(payload);
    }

    // ======================== UTILITY ========================

    private RigidBodyConstructionInfo makeInfo(float mass, com.bulletphysics.linearmath.Transform startTransform, CollisionShape shape) {
        Vector3f inertia = new Vector3f();
        if (mass > 0f) shape.calculateLocalInertia(mass, inertia);
        DefaultMotionState motionState = new DefaultMotionState(startTransform);
        RigidBodyConstructionInfo info = new RigidBodyConstructionInfo(mass, motionState, shape, inertia);
        info.linearDamping = 0.15f;
        info.angularDamping = 0.90f;
        info.restitution = 0.0f;
        info.friction = 1.2f;
        info.additionalDamping = true;
        return info;
    }

    private Generic6DofConstraint createJoint(RigidBody a, RigidBody b, Vector3f worldAnchor,
                                              Vector3f linearLower, Vector3f linearUpper,
                                              Vector3f angularLower, Vector3f angularUpper) {
        com.bulletphysics.linearmath.Transform ta = new com.bulletphysics.linearmath.Transform();
        a.getMotionState().getWorldTransform(ta);
        com.bulletphysics.linearmath.Transform tb = new com.bulletphysics.linearmath.Transform();
        b.getMotionState().getWorldTransform(tb);

        Vector3f localA = worldPointToLocal(ta, worldAnchor);
        Vector3f localB = worldPointToLocal(tb, worldAnchor);

        com.bulletphysics.linearmath.Transform frameA = new com.bulletphysics.linearmath.Transform();
        frameA.setIdentity(); frameA.origin.set(localA);
        com.bulletphysics.linearmath.Transform frameB = new com.bulletphysics.linearmath.Transform();
        frameB.setIdentity(); frameB.origin.set(localB);

        Generic6DofConstraint joint = new Generic6DofConstraint(a, b, frameA, frameB, true);
        joint.setLinearLowerLimit(linearLower);
        joint.setLinearUpperLimit(linearUpper);
        joint.setAngularLowerLimit(angularLower);
        joint.setAngularUpperLimit(angularUpper);

        a.activate();
        b.activate();
        world.addConstraint(joint, true);
        return joint;
    }

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

    private Vector3f worldPointToLocal(com.bulletphysics.linearmath.Transform bodyWorldTransform, Vector3f worldPoint) {
        Vector3f delta = new Vector3f(worldPoint);
        delta.sub(bodyWorldTransform.origin);
        Quat4f rot = bodyWorldTransform.getRotation(new Quat4f());
        Quat4f qc = new Quat4f(-rot.x, -rot.y, -rot.z, rot.w);
        return rotateVecByQuat(qc, delta);
    }
}
