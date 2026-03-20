package ru.liko.trauma.common.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.liko.trauma.Trauma;
import ru.liko.trauma.common.capability.ModAttachments;
import ru.liko.trauma.common.system.TraumaData;
import ru.liko.trauma.network.SyncTraumaDataPacket;

@EventBusSubscriber(modid = Trauma.MODID)
public class SyncEventHandler {

    private static final ResourceLocation HEALTH_PENALTY_ID = ResourceLocation.fromNamespaceAndPath(Trauma.MODID,
            "blood_loss_health_penalty");

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        syncData(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            // Death should fully reset trauma state on respawn
            event.getEntity().setData(ModAttachments.TRAUMA_DATA, TraumaData.createDefault());
            // Clear suppression state on server for this player
            PlayerTickEventHandler.SUPPRESSION_MAP.remove(event.getEntity().getUUID());
            // Remove max health penalty attribute - it will be reapplied by the tick handler
            AttributeInstance maxHealthAttr = event.getEntity().getAttribute(Attributes.MAX_HEALTH);
            if (maxHealthAttr != null) {
                maxHealthAttr.removeModifier(HEALTH_PENALTY_ID);
            }
        } else {
            // Preserve trauma data for non-death clone events (e.g. End return)
            TraumaData oldData = event.getOriginal().getData(ModAttachments.TRAUMA_DATA);
            event.getEntity().setData(ModAttachments.TRAUMA_DATA, oldData);
        }
        syncData(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        syncData(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // Clean up suppression map when player disconnects
        PlayerTickEventHandler.SUPPRESSION_MAP.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        syncData(event.getEntity());
    }

    public static void syncData(net.minecraft.world.entity.LivingEntity entity) {
        if (!entity.level().isClientSide() && entity instanceof ServerPlayer serverPlayer) {
            TraumaData data = entity.getData(ModAttachments.TRAUMA_DATA);
            PacketDistributor.sendToPlayer(serverPlayer,
                    new SyncTraumaDataPacket(data.bloodVolume(), data.bleedStrength(),
                            data.blurIntensity(), data.legFracture(), data.hasSplint(), data.legDislocation()));
        }
    }
}
