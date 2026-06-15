package ru.liko.trauma.client.ragdoll;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import ru.liko.trauma.Trauma;

/**
 * Client-side event handler for ragdoll state management.
 * 
 * Responsibilities:
 * - Block all player input (movement, interactions, jumping) when the local
 * player is ragdolled
 * - Clean up all client-side ragdolls on disconnect
 * - Prevent inventory/menu opening during ragdoll
 * 
 * This works in tandem with server-side protections in PhysicsHooks:
 * - Server: teleports player, zeros velocity, cancels actions via events
 * - Client: blocks input to prevent useless packet spam and improve UX
 */
@EventBusSubscriber(modid = Trauma.MODID, value = Dist.CLIENT)
public class ClientRagdollEventHandler {

    /**
     * Block all movement input when the local player is ragdolled.
     * This prevents the client from sending movement packets that the server would
     * reject anyway.
     */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!ClientRagdollManager.isLocalPlayerRagdolled())
            return;

        var input = event.getInput();
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    /**
     * Block all interactions (attack, use item, pick block) when the local player
     * is ragdolled.
     */
    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (ClientRagdollManager.isLocalPlayerRagdolled()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /**
     * Clear all client-side ragdolls when disconnecting from a server.
     * Prevents stale ragdoll data from persisting across server connections.
     */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientRagdollManager.clear();
    }

    /**
     * Clear local player ragdoll state when the player entity is recreated (e.g.
     * respawn).
     * This acts as a fallback in case the server's RagdollEndPayload is missed or
     * delayed.
     */
    @SubscribeEvent
    public static void onPlayerClone(ClientPlayerNetworkEvent.Clone event) {
        if (event.getOldPlayer() != null) {
            ClientRagdollManager.remove(event.getOldPlayer().getId());
        }
        if (event.getNewPlayer() != null) {
            ClientRagdollManager.remove(event.getNewPlayer().getId());
        }
    }
}
