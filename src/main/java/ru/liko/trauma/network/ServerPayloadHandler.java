package ru.liko.trauma.network;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ru.liko.trauma.common.event.PlayerTickEventHandler;

public class ServerPayloadHandler {

    private static final ServerPayloadHandler INSTANCE = new ServerPayloadHandler();

    public static ServerPayloadHandler getInstance() {
        return INSTANCE;
    }

    public void handleSuppressBleeding(final SuppressBleedingPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = (Player) context.player();
            if (player != null) {
                if (packet.isSuppressing()) {
                    PlayerTickEventHandler.SUPPRESSION_MAP.put(player.getUUID(), true);
                } else {
                    PlayerTickEventHandler.SUPPRESSION_MAP.remove(player.getUUID());
                }
            }
        });
    }

    public void handleMinigameResult(final MedicalMinigameResultPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = (Player) context.player();
            if (player != null) {
                ru.liko.trauma.common.system.MinigameManager.handleMinigameResult(player, payload.success());
            }
        });
    }

    public void handleDislocationTry(final DislocationTryPacket payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = (Player) context.player();
            if (player != null && player.getServer() != null) {
                Player target = player.getServer().getPlayerList().getPlayer(payload.targetUUID());
                if (target != null) {
                    var data = target.getData(ru.liko.trauma.common.capability.ModAttachments.TRAUMA_DATA);
                    if (data != null) {
                        target.setData(ru.liko.trauma.common.capability.ModAttachments.TRAUMA_DATA, data.withLegDislocation(payload.dislocationValue()));
                        ru.liko.trauma.common.event.SyncEventHandler.syncData(target);
                    }
                }
            }
        });
    }
}
