package ru.liko.trauma.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.trauma.Trauma;

public record SuppressBleedingPacket(boolean isSuppressing) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SuppressBleedingPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(Trauma.MODID, "suppress_bleeding"));

    public static final StreamCodec<FriendlyByteBuf, SuppressBleedingPacket> STREAM_CODEC = StreamCodec.composite(
            net.minecraft.network.codec.ByteBufCodecs.BOOL, SuppressBleedingPacket::isSuppressing,
            SuppressBleedingPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
