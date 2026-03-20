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

        registrar.playToServer(
                SuppressBleedingPacket.TYPE,
                SuppressBleedingPacket.STREAM_CODEC,
                ServerPayloadHandler.getInstance()::handleSuppressBleeding);

        registrar.playToServer(
                MedicalMinigameResultPayload.TYPE,
                MedicalMinigameResultPayload.STREAM_CODEC,
                ServerPayloadHandler.getInstance()::handleMinigameResult);

        registrar.playToServer(
                DislocationTryPacket.TYPE,
                DislocationTryPacket.STREAM_CODEC,
                ServerPayloadHandler.getInstance()::handleDislocationTry);

        registrar.playToClient(
                SyncTraumaDataPacket.TYPE,
                SyncTraumaDataPacket.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.getInstance().handleDataSync(payload, context));

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

        registrar.playToClient(
                MedicalMinigameStartPayload.TYPE,
                MedicalMinigameStartPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.getInstance().handleMinigameStart(payload, context));
    }
}
