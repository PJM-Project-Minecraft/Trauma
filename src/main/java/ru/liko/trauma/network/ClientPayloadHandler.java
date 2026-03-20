package ru.liko.trauma.network;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ru.liko.trauma.client.ragdoll.ClientRagdollManager;
import ru.liko.trauma.common.capability.ModAttachments;
import ru.liko.trauma.common.system.TraumaData;

public class ClientPayloadHandler {

    private static final ClientPayloadHandler INSTANCE = new ClientPayloadHandler();

    public static ClientPayloadHandler getInstance() {
        return INSTANCE;
    }

    public void handleDataSync(final SyncTraumaDataPacket data, final IPayloadContext context) {
        // Enqueue the packet operation on the main client thread
        context.enqueueWork(() -> {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                TraumaData localData = player.getData(ModAttachments.TRAUMA_DATA);
                TraumaData updatedData = localData.withBlood(data.bloodVolume()).withBleedStrength(data.bleedStrength())
                        .withBlur(data.blurIntensity())
                        .withLegFracture(data.legFracture())
                        .withSplint(data.hasSplint())
                        .withLegDislocation(data.legDislocation());
                player.setData(ModAttachments.TRAUMA_DATA, updatedData);
            }
        });
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

    public void handleMinigameStart(final MedicalMinigameStartPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft.getInstance().setScreen(new ru.liko.trauma.client.gui.MedicalMinigameScreen(payload.targetId()));
        });
    }
}
