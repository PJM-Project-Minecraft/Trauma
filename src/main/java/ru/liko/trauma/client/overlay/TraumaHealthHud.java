package ru.liko.trauma.client.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import ru.liko.trauma.Config;
import ru.liko.trauma.Trauma;

@EventBusSubscriber(modid = Trauma.MODID, value = Dist.CLIENT)
public final class TraumaHealthHud {

    private static final ResourceLocation LAYER_ID = ResourceLocation.fromNamespaceAndPath(Trauma.MODID, "health_bar");

    private TraumaHealthHud() {
    }

    /** Вызывается с MOD event bus из {@link ru.liko.trauma.Trauma.ClientModEvents}. */
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, LAYER_ID, TraumaHealthHud::renderBar);
    }

    @SubscribeEvent
    public static void hideVanillaHearts(RenderGuiLayerEvent.Pre event) {
        if (Config.SHOW_VANILLA_HEALTH.get()) {
            return;
        }
        if (VanillaGuiLayers.PLAYER_HEALTH.equals(event.getName())) {
            event.setCanceled(true);
        }
    }

    private static void renderBar(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        if (Config.SHOW_VANILLA_HEALTH.get()) {
            return;
        }

        int screenH = mc.getWindow().getGuiScaledHeight();

        int hp = Mth.ceil(mc.player.getHealth());
        String hpStr = Integer.toString(hp);
        int textWidth = mc.font.width(hpStr);

        int barW = 100;
        int barH = 8;
        int padding = 5;
        int crossSize = 9;
        
        int panelW = padding + crossSize + 4 + textWidth + 5 + barW + padding;
        int panelH = 18;

        int x = 10;
        int y = screenH - panelH - 10; // Левый нижний угол с отступом 10

        // Фон панели
        graphics.fill(x, y, x + panelW, y + panelH, 0xC0101010);

        // Крест (9x9)
        int crossX = x + padding;
        int crossY = y + (panelH - crossSize) / 2;
        graphics.fill(crossX + 3, crossY, crossX + 6, crossY + 9, 0xFF888888);
        graphics.fill(crossX, crossY + 3, crossX + 9, crossY + 6, 0xFF888888);

        // Текст
        int textX = crossX + crossSize + 4;
        int textY = y + (panelH - 8) / 2 + 1;
        graphics.drawString(mc.font, hpStr, textX, textY, 0xFFCCCCCC, false);

        // Бар здоровья
        int barX = textX + textWidth + 5;
        int barY = y + (panelH - barH) / 2;
        
        // Рамка и фон бара
        graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF353535);
        graphics.fill(barX + 1, barY + 1, barX + barW - 1, barY + barH - 1, 0xFF151515);

        // Заливка бара
        float maxHp = mc.player.getMaxHealth();
        float pct = maxHp > 0 ? Mth.clamp(mc.player.getHealth() / maxHp, 0f, 1f) : 0f;
        int fillW = Mth.floor((barW - 2) * pct);
        if (fillW > 0) {
            int fillColor = pct > 0.35f ? 0xFFAAAAAA : 0xFF884444; // Краснеет при низком ХП
            graphics.fill(barX + 1, barY + 1, barX + 1 + fillW, barY + barH - 1, fillColor);
        }
    }
}
