package ru.liko.trauma.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.trauma.Trauma;

public record MedicalMinigameStartPayload(int targetId, int medicalTypeOrdinal) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MedicalMinigameStartPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(Trauma.MODID, "medical_minigame_start"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MedicalMinigameStartPayload> STREAM_CODEC = CustomPacketPayload
            .codec(
                    MedicalMinigameStartPayload::write, MedicalMinigameStartPayload::new);

    public MedicalMinigameStartPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readInt(), buffer.readInt());
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeInt(this.targetId);
        buffer.writeInt(this.medicalTypeOrdinal);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
