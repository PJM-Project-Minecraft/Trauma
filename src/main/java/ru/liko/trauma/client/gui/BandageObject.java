package ru.liko.trauma.client.gui;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import ru.liko.trauma.Trauma;

// Based on Prototype-Pain Mod (MIT) - Credit: AdInVas
public class BandageObject extends GrabObject {
    private final int centerX;
    private final int centerY;
    private final float radius = 70;

    private float angle = 0;
    private float rotation = 0f;
    private float scaleFactor = 1f;
    private float progress = 0f;

    private final float maxProgress;

    public BandageObject(int centerX, int centerY, float scale, float maxProgress) {
        super(0, 0, 0, 0, 64, 64,
                ResourceLocation.fromNamespaceAndPath(Trauma.MODID, "textures/gui/bandage.png"),
                64, 64, scale);
        this.centerX = centerX;
        this.centerY = centerY;
        this.maxProgress = maxProgress;

        float scaleoffsetX = (float) (Math.cos(angle) * (-texWidth / 2f * (1 - scaleFactor)));
        float scaleoffsetY = (float) (Math.sin(angle) * (-texHeight / 2f * (1 - scaleFactor)));

        this.x = (int) (centerX + scaleoffsetX + Math.cos(angle) * radius - texWidth * scale / 2f);
        this.y = (int) (centerY + scaleoffsetY + Math.sin(angle) * radius - texHeight * scale / 2f);
    }

    public float getProgress() {
        return progress;
    }

    @Override
    public void mouseDragged(double mouseX, double mouseY, int button) {
        if (dragging && button == 0) {
            double dx = mouseX - centerX;
            double dy = mouseY - centerY;
            float newAngle = (float) Math.atan2(dy, dx);

            // Compute angular difference
            float diff = newAngle - angle;

            // Normalize
            while (diff < -Math.PI)
                diff += (float) (2 * Math.PI);
            while (diff > Math.PI)
                diff -= (float) (2 * Math.PI);

            // Allow only clockwise motion
            if (diff > 0) {
                angle += diff;
                rotation += diff * 6f; // spin effect
                progress += Math.toDegrees(diff) / 10.0f; // some increment per rotation
            }

            if (angle > Math.PI * 2) {
                // Play sound every loop (using wool sound to simulate bandage)
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.playSound(SoundEvents.WOOL_PLACE, 0.5f, 1.0f);
                }
                angle -= Math.PI * 2;
            }

            // Shrink
            scaleFactor = 1.0f - 0.7f * (progress / maxProgress);
            if (scaleFactor < 0.3f)
                scaleFactor = 0.3f;

            float scaleoffsetX = (float) (Math.cos(angle) * (-texWidth / 2f * (1 - scaleFactor)));
            float scaleoffsetY = (float) (Math.sin(angle) * (-texHeight / 2f * (1 - scaleFactor)));

            this.x = (int) (centerX + scaleoffsetX + Math.cos(angle) * radius - texWidth * scale / 2f);
            this.y = (int) (centerY + scaleoffsetY + Math.sin(angle) * radius - texHeight * scale / 2f);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics) {
        var pose = guiGraphics.pose();
        pose.pushPose();

        pose.translate(x + texWidth * scale / 2f, y + texHeight * scale / 2f, 0);
        pose.mulPose(Axis.ZP.rotation(rotation / 2));
        pose.scale(scale * scaleFactor, scale * scaleFactor, 1f);
        pose.translate(-texWidth / 2f, -texHeight / 2f, 0);

        guiGraphics.blit(tex, 0, 0, 0, 0, texWidth, texHeight, texWidth, texHeight);

        pose.popPose();
    }
}
