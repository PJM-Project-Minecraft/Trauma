package ru.liko.trauma.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.trauma.Trauma;
import ru.liko.trauma.ragdoll.RagdollTransform;

/**
 * Server -> Client: Signals the start of a ragdoll for a player entity.
 * Contains the initial transforms for all 6 body parts.
 */
public record RagdollStartPayload(int playerEntityId, int ragdollId, java.util.UUID playerUUID, RagdollTransform[] transforms)
        implements CustomPacketPayload {

    public static final Type<RagdollStartPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Trauma.MODID, "ragdoll_start"));

    public static final StreamCodec<FriendlyByteBuf, RagdollStartPayload> STREAM_CODEC =
            StreamCodec.ofMember(RagdollStartPayload::write, RagdollStartPayload::read);

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(playerEntityId);
        buf.writeInt(ragdollId);
        buf.writeUUID(playerUUID);
        buf.writeInt(transforms.length);
        for (RagdollTransform t : transforms) {
            t.writeTo(buf);
        }
    }

    public static RagdollStartPayload read(FriendlyByteBuf buf) {
        int playerId = buf.readInt();
        int ragdollId = buf.readInt();
        java.util.UUID playerUUID = buf.readUUID();
        int len = buf.readInt();
        RagdollTransform[] transforms = new RagdollTransform[len];
        for (int i = 0; i < len; i++) {
            transforms[i] = RagdollTransform.readFrom(buf);
        }
        return new RagdollStartPayload(playerId, ragdollId, playerUUID, transforms);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
