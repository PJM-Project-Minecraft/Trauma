package ru.liko.trauma.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.trauma.Trauma;
import ru.liko.trauma.ragdoll.RagdollTransform;

/**
 * Server -> Client: Periodic update of ragdoll body-part transforms.
 * Sent every server tick while a ragdoll is active.
 */
public record RagdollUpdatePayload(int ragdollId, long timestamp, RagdollTransform[] transforms)
        implements CustomPacketPayload {

    public static final Type<RagdollUpdatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Trauma.MODID, "ragdoll_update"));

    public static final StreamCodec<FriendlyByteBuf, RagdollUpdatePayload> STREAM_CODEC =
            StreamCodec.ofMember(RagdollUpdatePayload::write, RagdollUpdatePayload::read);

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(ragdollId);
        buf.writeLong(timestamp);
        buf.writeInt(transforms.length);
        for (RagdollTransform t : transforms) {
            t.writeTo(buf);
        }
    }

    public static RagdollUpdatePayload read(FriendlyByteBuf buf) {
        int ragdollId = buf.readInt();
        long timestamp = buf.readLong();
        int len = buf.readInt();
        RagdollTransform[] transforms = new RagdollTransform[len];
        for (int i = 0; i < len; i++) {
            transforms[i] = RagdollTransform.readFrom(buf);
        }
        return new RagdollUpdatePayload(ragdollId, timestamp, transforms);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
