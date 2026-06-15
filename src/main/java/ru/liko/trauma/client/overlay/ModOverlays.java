package ru.liko.trauma.client.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import ru.liko.trauma.Trauma;
import ru.liko.trauma.common.sound.ModSounds;

@EventBusSubscriber(modid = Trauma.MODID, value = Dist.CLIENT)
public class ModOverlays {

    public static long lastHeartbeatTime = 0;
    public static int heartbeatDelay = 40;
    public static boolean heartbeatPhase = false;

    @SubscribeEvent
    public static void onClientPlayerTick(PlayerTickEvent.Post event) {
        if (!event.getEntity().level().isClientSide()) {
            return;
        }
        Player player = event.getEntity();
        if (player != Minecraft.getInstance().player) {
            return;
        }

        float hp = player.getHealth();
        if (hp <= 6.0f && hp > 0) {
            float urgency = hp / 6.0f;
            heartbeatDelay = (int) (20 + urgency * 40);
            if (player.tickCount % heartbeatDelay == 0) {
                lastHeartbeatTime = System.currentTimeMillis();
                heartbeatPhase = !heartbeatPhase;
                net.minecraft.sounds.SoundEvent sound = heartbeatPhase ? ModSounds.HEARTBEAT_IN.get()
                        : ModSounds.HEARTBEAT_OUT.get();
                player.level().playLocalSound(player.getX(), player.getY(), player.getZ(),
                        sound, net.minecraft.sounds.SoundSource.PLAYERS,
                        1.0F, 1.0F, false);
            }
        }
    }
}
