package ru.liko.trauma.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.trauma.Trauma;

/**
 * Server -> Client: Signals the end of a ragdoll. Client should remove ragdoll visuals.
 */
public record RagdollEndPayload(int ragdollId) implements CustomPacketPayload {

    public static final Type<RagdollEndPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Trauma.MODID, "ragdoll_end"));

    public static final StreamCodec<FriendlyByteBuf, RagdollEndPayload> STREAM_CODEC =
            StreamCodec.ofMember(RagdollEndPayload::write, RagdollEndPayload::read);

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(ragdollId);
    }

    public static RagdollEndPayload read(FriendlyByteBuf buf) {
        return new RagdollEndPayload(buf.readInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
