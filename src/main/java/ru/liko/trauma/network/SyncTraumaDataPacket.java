package ru.liko.trauma.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.trauma.Trauma;

public record SyncTraumaDataPacket(float bloodVolume, int bleedStrength,
        float blurIntensity, float legFracture, boolean hasSplint, float legDislocation)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncTraumaDataPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(Trauma.MODID, "sync_trauma_data"));

    public static final StreamCodec<FriendlyByteBuf, SyncTraumaDataPacket> STREAM_CODEC = StreamCodec.ofMember(
            SyncTraumaDataPacket::write,
            SyncTraumaDataPacket::new);

    public SyncTraumaDataPacket(FriendlyByteBuf buf) {
        this(buf.readFloat(), buf.readInt(), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readFloat());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeFloat(bloodVolume);
        buf.writeInt(bleedStrength);
        buf.writeFloat(blurIntensity);
        buf.writeFloat(legFracture);
        buf.writeBoolean(hasSplint);
        buf.writeFloat(legDislocation);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
