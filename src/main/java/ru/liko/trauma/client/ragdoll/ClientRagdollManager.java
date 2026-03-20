package ru.liko.trauma.client.ragdoll;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import ru.liko.trauma.ragdoll.RagdollPart;
import ru.liko.trauma.ragdoll.RagdollTransform;

import javax.annotation.Nullable;
import java.util.*;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side ragdoll manager. Stores ragdoll state for each player entity,
 * receives updates from the server, and provides interpolated transforms for rendering.
 */
public class ClientRagdollManager {

    private static final Map<Integer, ClientRagdoll> RAGDOLLS = new ConcurrentHashMap<>();

    public static void addOrUpdate(int playerEntityId, int ragdollId, @Nullable UUID playerUUID, RagdollTransform[] transforms, long serverTime) {
        ClientRagdoll r = RAGDOLLS.get(playerEntityId);
        if (r == null) {
            r = new ClientRagdoll(playerEntityId, ragdollId);
            RAGDOLLS.put(playerEntityId, r);
        }
        if (playerUUID != null) {
            r.cachePlayerUUID(playerUUID);
            // Try to look up skin immediately if the player entity is tracked client-side
            if (r.getCachedSkinTexture() == null) {
                tryResolveSkinFromUUID(r, playerUUID);
            }
        }
        r.pushUpdate(transforms, serverTime);
    }

    /** Overload without UUID for update-only calls */
    public static void addOrUpdate(int playerEntityId, int ragdollId, RagdollTransform[] transforms, long serverTime) {
        addOrUpdate(playerEntityId, ragdollId, null, transforms, serverTime);
    }

    /**
     * Try to resolve skin texture from a player UUID by looking in the level's
     * player list. If the player entity is loaded, cache its skin texture now.
     */
    private static void tryResolveSkinFromUUID(ClientRagdoll rag, UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (net.minecraft.world.entity.player.Player p : mc.level.players()) {
            if (p.getUUID().equals(uuid) && p instanceof net.minecraft.client.player.AbstractClientPlayer acp) {
                rag.cacheSkinTexture(acp.getSkin().texture());
                return;
            }
        }
    }

    public static void remove(int playerEntityId) {
        RAGDOLLS.remove(playerEntityId);
    }

    @Nullable
    public static ClientRagdoll get(int playerEntityId) {
        return RAGDOLLS.get(playerEntityId);
    }

    public static Collection<ClientRagdoll> getAll() {
        return new ArrayList<>(RAGDOLLS.values());
    }

    public static void clear() {
        RAGDOLLS.clear();
    }

    /**
     * Check if the local (this client's) player currently has an active ragdoll.
     * Used by client-side input blocking to prevent movement during ragdoll.
     */
    public static boolean isLocalPlayerRagdolled() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return false;

