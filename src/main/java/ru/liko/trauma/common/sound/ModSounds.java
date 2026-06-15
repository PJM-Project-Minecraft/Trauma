package ru.liko.trauma.common.sound;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.liko.trauma.Trauma;

import java.util.function.Supplier;

public class ModSounds {
        public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT,
                        Trauma.MODID);

        public static final Supplier<SoundEvent> HEARTBEAT_IN = SOUNDS.register("heartbeat_in",
                        () -> SoundEvent
                                        .createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Trauma.MODID,
                                                        "heartbeat_in")));

        public static final Supplier<SoundEvent> HEARTBEAT_OUT = SOUNDS.register("heartbeat_out",
                        () -> SoundEvent
                                        .createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Trauma.MODID,
                                                        "heartbeat_out")));
}
