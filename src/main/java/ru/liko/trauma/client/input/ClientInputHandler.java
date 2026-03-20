package ru.liko.trauma.client.input;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.liko.trauma.Trauma;
import ru.liko.trauma.network.SuppressBleedingPacket;
import ru.liko.trauma.common.system.TraumaData;
import ru.liko.trauma.common.capability.ModAttachments;

import net.neoforged.neoforge.client.event.InputEvent;
import ru.liko.trauma.client.ragdoll.ClientRagdollManager;

@EventBusSubscriber(modid = Trauma.MODID, value = Dist.CLIENT)
public class ClientInputHandler {

    private static boolean wasSuppressing = false;

    public static boolean isSuppressing() {
        return wasSuppressing;
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;

        // Block interactions during ragdoll (also handled by ClientRagdollEventHandler,
        // but double-safe)
        if (ClientRagdollManager.isLocalPlayerRagdolled()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }

        if (KeyBindings.SUPPRESS_KEY.isDown()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;

        // Handle fix dislocation key (direct bind)
        while (KeyBindings.FIX_DISLOCATION_KEY.consumeClick()) {
            TraumaData data = mc.player.getData(ModAttachments.TRAUMA_DATA);
            if (data.legDislocation() > 0) {
                mc.setScreen(new ru.liko.trauma.client.gui.minigames.DislocationMinigameScreen(null, mc.player));
            }
        }

        // Don't process suppression during ragdoll
        if (ClientRagdollManager.isLocalPlayerRagdolled()) {
            if (wasSuppressing) {
                wasSuppressing = false;
                PacketDistributor.sendToServer(new SuppressBleedingPacket(false));
            }
            return;
        }

        // Suppress bleeding if holding the dedicated key
        boolean isHoldingKey = KeyBindings.SUPPRESS_KEY.isDown();

        TraumaData data = mc.player.getData(ModAttachments.TRAUMA_DATA);

        boolean shouldSuppress = isHoldingKey && data.bleedStrength() > 0;

        if (shouldSuppress != wasSuppressing) {
            wasSuppressing = shouldSuppress;
            PacketDistributor.sendToServer(new SuppressBleedingPacket(shouldSuppress));
        }
    }
}
