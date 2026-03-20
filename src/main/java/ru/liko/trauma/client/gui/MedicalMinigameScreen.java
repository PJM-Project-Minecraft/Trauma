package ru.liko.trauma.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.liko.trauma.Trauma;
import ru.liko.trauma.network.MedicalMinigameResultPayload;

public class MedicalMinigameScreen extends Screen {

    private static final double REQUIRED_PROGRESS = 100.0;

    private BandageObject bandageObject;
    private HandObject handObject;

    private double lastMouseX = 0, lastMouseY = 0;
    private final int targetId;

    public MedicalMinigameScreen(int targetId) {
        super(Component.translatable("gui.trauma.medical_minigame"));
        this.targetId = targetId;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        handObject = new HandObject(centerX, centerY, this.width, this.height / 3 * 2);
        bandageObject = new BandageObject(centerX, centerY, 1.0f, (float) REQUIRED_PROGRESS);
    }

    @Override
    public void tick() {
        handObject.update(lastMouseX, lastMouseY);
        bandageObject.mouseDragged(handObject.x, handObject.y, 0);

        if (bandageObject.getProgress() >= REQUIRED_PROGRESS) {
            finishMinigame(true);
        }
        super.tick();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        bandageObject.mouseClicked(handObject.x, handObject.y, button);
        handObject.mouseClicked();
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        bandageObject.setDragging(false);
        handObject.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        ResourceLocation centerTex = ResourceLocation.fromNamespaceAndPath(Trauma.MODID,
                "textures/gui/bandage_center.png");
        guiGraphics.blit(centerTex, centerX - 40, centerY - 40, 0, 0, 80, 80, 80, 80);

        guiGraphics.drawCenteredString(this.font, Component.translatable("message.trauma.minigame_instruction_1"),
                centerX, 10, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("message.trauma.minigame_instruction_2"),
                centerX, 20, 0xFFFFFF);

        if (this.minecraft != null && this.minecraft.level != null) {
            net.minecraft.world.entity.Entity targetEntity = this.minecraft.level.getEntity(targetId);
            if (targetEntity != null) {
                guiGraphics.drawCenteredString(this.font,
                        Component.literal("Лечение: ").append(targetEntity.getDisplayName()),
                        centerX, 35, 0x44AAFF);
            }
        }

        bandageObject.render(guiGraphics);

        handObject.render(guiGraphics, partialTick);
    }

    private void finishMinigame(boolean success) {
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
            PacketDistributor.sendToServer(new MedicalMinigameResultPayload(success));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
