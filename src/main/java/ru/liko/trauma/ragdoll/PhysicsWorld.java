package ru.liko.trauma.ragdoll;

import com.bulletphysics.collision.broadphase.AxisSweep3;
import com.bulletphysics.collision.broadphase.BroadphaseInterface;
import com.bulletphysics.collision.dispatch.CollisionConfiguration;
import com.bulletphysics.collision.dispatch.CollisionDispatcher;
import com.bulletphysics.collision.dispatch.CollisionObject;
import com.bulletphysics.collision.dispatch.DefaultCollisionConfiguration;
import com.bulletphysics.dynamics.DiscreteDynamicsWorld;
import com.bulletphysics.dynamics.constraintsolver.ConstraintSolver;
import com.bulletphysics.dynamics.constraintsolver.SequentialImpulseConstraintSolver;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import ru.liko.trauma.common.entity.MannequinRagdoll;

import javax.vecmath.Vector3f;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Server-side JBullet physics world. One instance per dimension.
 * Manages all PlayerRagdoll instances and steps the simulation.
 */
public class PhysicsWorld {

    private static final Map<ServerLevel, PhysicsWorld> INSTANCES = new ConcurrentHashMap<>();

    public static PhysicsWorld get(ServerLevel level) {
        return INSTANCES.computeIfAbsent(level, PhysicsWorld::new);
    }

    /** Get all active PhysicsWorld instances across all dimensions. */
    public static Collection<PhysicsWorld> getAll() {
        return INSTANCES.values();
    }

    public static void remove(ServerLevel level) {
        PhysicsWorld world = INSTANCES.remove(level);
        if (world != null) {
            world.cleanup();
        }
    }

    private final ServerLevel level;
    private final BroadphaseInterface broadphase;
    private final CollisionConfiguration collisionConfig;
    private final CollisionDispatcher dispatcher;
    private final ConstraintSolver solver;
    private final DiscreteDynamicsWorld dynamicsWorld;

    private final Map<UUID, PlayerRagdoll> playerRagdolls = new ConcurrentHashMap<>();

    // Death ragdolls that are no longer tied to a player (player
    // respawned/disconnected)
    private final List<PlayerRagdoll> orphanedDeathRagdolls = new CopyOnWriteArrayList<>();

    // Mannequin ragdolls stepped in sync with the physics world
    private final List<MannequinRagdoll> mannequinRagdolls = new CopyOnWriteArrayList<>();

    // Flag to prevent duplicate restore from saved data
    private boolean savedDataRestored = false;

