package ru.liko.trauma.common.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import ru.liko.trauma.Trauma;
import ru.liko.trauma.common.item.MedicalItem;
import ru.liko.trauma.common.system.MinigameManager;

@EventBusSubscriber(modid = Trauma.MODID)
public class AllyHealingEventHandler {

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player healer = event.getEntity();
        Entity target = event.getTarget();

        if (!(target instanceof net.minecraft.world.entity.LivingEntity patient)) {
            return;
        }

        ItemStack heldItem = healer.getMainHandItem();
        if (!(heldItem.getItem() instanceof MedicalItem medicalItem)) {
            return;
        }

        if (healer.level().isClientSide()) {
            event.setCanceled(true);
            return;
        }

        double maxDistance = ru.liko.trauma.Config.MEDICAL_MAX_DISTANCE.get();
        if (healer.distanceTo(patient) > maxDistance) {
            healer.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.trauma.too_far"),
                    true);
            event.setCanceled(true);
            return;
        }

        if (healer.getCooldowns().isOnCooldown(heldItem.getItem())) {
            event.setCanceled(true);
            return;
        }

        if (medicalItem.getMedicalType() == MedicalItem.MedicalType.SPLINT) {
            // Splint is applied instantly, no minigame
            if (medicalItem.applyTo(patient)) {
                if (!healer.isCreative()) {
                    heldItem.shrink(1);
                }
                healer.getCooldowns().addCooldown(heldItem.getItem(), ru.liko.trauma.Config.MEDICAL_COOLDOWN.get());
            }
            event.setCanceled(true);
            return;
        }

        // Trigger minigame always for other items
        ru.liko.trauma.common.system.MinigameManager.startSession(healer, patient.getId(),
                medicalItem.getMedicalType());

        event.setCanceled(true);
    }
}
