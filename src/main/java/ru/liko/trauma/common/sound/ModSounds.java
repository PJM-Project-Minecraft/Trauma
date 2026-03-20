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

        public static final Supplier<SoundEvent> BLOOD_SPATTER = SOUNDS.register("blood_spatter",
                        () -> SoundEvent
                                        .createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Trauma.MODID,
                                                        "blood_spatter")));

        public static final Supplier<SoundEvent> BROKEN_BONE = SOUNDS.register("broken_bone",
                        () -> SoundEvent
                                        .createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Trauma.MODID,
                                                        "broken_bone")));
}