    public PhysicsWorld(ServerLevel level) {
        this.level = level;

        collisionConfig = new DefaultCollisionConfiguration();
        dispatcher = new CollisionDispatcher(collisionConfig);

        Vector3f worldAabbMin = new Vector3f(-10000f, -10000f, -10000f);
        Vector3f worldAabbMax = new Vector3f(10000f, 10000f, 10000f);
        broadphase = new AxisSweep3(worldAabbMin, worldAabbMax);

        solver = new SequentialImpulseConstraintSolver();

        dynamicsWorld = new DiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfig);
        dynamicsWorld.setGravity(new Vector3f(0f, -9.81f, 0f));
        dynamicsWorld.getSolverInfo().numIterations = 20;
    }

    /**
     * Step the physics simulation. Called once per server tick.
     * <p>
     * Profile hotspots during ragdoll load (Spark/JFR/async-profiler sample mode):
     * {@link DiscreteDynamicsWorld#stepSimulation(float, int, float)} (Bullet),
     * {@link PlayerRagdoll#afterStep()} → {@link PlayerRagdoll#rebuildLocalStaticBlockCollision},
     * {@link PlayerRagdoll#syncEntityXYZToTorsoIfNeeded}, {@link PlayerRagdoll#correctInterpenetrations}.
     */
    public void step(float dt) {
        // 4 sub-steps at 1/60 s each: deterministic, smooth, low overhead.
        dynamicsWorld.stepSimulation(dt, 4, 1f / 60f);

        for (PlayerRagdoll pr : playerRagdolls.values()) {
            pr.afterStep();
        }

        // Step orphaned death ragdolls (player respawned/disconnected but ragdoll
        // persists)
        for (PlayerRagdoll dr : orphanedDeathRagdolls) {
            dr.afterStep();
            // Remove expired death ragdolls
            if (!dr.isRagdollActive()) {
                orphanedDeathRagdolls.remove(dr);
            }
        }

        // Step mannequin ragdolls in sync with physics
        for (MannequinRagdoll mr : mannequinRagdolls) {
            mr.afterStep();
        }

        reconcilePlayers();
    }

    public DiscreteDynamicsWorld getDynamicsWorld() {
        return dynamicsWorld;
    }

    public CollisionDispatcher getDispatcher() {
        return dispatcher;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public void addPlayer(ServerPlayer player) {
        playerRagdolls.computeIfAbsent(player.getUUID(), uuid -> new PlayerRagdoll(player, this));
    }

    public void removePlayer(ServerPlayer player) {
        PlayerRagdoll pr = playerRagdolls.remove(player.getUUID());
        if (pr != null)
            pr.destroy();
    }

    public PlayerRagdoll getPlayerRagdoll(ServerPlayer player) {
        return playerRagdolls.get(player.getUUID());
    }

    public PlayerRagdoll getPlayerRagdoll(UUID uuid) {
        return playerRagdolls.get(uuid);
    }

    // ======================== MANNEQUIN RAGDOLL REGISTRATION
    // ========================

    public void registerMannequinRagdoll(MannequinRagdoll mr) {
        mannequinRagdolls.add(mr);
    }

    public void unregisterMannequinRagdoll(MannequinRagdoll mr) {
        mannequinRagdolls.remove(mr);
    }

    // ======================== DEATH RAGDOLL MANAGEMENT ========================

    // A static counter for virtual ragdoll IDs (negative to avoid player ID
    // collisions)
    private static int nextVirtualRagdollId = -1000;

    /**
     * Move a death ragdoll from the player map to the orphaned list.
     * Called when a player respawns — the death ragdoll persists independently.
     * Assigns a new virtual ID so the client doesn't confuse it with the living
     * player.
     */
    public void orphanDeathRagdoll(UUID uuid) {
        PlayerRagdoll pr = playerRagdolls.remove(uuid);
        if (pr != null && pr.getMode() == PlayerRagdoll.Mode.DEATH_RAGDOLL) {
            pr.setOrphaned(true);

            int oldId = pr.getNetworkRagdollId();
            int newId = nextVirtualRagdollId--;
            pr.setNetworkRagdollId(newId);

            // Notify clients to swap old ID with new ID
            ru.liko.trauma.network.RagdollEndPayload endPayload = new ru.liko.trauma.network.RagdollEndPayload(oldId);
            ru.liko.trauma.network.RagdollStartPayload startPayload = new ru.liko.trauma.network.RagdollStartPayload(
                    newId, newId, pr.getPlayerUUID(), pr.getRagdollTransforms());

            for (ServerPlayer sp : level.players()) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, endPayload);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, startPayload);
            }

            orphanedDeathRagdolls.add(pr);
        } else if (pr != null) {
            pr.destroy();
        }
    }

    /**
     * Get ALL active ragdolls (player + orphaned death + mannequin) for force
     * reactions.
     * Used for explosions and other area-of-effect checks.
     */
    public List<PlayerRagdoll> getAllActivePlayerRagdolls() {
        List<PlayerRagdoll> result = new ArrayList<>();
        for (PlayerRagdoll pr : playerRagdolls.values()) {
            if (pr.isRagdollActive())
                result.add(pr);
        }
        result.addAll(orphanedDeathRagdolls);
        return result;
    }

    public List<MannequinRagdoll> getMannequinRagdolls() {
        return mannequinRagdolls;
    }

    public Collection<PlayerRagdoll> getOrphanedDeathRagdolls() {
        return orphanedDeathRagdolls;
    }

    public ServerPlayer getPlayerFromBody(CollisionObject obj) {
        for (PlayerRagdoll pr : playerRagdolls.values()) {
            if (pr.hasBody(obj)) {
                return pr.getPlayer();
            }
        }
        return null;
    }

    public ServerPlayer getServerPlayer(UUID uuid) {
        return (ServerPlayer) level.getPlayerByUUID(uuid);
    }

    /** Remove ragdolls for players who left or changed dimension. */
    public void reconcilePlayers() {
        var it = playerRagdolls.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            PlayerRagdoll pr = entry.getValue();

            // Death ragdolls whose игрок исчез, ушёл в другое измерение или уже респавнулся
            // живым в этом же измерении — отвязываем труп, иначе ragdoll навечно привязан к UUID.
            if (pr.getMode() == PlayerRagdoll.Mode.DEATH_RAGDOLL) {
                ServerPlayer player = pr.getPlayer();
                if (player == null || player.serverLevel() != this.level || !player.isDeadOrDying()) {
                    pr.setOrphaned(true);

                    int oldId = pr.getNetworkRagdollId();
                    int newId = nextVirtualRagdollId--;
                    pr.setNetworkRagdollId(newId);

                    // Notify clients to swap old ID with new ID
                    ru.liko.trauma.network.RagdollEndPayload endPayload = new ru.liko.trauma.network.RagdollEndPayload(
                            oldId);
                    ru.liko.trauma.network.RagdollStartPayload startPayload = new ru.liko.trauma.network.RagdollStartPayload(
                            newId, newId, pr.getPlayerUUID(), pr.getRagdollTransforms());
                    for (ServerPlayer sp : level.players()) {
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, endPayload);
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, startPayload);
                    }

                    orphanedDeathRagdolls.add(pr);
                    it.remove();
                }
                continue;
            }

            ServerPlayer player = pr.getPlayer();
            if (player == null || player.serverLevel() != this.level) {
                pr.destroy();
                it.remove();
            }
        }
    }

    private void cleanup() {
        for (PlayerRagdoll pr : playerRagdolls.values()) {
            pr.destroy();
        }
        playerRagdolls.clear();
        for (PlayerRagdoll dr : orphanedDeathRagdolls) {
            dr.destroy();
        }
        orphanedDeathRagdolls.clear();
        mannequinRagdolls.clear();
    }

    /**
     * Clean up ALL physics worlds across all dimensions. Called on server shutdown.
     */
    public static void cleanupAll() {
        for (PhysicsWorld world : INSTANCES.values()) {
            world.cleanup();
        }
        INSTANCES.clear();
    }

    // ======================== SAVE / LOAD ========================

    public boolean hasRestoredSavedData() {
        return savedDataRestored;
    }

    /**
     * Restore death ragdolls from saved data on first tick after world load.
     */
    public void restoreFromSavedData(ServerLevel serverLevel) {
        savedDataRestored = true;
        RagdollSavedData data = RagdollSavedData.getOrCreate(serverLevel);
        List<RagdollSavedData.SavedDeathRagdoll> savedList = data.getSavedRagdolls();
        if (savedList.isEmpty())
            return;

        for (RagdollSavedData.SavedDeathRagdoll saved : savedList) {
            // Assign a fresh virtual ID to avoid collisions with the counter
            int freshId = nextVirtualRagdollId--;
            PlayerRagdoll ragdoll = PlayerRagdoll.createFromSavedData(
                    this, saved.playerUUID, freshId,
                    saved.deathTicksRemaining, saved.transforms, saved.corpseIntegrity);
            orphanedDeathRagdolls.add(ragdoll);

            // Notify all players in this dimension
            ru.liko.trauma.network.RagdollStartPayload startPayload = new ru.liko.trauma.network.RagdollStartPayload(
                    freshId, freshId, saved.playerUUID, ragdoll.getRagdollTransforms());
            for (net.minecraft.server.level.ServerPlayer sp : serverLevel.players()) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, startPayload);
            }
        }
        data.clearSavedRagdolls();
    }

    /**
     * Save all active death ragdolls to SavedData (called before server shutdown).
     */
    public void saveDeathRagdolls(ServerLevel serverLevel) {
        RagdollSavedData data = RagdollSavedData.getOrCreate(serverLevel);
        data.collectFromPhysicsWorld(this);
    }
}
