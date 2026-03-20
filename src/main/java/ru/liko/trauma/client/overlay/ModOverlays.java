package ru.liko.trauma.client.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import ru.liko.trauma.Config;
import ru.liko.trauma.Trauma;
import ru.liko.trauma.common.capability.ModAttachments;
import ru.liko.trauma.common.system.TraumaData;

@EventBusSubscriber(modid = Trauma.MODID, value = Dist.CLIENT)
public class ModOverlays {

    /**
     * Called from Trauma.ClientModEvents (MOD bus) to register GUI layers.
     * RegisterGuiLayersEvent fires on the MOD bus, not the GAME bus.
     */
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(Trauma.MODID, "trauma_hud"),
                (graphics, partialTick) -> {
                    Minecraft mc = Minecraft.getInstance();
                    Player player = mc.player;
                    if (player == null || mc.options.hideGui)
                        return;

                    Config.GuiPosition pos = Config.HUD_GUI_POSITION.get();
                    if (pos == Config.GuiPosition.DISABLED)
                        return;

                    TraumaData data = player.getData(ModAttachments.TRAUMA_DATA);
                    Font font = mc.font;
                    int screenWidth = mc.getWindow().getGuiScaledWidth();
                    int screenHeight = mc.getWindow().getGuiScaledHeight();

                    // --- SCUM STYLE HUD ---
                    int barW = 85;
                    int hpBarH = 11;
                    int bloodBarH = 4;

                    int x = pos == Config.GuiPosition.BOTTOM_LEFT ? 15 : (screenWidth - barW - 15);
                    int y = screenHeight - hpBarH - 3;

                    // Use a constant 20.0f for the absolute max health scaling, so max health
                    // reduction is visible
                    float hpPercent = Math.clamp(player.getHealth() / 20.0f, 0f, 1f);
                    float bloodPercent = Math.clamp(data.bloodVolume() / TraumaData.MAX_BLOOD, 0f, 1f);

                    // ==============================
                    // 1. HEALTH BAR (Small, bottom)
                    // ==============================
                    graphics.fill(x, y, x + barW, y + hpBarH, 0x66000000); // Dark translucent background

                    // HP Foreground (White/Grey like SCUM, turning red if very low)
                    int hpColor = hpPercent > 0.3f ? 0xFFE0E0E0 : 0xFFFF3333;
                    graphics.fill(x, y, x + (int) (barW * hpPercent), y + hpBarH, hpColor);

                    // Inside Text (HLTH 10 / 20)
                    String hpText = String.format("HLTH %.0f / 20", player.getHealth());
                    graphics.drawString(font, hpText, x + 3, y + 2, 0xFFFFFFFF, true);

                    // ==============================
                    // 2. BLOOD BAR (Even smaller, above HP)
                    // ==============================
                    int bloodY = y - bloodBarH - 1;
                    graphics.fill(x, bloodY, x + barW, bloodY + bloodBarH, 0x66000000); // Dark translucent background

                    int bloodColor = bloodPercent > 0.4f ? 0xFFCC0000 : 0xFFFF1111;
                    graphics.fill(x, bloodY, x + (int) (barW * bloodPercent), bloodY + bloodBarH, bloodColor); // Red
                                                                                                               // fill

                    // Text (ML) - positioned above the thin blood bar
                    String bloodText = String.format("%d ML", (int) data.bloodVolume());
                    graphics.drawString(font, bloodText, x + 3, bloodY - 9, 0xFFFFFFFF, true);

                    // Bleeding and Hints (Left-aligned above the blood bar and text)
                    if (data.bleedStrength() > 0) {
                        double baseDrain = 0;
                        switch (data.bleedStrength()) {
                            case 1:
                                baseDrain = ru.liko.trauma.Config.LIGHT_BLEED_DRAIN.get();
                                break;
                            case 2:
                                baseDrain = ru.liko.trauma.Config.HEAVY_BLEED_DRAIN.get();
                                break;
                            default:
                                baseDrain = ru.liko.trauma.Config.SEVERE_BLEED_DRAIN.get();
                                break;
                        }

                        double perSecond = baseDrain; // Config is already per second
                        if (ru.liko.trauma.client.input.ClientInputHandler.isSuppressing()) {
                            perSecond *= 0.5; // 50% reduction when suppressing
                        }

                        String bleedAmount = (perSecond == Math.floor(perSecond))
                                ? String.format(java.util.Locale.US, "%.0f", perSecond)
                                : String.format(java.util.Locale.US, "%.1f", perSecond);

                        Component bleedText = Component.translatable("gui.trauma.bleeding_hud", bleedAmount);
                        graphics.drawString(font, bleedText, x, bloodY - 20, 0xFFFF2222, true);

                        if (ru.liko.trauma.client.input.ClientInputHandler.isSuppressing()) {
                            Component suppressText = Component.translatable("gui.trauma.suppressing");
                            graphics.drawString(font, suppressText, x, bloodY - 30, 0xFF55FF55, true);
                        } else {
                            Component hintText = Component.translatable("gui.trauma.suppress_hint",
                                    ru.liko.trauma.client.input.KeyBindings.SUPPRESS_KEY.getTranslatedKeyMessage());
                            graphics.drawString(font, hintText, x, bloodY - 30, 0xAAAAAA, true);
                        }
                    }

                    // --- BROKEN LEG INDICATOR ---
                    if (data.legFracture() > 0) {
                        int yOffset = data.bleedStrength() > 0 ? 40 : 20;
                        Component brokenLegText = Component.translatable("gui.trauma.broken_leg");
                        graphics.drawString(font, brokenLegText, x, bloodY - yOffset, 0xFFFFAA00, true);
                        
                        if (data.hasSplint()) {
                            Component splintText = Component.translatable("gui.trauma.splint_applied");
                            graphics.drawString(font, splintText, x, bloodY - yOffset - 10, 0xFF55FF55, true);
                        }
                    } else if (data.legDislocation() > 0) {
                        int yOffset = data.bleedStrength() > 0 ? 40 : 20;
                        Component dislocatedLegText = Component.translatable("gui.trauma.dislocated_leg");
                        graphics.drawString(font, dislocatedLegText, x, bloodY - yOffset, 0xFFFFAA00, true);
                    }
                });
    }

    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        // Hide vanilla health if our custom HUD is enabled (overrides old config)
        if (event.getName().equals(VanillaGuiLayers.PLAYER_HEALTH)) {
            if (Config.HUD_GUI_POSITION.get() != Config.GuiPosition.DISABLED) {
                event.setCanceled(true);
            }
        }
    }

    // Global heartbeat state - used by ShaderHandler for synced blur pulse
    public static long lastHeartbeatTime = 0;
    public static int heartbeatDelay = 40;
    public static boolean heartbeatPhase = false;

    @SubscribeEvent
    public static void onClientPlayerTick(PlayerTickEvent.Post event) {
        if (!event.getEntity().level().isClientSide)
            return;
        Player player = event.getEntity();
        if (player != Minecraft.getInstance().player)
            return;

        // Heartbeat only at low HP (3 hearts or less)
        float hp = player.getHealth();
        if (hp <= 6.0f && hp > 0) {
            heartbeatDelay = (int) (20 + (hp / 6.0f) * 40); // Faster at lower HP
            if (player.tickCount % heartbeatDelay == 0) {
                lastHeartbeatTime = System.currentTimeMillis();
                heartbeatPhase = !heartbeatPhase;
                net.minecraft.sounds.SoundEvent sound = heartbeatPhase
                        ? ru.liko.trauma.common.sound.ModSounds.HEARTBEAT_IN.get()
                        : ru.liko.trauma.common.sound.ModSounds.HEARTBEAT_OUT.get();
                player.level().playLocalSound(player.getX(), player.getY(), player.getZ(),
                        sound, net.minecraft.sounds.SoundSource.PLAYERS,
                        1.0F, 1.0F, false);
            }
        }
    }
}
