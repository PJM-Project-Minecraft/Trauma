package ru.liko.trauma.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.liko.trauma.Trauma;

public record MedicalMinigameResultPayload(boolean success) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MedicalMinigameResultPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(Trauma.MODID, "medical_minigame_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MedicalMinigameResultPayload> STREAM_CODEC = CustomPacketPayload
            .codec(
                    MedicalMinigameResultPayload::write, MedicalMinigameResultPayload::new);

    public MedicalMinigameResultPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBoolean());
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(this.success);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