        ClientRagdoll rag = RAGDOLLS.get(player.getId());
        return rag != null && rag.isActive();
    }

    /**
     * Get the number of active ragdolls (for debugging/diagnostics).
     */
    public static int getActiveCount() {
        int count = 0;
        for (ClientRagdoll rag : RAGDOLLS.values()) {
            if (rag.isActive()) count++;
        }
        return count;
    }

    /**
     * Mixin-safe method: returns camera data as plain floats.
     * Returns float[7] = {x, y, z, qx, qy, qz, qw} or null if no active ragdoll.
     * This avoids referencing javax.vecmath types which aren't available at mixin load time.
     */
    @Nullable
    public static float[] getCameraDataForEntity(int entityId, float partialTick) {
        ClientRagdoll rag = RAGDOLLS.get(entityId);
        if (rag == null || !rag.isActive()) return null;

        RagdollTransform headT = rag.getPartInterpolated(RagdollPart.HEAD, partialTick);
        if (headT == null) return null;

        return new float[]{
                headT.position.x, headT.position.y, headT.position.z,
                headT.rotation.x, headT.rotation.y, headT.rotation.z, headT.rotation.w
        };
    }

    // ============================================================

    public static class ClientRagdoll {
        public final int playerEntityId;
        public final int ragdollId;

        private final RagdollTransform[] last = new RagdollTransform[6];
        private final RagdollTransform[] current = new RagdollTransform[6];

        // Cached skin texture for rendering after entity removal (death ragdoll orphaning)
        private ResourceLocation cachedSkinTexture = null;

        // UUID of the original player (used to find skin for orphaned death ragdolls)
        @Nullable
        private UUID cachedPlayerUUID = null;

        // Time-based interpolation state (client system time)
        private long lastReceiveMs = 0;
        private long receiveMs = 0;
        private float smoothTickInterval = 50f; // ms, initial guess = 1 server tick (20 TPS)

        // Blend-in state: smooth transition when ragdoll first activates
        private long startTimeMs = 0;
        private RagdollTransform[] initialTransforms = null;
        private boolean blendComplete = false;
        private static final long BLEND_DURATION_MS = 300;

        public ClientRagdoll(int playerEntityId, int ragdollId) {
            this.playerEntityId = playerEntityId;
            this.ragdollId = ragdollId;
        }

        public void pushUpdate(RagdollTransform[] transforms, long serverTime) {
            // Shift current -> last
            System.arraycopy(current, 0, last, 0, 6);
            for (RagdollTransform t : transforms) {
                if (t == null) continue;
                int idx = t.partId;
                if (idx >= 0 && idx < 6) current[idx] = t;
            }

            // Track receive times for time-based interpolation
            lastReceiveMs = receiveMs;
            receiveMs = System.currentTimeMillis();

            // Smooth the tick interval estimate using exponential moving average
            if (lastReceiveMs > 0) {
                long rawInterval = receiveMs - lastReceiveMs;
                if (rawInterval > 0 && rawInterval < 500) { // sanity: ignore huge gaps
                    smoothTickInterval = smoothTickInterval * 0.8f + rawInterval * 0.2f;
                }
            }

            // Store initial transforms for blend-in on first update
            if (startTimeMs == 0) {
                startTimeMs = System.currentTimeMillis();
                initialTransforms = new RagdollTransform[6];
                System.arraycopy(current, 0, initialTransforms, 0, 6);
            }
        }

        /**
         * Get interpolated transform for a body part.
         * Uses time-based interpolation between server snapshots with extrapolation support.
         * @param partialTicks ignored — interpolation is now time-based
         */
        @Nullable
        public RagdollTransform getPartInterpolated(RagdollPart part, float partialTicks) {
            int idx = part.index;
            RagdollTransform prev = last[idx];
            RagdollTransform cur = current[idx];
            if (cur == null && prev == null) return null;
            if (prev == null) return applyBlend(cur, idx);
            if (cur == null) return applyBlend(prev, idx);

            float a = computeAlpha();
            float clampedA = Math.min(a, 1.0f);

            // Interpolate position
            float x, y, z;
            if (a > 1.0f && cur.velocity != null) {
                // Extrapolate using velocity when packet is late
                float extraSec = (a - 1.0f) * smoothTickInterval / 1000f;
                x = cur.position.x + cur.velocity.x * extraSec;
                y = cur.position.y + cur.velocity.y * extraSec;
                z = cur.position.z + cur.velocity.z * extraSec;
            } else {
                x = lerp(prev.position.x, cur.position.x, a);
                y = lerp(prev.position.y, cur.position.y, a);
                z = lerp(prev.position.z, cur.position.z, a);
            }

            // Slerp rotation (no extrapolation for rotation — use clamped alpha)
            float[] q = slerp(
                    prev.rotation.x, prev.rotation.y, prev.rotation.z, prev.rotation.w,
                    cur.rotation.x, cur.rotation.y, cur.rotation.z, cur.rotation.w,
                    clampedA
            );

            RagdollTransform result = new RagdollTransform(idx, x, y, z, q[0], q[1], q[2], q[3],
                    cur.velocity.x, cur.velocity.y, cur.velocity.z);
            return applyBlend(result, idx);
        }

        public Map<RagdollPart, RagdollTransform> getAllPartsInterpolated(float partialTicks) {
            Map<RagdollPart, RagdollTransform> map = new EnumMap<>(RagdollPart.class);
            for (RagdollPart part : RagdollPart.values()) {
                map.put(part, getPartInterpolated(part, partialTicks));
            }
            return map;
        }

        public boolean isActive() {
            for (RagdollTransform t : current) {
                if (t != null) return true;
            }
            return false;
        }

        /** Cache the player's skin texture so we can render after entity removal. */
        public void cacheSkinTexture(ResourceLocation skin) {
            this.cachedSkinTexture = skin;
        }

        /** Get cached skin texture, or null if never cached. */
        @Nullable
        public ResourceLocation getCachedSkinTexture() {
            return cachedSkinTexture;
        }

        /** Cache the UUID of the original player (received from server via RagdollStartPayload). */
        public void cachePlayerUUID(UUID uuid) {
            this.cachedPlayerUUID = uuid;
        }

        /** Get the original player UUID, or null if not known. */
        @Nullable
        public UUID getCachedPlayerUUID() {
            return cachedPlayerUUID;
        }

        // --- Time-based interpolation ---

        /**
         * Compute interpolation alpha based on elapsed time since last server update.
         * alpha=0 at receive time, alpha=1 at next expected update, alpha>1 if packet is late.
         */
        private float computeAlpha() {
            long now = System.currentTimeMillis();
            float elapsed = (float) (now - receiveMs);
            float interval = Math.max(smoothTickInterval, 10f); // min 10ms safety
            return Math.max(0f, Math.min(1.3f, elapsed / interval)); // allow 30% extrapolation
        }

        // --- Blend-in support ---

        /**
         * Apply smooth blend-in when ragdoll first activates (over BLEND_DURATION_MS).
         * Uses smoothstep easing for natural feel.
         */
        @Nullable
        private RagdollTransform applyBlend(RagdollTransform physics, int idx) {
            if (physics == null) return null;
            if (blendComplete || initialTransforms == null || initialTransforms[idx] == null) return physics;

            long now = System.currentTimeMillis();
            float blendAlpha = Math.min(1f, (now - startTimeMs) / (float) BLEND_DURATION_MS);
            if (blendAlpha >= 1f) {
                blendComplete = true;
                return physics;
            }

            // Smoothstep easing for natural blend curve
            blendAlpha = blendAlpha * blendAlpha * (3f - 2f * blendAlpha);

            RagdollTransform init = initialTransforms[idx];
            float x = lerp(init.position.x, physics.position.x, blendAlpha);
            float y = lerp(init.position.y, physics.position.y, blendAlpha);
            float z = lerp(init.position.z, physics.position.z, blendAlpha);
            float[] q = slerp(
                    init.rotation.x, init.rotation.y, init.rotation.z, init.rotation.w,
                    physics.rotation.x, physics.rotation.y, physics.rotation.z, physics.rotation.w,
                    blendAlpha
            );
            return new RagdollTransform(idx, x, y, z, q[0], q[1], q[2], q[3],
                    physics.velocity.x, physics.velocity.y, physics.velocity.z);
        }

        // --- Math helpers ---

        private float lerp(float a, float b, float t) {
            return a + (b - a) * t;
        }

        private float[] slerp(float x1, float y1, float z1, float w1,
                              float x2, float y2, float z2, float w2, float t) {
            // Normalize
            float mag1 = (float) Math.sqrt(x1 * x1 + y1 * y1 + z1 * z1 + w1 * w1);
            float mag2 = (float) Math.sqrt(x2 * x2 + y2 * y2 + z2 * z2 + w2 * w2);
            if (mag1 < 0.0001f || mag2 < 0.0001f) return new float[]{x2, y2, z2, w2};
            x1 /= mag1; y1 /= mag1; z1 /= mag1; w1 /= mag1;
            x2 /= mag2; y2 /= mag2; z2 /= mag2; w2 /= mag2;

            float dot = x1 * x2 + y1 * y2 + z1 * z2 + w1 * w2;
            if (dot < 0f) {
                dot = -dot; x2 = -x2; y2 = -y2; z2 = -z2; w2 = -w2;
            }

            if (dot > 0.9995f) {
                // Linear fallback for nearly identical quaternions
                float rx = x1 + t * (x2 - x1);
                float ry = y1 + t * (y2 - y1);
                float rz = z1 + t * (z2 - z1);
                float rw = w1 + t * (w2 - w1);
                float m = (float) Math.sqrt(rx * rx + ry * ry + rz * rz + rw * rw);
                if (m < 0.0001f) return new float[]{x2, y2, z2, w2};
                return new float[]{rx / m, ry / m, rz / m, rw / m};
            }

            double theta0 = Math.acos(dot);
            double theta = theta0 * t;
            double sinTheta = Math.sin(theta);
            double sinTheta0 = Math.sin(theta0);

            float s0 = (float) (Math.cos(theta) - dot * sinTheta / sinTheta0);
            float s1 = (float) (sinTheta / sinTheta0);

            return new float[]{
                    s0 * x1 + s1 * x2,
                    s0 * y1 + s1 * y2,
                    s0 * z1 + s1 * z2,
                    s0 * w1 + s1 * w2
            };
        }
    }
}
