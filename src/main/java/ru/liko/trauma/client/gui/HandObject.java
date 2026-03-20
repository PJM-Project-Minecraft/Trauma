package ru.liko.trauma.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import ru.liko.trauma.Trauma;

// Based on Prototype-Pain Mod (MIT) - Credit: AdInVas
public class HandObject {
    public double x, y;
    public double vx, vy;
    private double prevX, prevY;
    private double shakeX = 0;
    private double shakeY = 0;
    private final double shakeAmount = 24.0;
    private float shakeScale = 0;

    private double stiffness = 0.25;
    private final double damping = 0.6;

    private int handStartX, handStartY;
    private boolean is_clicked = false;
    private float angle = 0;

    // Time offset for continuous smooth shake
    private final double timeOffset = Math.random() * 1000.0;

    private static final ResourceLocation ARM_TEX = ResourceLocation.fromNamespaceAndPath(Trauma.MODID,
            "textures/gui/limbs/arm.png");
    private static final ResourceLocation ARM_CLICK_TEX = ResourceLocation.fromNamespaceAndPath(Trauma.MODID,
            "textures/gui/limbs/arm_click.png");

    public HandObject(double startX, double startY, int handStartX, int handStartY) {
        this.x = startX;
        this.y = startY;
        this.handStartX = handStartX;
        this.handStartY = handStartY;
    }

    public void setStiffness(double stiffness) {
        this.stiffness = stiffness;
    }

    public void setShakeScale(float shakeScale) {
        this.shakeScale = shakeScale;
    }

    public void update(double mouseX, double mouseY) {
        double time = (System.currentTimeMillis() + timeOffset) / 250.0;

        // Use continuous sine waves to produce a smooth shake instead of purely random
        // jagged jumps
        shakeX = (Math.sin(time) + Math.sin(time * 0.73)) * 0.5 * shakeAmount * shakeScale;
        shakeY = (Math.cos(time * 0.8) + Math.cos(time * 1.3)) * 0.5 * shakeAmount * shakeScale;

        prevX = x;
        prevY = y;

        // Spring physics toward target (mouse cursor)
        double dx = mouseX - x + shakeX;
        double dy = mouseY - y + shakeY;

        double ax = dx * stiffness;
        double ay = dy * stiffness;

        vx += ax;
        vy += ay;

        vx *= damping;
        vy *= damping;

        x += vx;
        y += vy;

        double relX = x - handStartX;
        double relY = y - handStartY;
        angle = (float) Math.atan2(relY, relX);
    }

    public void render(GuiGraphics guiGraphics, float partialTicks) {
        double renderX = prevX + (x - prevX) * partialTicks;
        double renderY = prevY + (y - prevY) * partialTicks;

        PoseStack pose = guiGraphics.pose();
        pose.pushPose();

        pose.translate(renderX, renderY, 0);
        pose.mulPose(Axis.ZP.rotation((float) angle));

        if (is_clicked) {
            guiGraphics.blit(ARM_CLICK_TEX, -160, -32, 0, 0, 192, 64, 192, 64);
        } else {
            guiGraphics.blit(ARM_TEX, -160, -32, 0, 0, 192, 64, 192, 64);
        }

        pose.popPose();
    }

    public void mouseClicked() {
        is_clicked = true;
    }

    public void mouseReleased() {
        is_clicked = false;
    }

    public boolean isClicked() {
        return is_clicked;
    }
}
