package ru.liko.trauma.client.overlay;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import ru.liko.trauma.Config;
import ru.liko.trauma.Trauma;
import ru.liko.trauma.common.capability.ModAttachments;
import ru.liko.trauma.common.system.TraumaData;

@EventBusSubscriber(modid = Trauma.MODID, value = Dist.CLIENT)
public class InventoryOverlay {

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen inventoryScreen))
            return;

        Config.GuiPosition pos = Config.INVENTORY_GUI_POSITION.get();
        if (pos == Config.GuiPosition.DISABLED)
            return;

        Player player = inventoryScreen.getMinecraft().player;
        if (player == null)
            return;

        TraumaData data = player.getData(ModAttachments.TRAUMA_DATA);
        GuiGraphics g = event.getGuiGraphics();
        Font font = inventoryScreen.getMinecraft().font;

        int screenW = inventoryScreen.width;
        int screenH = inventoryScreen.height;
        
        // Calculate dynamic height based on active statuses
        int activeStatuses = 0;
        if (data.bleedStrength() > 0) activeStatuses++;
        if (data.legFracture() > 0) activeStatuses++;
        else if (data.legDislocation() > 0) activeStatuses++;
        
        int panelW = 160;
        int panelH = 65 + (activeStatuses > 0 ? (activeStatuses * 14 + 10) : 15); // Base height + statuses + padding

        int panelX, panelY;
        if (pos == Config.GuiPosition.BOTTOM_RIGHT) {
            panelX = screenW - panelW - 10;
            panelY = screenH - panelH - 10;
        } else {
            // BOTTOM_LEFT
            panelX = 10;
            panelY = screenH - panelH - 10;
        }

        // Panel Background (SCUM style - dark, semi-transparent, technical)
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xDD111111);
        
        // Tech borders
        g.fill(panelX, panelY, panelX + panelW, panelY + 2, 0xFF444444); // Top thick
        g.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, 0xFF333333); // Bottom thin
        g.fill(panelX, panelY, panelX + 1, panelY + panelH, 0xFF333333); // Left
        g.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, 0xFF333333); // Right

        // Header Title
        Component title = Component.translatable("gui.trauma.status_title");
        g.drawString(font, title, panelX + 6, panelY + 6, 0xFFAAAAAA, false);
        g.fill(panelX + 6, panelY + 16, panelX + panelW - 6, panelY + 17, 0x55FFFFFF);

        int currentY = panelY + 22;
        int barX = panelX + 8;
        int barW = panelW - 16;
        int barH = 10;

        // Health Bar
        float hp = player.getHealth();
        float maxHp = player.getMaxHealth();
        float hpPercent = Math.clamp(hp / maxHp, 0f, 1f);

        g.fill(barX - 1, currentY - 1, barX + barW + 1, currentY + barH + 1, 0xAA000000); // Outer border
        g.fill(barX, currentY, barX + barW, currentY + barH, 0xFF222222); // Empty bg
        
        int hpColor = hpPercent > 0.5f ? 0xFFE0E0E0 : (hpPercent > 0.25f ? 0xFFFFAA00 : 0xFFFF3333);
        if (hpPercent > 0) {
            g.fill(barX, currentY, barX + (int) (barW * hpPercent), currentY + barH, hpColor);
        }

        String hpStr = String.format("%.0f / %.0f", hp, maxHp);
        g.drawString(font, "HLTH", barX + 2, currentY + 1, 0xFFFFFFFF, true);
        g.drawString(font, hpStr, barX + barW - font.width(hpStr) - 2, currentY + 1, 0xFFFFFFFF, true);

        currentY += barH + 6;

        // Blood Bar
        float bloodPercent = Math.clamp(data.bloodVolume() / TraumaData.MAX_BLOOD, 0f, 1f);

        g.fill(barX - 1, currentY - 1, barX + barW + 1, currentY + barH + 1, 0xAA000000); // Outer border
        g.fill(barX, currentY, barX + barW, currentY + barH, 0xFF220000); // Empty bg
        
        int bloodColor = bloodPercent > 0.6f ? 0xFFCC0000 : (bloodPercent > 0.3f ? 0xFFFF4444 : 0xFFFF1111);
        if (bloodPercent > 0) {
            g.fill(barX, currentY, barX + (int) (barW * bloodPercent), currentY + barH, bloodColor);
        }

        String bloodStr = String.format("%.0f ML", data.bloodVolume());
        g.drawString(font, "BLOOD", barX + 2, currentY + 1, 0xFFFFFFFF, true);
        g.drawString(font, bloodStr, barX + barW - font.width(bloodStr) - 2, currentY + 1, 0xFFFFFFFF, true);

        currentY += barH + 8;

        // Status Effects Section
        if (activeStatuses > 0) {
            g.fill(panelX + 6, currentY, panelX + panelW - 6, currentY + 1, 0x33FFFFFF);
            currentY += 4;
            
            // Bleeding
            if (data.bleedStrength() > 0) {
                Component bleedStatus = Component.translatable("gui.trauma.bleed_level_" + data.bleedStrength());
                int bleedColor = data.bleedStrength() >= 3 ? 0xFFFF2222 : data.bleedStrength() >= 2 ? 0xFFFF7700 : 0xFFFFAA00;
                
                g.fill(barX, currentY + 2, barX + 4, currentY + 6, bleedColor);
                g.drawString(font, bleedStatus, barX + 8, currentY, bleedColor, false);
                currentY += 14;
            }
            
            // Leg Injuries
            if (data.legFracture() > 0) {
                g.fill(barX, currentY + 2, barX + 4, currentY + 6, 0xFFFF3333);
                g.drawString(font, Component.translatable("gui.trauma.broken_leg"), barX + 8, currentY, 0xFFFF3333, false);
                currentY += 14;
                
                if (data.hasSplint()) {
                    g.fill(barX, currentY + 2, barX + 4, currentY + 6, 0xFF55FF55);
                    g.drawString(font, Component.translatable("gui.trauma.splint_applied"), barX + 8, currentY, 0xFF55FF55, false);
                    currentY += 14;
                }
            } else if (data.legDislocation() > 0) {
                g.fill(barX, currentY + 2, barX + 4, currentY + 6, 0xFFFFAA00);
                g.drawString(font, Component.translatable("gui.trauma.dislocated_leg"), barX + 8, currentY, 0xFFFFAA00, false);
                currentY += 14;
            }
        } else {
            // No active negative statuses
            g.fill(panelX + 6, currentY, panelX + panelW - 6, currentY + 1, 0x33FFFFFF);
            currentY += 4;
            g.fill(barX, currentY + 2, barX + 4, currentY + 6, 0xFF55FF55);
            g.drawString(font, Component.translatable("gui.trauma.bleed_none"), barX + 8, currentY, 0xFF55FF55, false);
        }
    }
}
