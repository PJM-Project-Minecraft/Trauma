package ru.liko.trauma.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import ru.liko.trauma.Trauma;

@EventBusSubscriber(modid = Trauma.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ModNetworking {

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(Trauma.MODID).versioned("1.0");

        registrar.playToClient(
                RagdollStartPayload.TYPE,
                RagdollStartPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.getInstance().handleRagdollStart(payload, context));

        registrar.playToClient(
                RagdollUpdatePayload.TYPE,
                RagdollUpdatePayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.getInstance().handleRagdollUpdate(payload, context));

        registrar.playToClient(
                RagdollEndPayload.TYPE,
                RagdollEndPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.getInstance().handleRagdollEnd(payload, context));
    }
}
