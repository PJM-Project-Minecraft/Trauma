package ru.liko.trauma.client.input;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import ru.liko.trauma.Trauma;
import ru.liko.trauma.client.ragdoll.ClientRagdollManager;

@EventBusSubscriber(modid = Trauma.MODID, value = Dist.CLIENT)
public class ClientInputHandler {

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        if (ClientRagdollManager.isLocalPlayerRagdolled()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }
}
