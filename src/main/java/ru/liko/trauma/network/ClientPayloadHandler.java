package ru.liko.trauma.network;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ru.liko.trauma.client.ragdoll.ClientRagdollManager;

public class ClientPayloadHandler {

    private static final ClientPayloadHandler INSTANCE = new ClientPayloadHandler();

    public static ClientPayloadHandler getInstance() {
        return INSTANCE;
    }

    public void handleRagdollStart(final RagdollStartPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientRagdollManager.addOrUpdate(
                    payload.playerEntityId(),
                    payload.ragdollId(),
                    payload.playerUUID(),
                    payload.transforms(),
                    System.currentTimeMillis());
        });
    }

    public void handleRagdollUpdate(final RagdollUpdatePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientRagdollManager.addOrUpdate(
                    payload.ragdollId(),
                    payload.ragdollId(),
                    payload.transforms(),
                    payload.timestamp());
        });
    }

    public void handleRagdollEnd(final RagdollEndPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientRagdollManager.remove(payload.ragdollId());
        });
    }
}
