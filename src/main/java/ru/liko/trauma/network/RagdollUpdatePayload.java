package ru.liko.trauma.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.trauma.Trauma;
import ru.liko.trauma.ragdoll.RagdollTransform;

/**
 * Server → Client: Periodic update of ragdoll body-part transforms.
 * Sent every server tick while a ragdoll is active.
 *
 * <h3>Compact wire format (≈ 172 bytes vs 280 bytes naïve)</h3>
 * <pre>
 * Header:  ragdollId (int 4B) | timestamp (long 8B) | count (byte 1B)
 * Per transform (6 parts × 26 bytes each):
 *   partId    byte   1 B
 *   pos.x/y/z float  12 B  (world-space, kept as float32)
 *   rotation  7 B   (smallest-three: index byte + 3 signed shorts)
 *   velocity  6 B   (3 signed shorts, 1/100 scale)
 * </pre>
 *
 * <h3>Smallest-three quaternion encoding</h3>
 * Given unit quaternion q = (x, y, z, w):
 * <ol>
 *   <li>Find index of the component with the largest absolute value.</li>
 *   <li>Flip all signs if that component is negative (q and –q are identical rotations).</li>
 *   <li>Encode the remaining three components as signed shorts scaled by
 *       {@code 32767 / (√2/2) ≈ 46341} — valid because non-max components
 *       of a unit quaternion are always ≤ √2/2.</li>
 *   <li>Reconstruct: dropped component = √(1 − a² − b² − c²), always positive.</li>
 * </ol>
 */
public record RagdollUpdatePayload(int ragdollId, long timestamp, RagdollTransform[] transforms)
        implements CustomPacketPayload {

    public static final Type<RagdollUpdatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Trauma.MODID, "ragdoll_update"));

    public static final StreamCodec<FriendlyByteBuf, RagdollUpdatePayload> STREAM_CODEC =
            StreamCodec.ofMember(RagdollUpdatePayload::write, RagdollUpdatePayload::read);

    // ---------------------------------------------------------------
    // Codec constants
    // ---------------------------------------------------------------

    /** Quaternion short scale: √2/2 maps to Short.MAX_VALUE. */
    private static final float QUAT_ENCODE = 32767f / 0.70710678f;
    private static final float QUAT_DECODE = 0.70710678f / 32767f;

    /**
     * Velocity short scale: 1 unit/s → 100 integer units.
     * Max representable velocity ≈ ±327 blocks/s (physics is clamped to ±90).
     */
    private static final float VEL_ENCODE = 100f;
    private static final float VEL_DECODE = 1f / 100f;

    // ---------------------------------------------------------------
    // Wire read / write
    // ---------------------------------------------------------------

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(ragdollId);
        buf.writeLong(timestamp);
        buf.writeByte(transforms.length);
        for (RagdollTransform t : transforms) {
            writeTransform(buf, t);
        }
    }

    public static RagdollUpdatePayload read(FriendlyByteBuf buf) {
        int ragdollId  = buf.readInt();
        long timestamp = buf.readLong();
        int len = buf.readByte() & 0xFF;
        RagdollTransform[] transforms = new RagdollTransform[len];
        for (int i = 0; i < len; i++) {
            transforms[i] = readTransform(buf);
        }
        return new RagdollUpdatePayload(ragdollId, timestamp, transforms);
    }

    // ---------------------------------------------------------------
    // Per-transform encoding helpers
    // ---------------------------------------------------------------

    private static void writeTransform(FriendlyByteBuf buf, RagdollTransform t) {
        // partId: byte (0-5)
        buf.writeByte(t.partId);
        // position: 3 × float32
        buf.writeFloat(t.position.x);
        buf.writeFloat(t.position.y);
        buf.writeFloat(t.position.z);
        // rotation: smallest-three (7 bytes)
        writeCompactQuat(buf, t.rotation.x, t.rotation.y, t.rotation.z, t.rotation.w);
        // velocity: 3 × short (6 bytes)
        writeCompactVel(buf, t.velocity.x, t.velocity.y, t.velocity.z);
    }

    private static RagdollTransform readTransform(FriendlyByteBuf buf) {
        int partId = buf.readByte() & 0xFF;
        float px = buf.readFloat(), py = buf.readFloat(), pz = buf.readFloat();
        float[] q = readCompactQuat(buf);
        float[] v = readCompactVel(buf);
        return new RagdollTransform(partId, px, py, pz, q[0], q[1], q[2], q[3], v[0], v[1], v[2]);
    }

    // ---------------------------------------------------------------
    // Smallest-three quaternion codec
    // ---------------------------------------------------------------

    private static void writeCompactQuat(FriendlyByteBuf buf, float qx, float qy, float qz, float qw) {
        // Normalise to be safe against floating-point drift
        float len = (float) Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
        if (len > 0.0001f) { qx /= len; qy /= len; qz /= len; qw /= len; }

        // Find the component with the largest absolute value
        float ax = Math.abs(qx), ay = Math.abs(qy), az = Math.abs(qz), aw = Math.abs(qw);
        int   maxIdx;
        if      (ax >= ay && ax >= az && ax >= aw) maxIdx = 0;
        else if (ay >= az && ay >= aw)             maxIdx = 1;
        else if (az >= aw)                         maxIdx = 2;
        else                                       maxIdx = 3;

        // Canonical form: ensure the dropped component is always positive
        float maxVal = (maxIdx == 0) ? qx : (maxIdx == 1) ? qy : (maxIdx == 2) ? qz : qw;
        if (maxVal < 0f) { qx = -qx; qy = -qy; qz = -qz; qw = -qw; }

        float[] c = { qx, qy, qz, qw };

        buf.writeByte(maxIdx); // 1 byte — which component was dropped
        for (int i = 0; i < 4; i++) {
            if (i != maxIdx) {
                buf.writeShort(clampShort(c[i] * QUAT_ENCODE)); // 2 bytes each, 3 written = 6 bytes
            }
        }
    }

    private static float[] readCompactQuat(FriendlyByteBuf buf) {
        int maxIdx = buf.readByte() & 0x03; // only 2 bits meaningful
        float[] c = new float[4];
        for (int i = 0; i < 4; i++) {
            if (i != maxIdx) {
                c[i] = buf.readShort() * QUAT_DECODE;
            }
        }
        // Reconstruct dropped component (always positive in canonical form)
        float sumSq = c[0] * c[0] + c[1] * c[1] + c[2] * c[2] + c[3] * c[3];
        c[maxIdx] = (float) Math.sqrt(Math.max(0f, 1f - sumSq));
        return c; // [qx, qy, qz, qw]
    }

    // ---------------------------------------------------------------
    // Compact velocity codec
    // ---------------------------------------------------------------

    private static void writeCompactVel(FriendlyByteBuf buf, float vx, float vy, float vz) {
        buf.writeShort(clampShort(vx * VEL_ENCODE));
        buf.writeShort(clampShort(vy * VEL_ENCODE));
        buf.writeShort(clampShort(vz * VEL_ENCODE));
    }

    private static float[] readCompactVel(FriendlyByteBuf buf) {
        return new float[] {
                buf.readShort() * VEL_DECODE,
                buf.readShort() * VEL_DECODE,
                buf.readShort() * VEL_DECODE
        };
    }

    // ---------------------------------------------------------------
    // Utility
    // ---------------------------------------------------------------

    private static short clampShort(float v) {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(v)));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
