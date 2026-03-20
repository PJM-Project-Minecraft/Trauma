package ru.liko.trauma.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.trauma.Trauma;

import java.util.UUID;

public record DislocationTryPacket(UUID targetUUID, float dislocationValue) implements CustomPacketPayload {

    public static final Type<DislocationTryPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Trauma.MODID, "dislocation_try"));

    public static final StreamCodec<FriendlyByteBuf, DislocationTryPacket> STREAM_CODEC = StreamCodec.composite(
            net.minecraft.core.UUIDUtil.STREAM_CODEC, DislocationTryPacket::targetUUID,
            ByteBufCodecs.FLOAT, DislocationTryPacket::dislocationValue,
            DislocationTryPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
