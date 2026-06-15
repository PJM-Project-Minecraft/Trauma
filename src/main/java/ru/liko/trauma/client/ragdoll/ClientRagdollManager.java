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
     * Resolve a player's skin texture from a UUID via the network's
     * {@link net.minecraft.client.multiplayer.PlayerInfo}.
     * <p>
     * PlayerInfo persists for every connected player regardless of whether their
     * entity is currently within view distance, so this lookup succeeds even when
     * the player is too far to be tracked by the client world (which is the
     * common case for a death ragdoll viewed by a distant killer).
     */
    @Nullable
    public static ResourceLocation resolveSkinFromUUID(UUID uuid) {
        if (uuid == null) return null;
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.multiplayer.ClientPacketListener conn = mc.getConnection();
        if (conn != null) {
            net.minecraft.client.multiplayer.PlayerInfo info = conn.getPlayerInfo(uuid);
            if (info != null) {
                return info.getSkin().texture();
            }
        }
        // Fallback: scan loaded entities (only works when the player is in view distance)
        if (mc.level != null) {
            for (net.minecraft.world.entity.player.Player p : mc.level.players()) {
                if (p.getUUID().equals(uuid) && p instanceof net.minecraft.client.player.AbstractClientPlayer acp) {
                    return acp.getSkin().texture();
                }
            }
        }
        return null;
    }

    private static void tryResolveSkinFromUUID(ClientRagdoll rag, UUID uuid) {
        ResourceLocation skin = resolveSkinFromUUID(uuid);
        if (skin != null) rag.cacheSkinTexture(skin);
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

        // ---------------------------------------------------------------
        // Delay-based snapshot buffer.
        //
        // The server runs physics and sends transforms every tick (~50 ms).
        // Instead of interpolating from "last received" to "current received"
        // using adaptive client-side timing (which differs per client), we
        // store a ring-buffer of (serverTime, transforms) snapshots and
        // always display the state at a fixed lag behind the latest snapshot.
        //
        // Result: every client renders the SAME server timestamp, so all
        // observers see identical positions regardless of their network delay.
        // ---------------------------------------------------------------
        private static final int  BUFFER_SIZE      = 12;  // ~600 ms history at 20 TPS
        private static final long DISPLAY_DELAY_MS = 160; // ~3 ticks behind latest — absorbs jitter

        private final long[][]           bufferTimes  = new long[BUFFER_SIZE][1];
        private final RagdollTransform[][] bufferFrames = new RagdollTransform[BUFFER_SIZE][6];
        private int  bufWriteIdx  = 0;
        private int  bufCount     = 0;

        // Cached skin texture for rendering after entity removal (death ragdoll orphaning)
        private ResourceLocation cachedSkinTexture = null;

        // UUID of the original player (used to find skin for orphaned death ragdolls)
        @Nullable
        private UUID cachedPlayerUUID = null;

        // Last client-side receive time — used only for stale-detection
        private long receiveMs = 0;

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
            // Build a full 6-part frame, carrying forward missing parts from the previous frame
            RagdollTransform[] frame = new RagdollTransform[6];
            if (bufCount > 0) {
                int prevIdx = (bufWriteIdx - 1 + BUFFER_SIZE) % BUFFER_SIZE;
                System.arraycopy(bufferFrames[prevIdx], 0, frame, 0, 6);
            }
            for (RagdollTransform t : transforms) {
                if (t == null) continue;
                int idx = t.partId;
                if (idx >= 0 && idx < 6) frame[idx] = t;
            }

            bufferTimes[bufWriteIdx][0]  = serverTime;
            bufferFrames[bufWriteIdx]    = frame;
            bufWriteIdx  = (bufWriteIdx + 1) % BUFFER_SIZE;
            bufCount     = Math.min(bufCount + 1, BUFFER_SIZE);

            receiveMs = System.currentTimeMillis();

            // Capture initial transforms for blend-in on the very first update
            if (startTimeMs == 0) {
                startTimeMs      = receiveMs;
                initialTransforms = new RagdollTransform[6];
                System.arraycopy(frame, 0, initialTransforms, 0, 6);
            }
        }

        /**
         * Return the interpolated transform for {@code part} at the delay-adjusted
         * display time. All clients compute the same target server-timestamp, so the
         * rendered position is identical across observers regardless of their ping.
         *
         * <p>Position uses a <b>Cubic Hermite spline</b> with the stored velocity as
         * tangent, giving smooth curved trajectories instead of piecewise linear
         * motion. Rotation still uses slerp (quaternion spherical-linear).
         *
         * <p>When the target time is ahead of the latest snapshot (packet loss),
         * the method <b>extrapolates</b> using the last known velocity for up to
         * 200 ms to hide the stutter.
         */
        @Nullable
        public RagdollTransform getPartInterpolated(RagdollPart part, float partialTicks) {
            if (bufCount == 0) return null;

            int latestIdx  = (bufWriteIdx - 1 + BUFFER_SIZE) % BUFFER_SIZE;
            long latestTime = bufferTimes[latestIdx][0];
            long targetTime = latestTime - DISPLAY_DELAY_MS;

            // Walk buffer newest → oldest to find frames that bracket targetTime.
            int afterIdx  = -1; // first frame with serverTime >= targetTime
            int beforeIdx = -1; // last  frame with serverTime <= targetTime
            for (int i = 0; i < bufCount; i++) {
                int idx = (bufWriteIdx - 1 - i + BUFFER_SIZE * 2) % BUFFER_SIZE;
                long t  = bufferTimes[idx][0];
                if (t >= targetTime) afterIdx  = idx;
                if (t <= targetTime) { beforeIdx = idx; break; }
            }

            int partIdx = part.index;
            RagdollTransform result;

            if (beforeIdx == -1 && afterIdx == -1) {
                return null;
            } else if (beforeIdx == -1) {
                // Target is before all stored frames — show oldest available
                result = bufferFrames[afterIdx][partIdx];
            } else if (afterIdx == -1 || afterIdx == beforeIdx) {
                // Target is at or past the latest snapshot.
                // Extrapolate using velocity to hide packet loss (max 200 ms).
                RagdollTransform latest = bufferFrames[latestIdx][partIdx];
                if (latest == null) return null;
                float extraSec = Math.min((targetTime - latestTime) / 1000f, 0.2f);
                if (extraSec > 0f) {
                    result = new RagdollTransform(partIdx,
                            latest.position.x + latest.velocity.x * extraSec,
                            latest.position.y + latest.velocity.y * extraSec,
                            latest.position.z + latest.velocity.z * extraSec,
                            latest.rotation.x, latest.rotation.y,
                            latest.rotation.z, latest.rotation.w,
                            latest.velocity.x, latest.velocity.y, latest.velocity.z);
                } else {
                    result = latest;
                }
            } else {
                // Cubic Hermite interpolation between beforeIdx and afterIdx.
                RagdollTransform before = bufferFrames[beforeIdx][partIdx];
                RagdollTransform after  = bufferFrames[afterIdx][partIdx];
                if (before == null && after == null) return null;
                if (before == null) { result = after; }
                else if (after == null) { result = before; }
                else {
                    long tBefore = bufferTimes[beforeIdx][0];
                    long tAfter  = bufferTimes[afterIdx][0];
                    float alpha = (tAfter == tBefore) ? 1f
                            : (float) (targetTime - tBefore) / (float) (tAfter - tBefore);
                    alpha = Math.max(0f, Math.min(1f, alpha));

                    // dt (seconds) scales the velocity tangents to match position units
                    float dtSec = (tAfter - tBefore) / 1000f;

                    // Hermite basis functions
                    float a2  = alpha * alpha;
                    float a3  = a2 * alpha;
                    float h00 =  2f * a3 - 3f * a2 + 1f;
                    float h10 =       a3 - 2f * a2 + alpha;
                    float h01 = -2f * a3 + 3f * a2;
                    float h11 =       a3 -       a2;

                    // Tangents = velocity × dt (Catmull-Rom style)
                    float m0x = before.velocity.x * dtSec, m0y = before.velocity.y * dtSec, m0z = before.velocity.z * dtSec;
                    float m1x = after.velocity.x  * dtSec, m1y = after.velocity.y  * dtSec, m1z = after.velocity.z  * dtSec;

                    float x = h00 * before.position.x + h10 * m0x + h01 * after.position.x + h11 * m1x;
                    float y = h00 * before.position.y + h10 * m0y + h01 * after.position.y + h11 * m1y;
                    float z = h00 * before.position.z + h10 * m0z + h01 * after.position.z + h11 * m1z;

                    float[] q = slerp(
                            before.rotation.x, before.rotation.y, before.rotation.z, before.rotation.w,
                            after.rotation.x,  after.rotation.y,  after.rotation.z,  after.rotation.w,
                            alpha);
                    result = new RagdollTransform(partIdx, x, y, z, q[0], q[1], q[2], q[3],
                            after.velocity.x, after.velocity.y, after.velocity.z);
                }
            }

            return applyBlend(result, partIdx);
        }

        public Map<RagdollPart, RagdollTransform> getAllPartsInterpolated(float partialTicks) {
            Map<RagdollPart, RagdollTransform> map = new EnumMap<>(RagdollPart.class);
            for (RagdollPart part : RagdollPart.values()) {
                map.put(part, getPartInterpolated(part, partialTicks));
            }
            return map;
        }

        /**
         * Maximum time in milliseconds without receiving an update before a ragdoll
         * is considered stale and treated as inactive. This acts as a safety net for
         * cases where a RagdollEndPayload is missed (e.g. dimension change race).
         * Server sends updates every tick (~50 ms), so 5 seconds = ~100 missed ticks.
         */
        private static final long STALE_TIMEOUT_MS = 5_000L;

        public boolean isActive() {
            if (receiveMs > 0 && System.currentTimeMillis() - receiveMs > STALE_TIMEOUT_MS) {
                return false;
            }
            if (bufCount == 0) return false;
            int latestIdx = (bufWriteIdx - 1 + BUFFER_SIZE) % BUFFER_SIZE;
            for (RagdollTransform t : bufferFrames[latestIdx]) {
                if (t != null) return true;
            }
            return false;
        }

        /** Returns true if this ragdoll has ever received a server update (not freshly created). */
        public boolean hasReceivedUpdate() {
            return receiveMs > 0;
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
                float rx = x1 + t * (x2 - x1);
                float ry = y1 + t * (y2 - y1);
                float rz = z1 + t * (z2 - z1);
                float rw = w1 + t * (w2 - w1);
                float m = (float) Math.sqrt(rx * rx + ry * ry + rz * rz + rw * rw);
                if (m < 0.0001f) return new float[]{x2, y2, z2, w2};
                return new float[]{rx / m, ry / m, rz / m, rw / m};
            }

            double theta0 = Math.acos(dot);
            double theta  = theta0 * t;
            double sinTheta  = Math.sin(theta);
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
