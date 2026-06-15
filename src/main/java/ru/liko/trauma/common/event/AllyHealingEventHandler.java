package ru.liko.trauma.common.event;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import ru.liko.trauma.Config;
import ru.liko.trauma.Trauma;
import ru.liko.trauma.common.item.MedicalItem;

@EventBusSubscriber(modid = Trauma.MODID)
public class AllyHealingEventHandler {

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.isCanceled()) {
            return;
        }
        if (!(event.getTarget() instanceof Player patient)) {
            return;
        }

        Player healer = event.getEntity();
        if (patient == healer) {
            return;
        }

        ItemStack heldItem = healer.getMainHandItem();
        if (!(heldItem.getItem() instanceof MedicalItem medicalItem)) {
            return;
        }

        if (healer.level().isClientSide()) {
            return;
        }

        double maxDistance = Config.MEDICAL_MAX_DISTANCE.get();
        if (healer.distanceTo(patient) > maxDistance) {
            healer.displayClientMessage(Component.translatable("message.trauma.too_far"), true);
            event.setCanceled(true);
            return;
        }

        if (healer.getCooldowns().isOnCooldown(heldItem.getItem())) {
            event.setCanceled(true);
            return;
        }

        if (!medicalItem.canApplyTo(patient)) {
            return;
        }

        if (!medicalItem.applyTo(patient)) {
            return;
        }

        event.setCanceled(true);

        var medicalItemForCooldown = heldItem.getItem();
        if (!healer.isCreative()) {
            heldItem.shrink(1);
        }
        healer.getCooldowns().addCooldown(medicalItemForCooldown, Config.MEDICAL_COOLDOWN.get());

        if (healer.isUsingItem()) {
            return;
        }
        ItemStack main = healer.getMainHandItem();
        if (!main.isEmpty() && main.getItem() instanceof MedicalItem) {
            MedicalItem.markAllyHealAnimation(main);
            healer.startUsingItem(InteractionHand.MAIN_HAND);
        } else {
            healer.swing(InteractionHand.MAIN_HAND, true);
        }
    }
}
