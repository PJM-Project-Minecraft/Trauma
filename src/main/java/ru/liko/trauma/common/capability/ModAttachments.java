package ru.liko.trauma.common.capability;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import ru.liko.trauma.Trauma;
import ru.liko.trauma.common.system.TraumaData;

import java.util.function.Supplier;

public class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister
            .create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Trauma.MODID);

    public static final Supplier<AttachmentType<TraumaData>> TRAUMA_DATA = ATTACHMENT_TYPES.register(
            "trauma_data",
            () -> AttachmentType.builder(() -> TraumaData.createDefault())
                    .serialize(TraumaData.CODEC)
                    .build());
}
