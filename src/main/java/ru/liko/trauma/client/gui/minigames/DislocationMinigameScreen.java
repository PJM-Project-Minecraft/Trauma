package ru.liko.trauma.client.gui.minigames;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector2d;
import ru.liko.trauma.Trauma;
import ru.liko.trauma.client.gui.HandObject;
import ru.liko.trauma.common.system.TraumaData;
import ru.liko.trauma.network.DislocationTryPacket;
import net.neoforged.neoforge.network.PacketDistributor;

public class DislocationMinigameScreen extends Screen {
    private final Screen parent;
    private final Player target;

    private HandObject handObject;
    private BoneObject boneObject;

    public DislocationMinigameScreen(Screen parent, Player target) {
        super(Component.literal("DislocationMinigame"));
        this.parent = parent;
        this.target = target;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        Minecraft mc = Minecraft.getInstance();

        guiGraphics.drawCenteredString(mc.font, Component.translatable("gui.trauma.dislocation_instruction1"),
                this.width / 2, 10, 0xFFFFFF);

        if (target != null && target != mc.player) {
            guiGraphics.drawCenteredString(mc.font,
                    Component.literal("Лечение: ").append(target.getDisplayName()),
                    this.width / 2, 25, 0x44AAFF);
        }

        guiGraphics.drawCenteredString(mc.font, (int) boneObject.getFakeDislocation() + "%", this.width / 2,
                this.height / 6 + 175, 0xCC0000);
        guiGraphics.drawCenteredString(mc.font, Component.translatable("gui.trauma.minigame_exit"), this.width / 2,
                this.height / 6 + 190, 0xFFFFFF);

        guiGraphics.blit(ResourceLocation.fromNamespaceAndPath(Trauma.MODID, "textures/gui/limbs/bone2.png"),
                this.width / 2 - 160, this.height / 2 - 40, 0, 0, 160, 80, 160, 80);
        guiGraphics.setColor(0.5f, 0.5f, 0.5f, 0.1f);
        guiGraphics.blit(ResourceLocation.fromNamespaceAndPath(Trauma.MODID, "textures/gui/limbs/bone.png"),
                this.width / 2, this.height / 2 - 40, 0, 0, 160, 80, 160, 80);
        guiGraphics.setColor(1, 1, 1, 1f);
        boneObject.render(guiGraphics);

        handObject.render(guiGraphics, partialTicks);
    }

    private double lastpMouseX = 100, lastpMouseY = 100;
    public int endtick = -40;

    @Override
    public void tick() {
        super.tick();
        handObject.update(lastpMouseX, lastpMouseY);
        boneObject.update();
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            // We don't have pain/consciousness in Trauma, so we just use default values
            float consscale = 0.15f;
            float painscale = 0.0f;
            handObject.setShakeScale(painscale);
            handObject.setStiffness(consscale);
            if (handObject.isClicked() && boneObject.isInside(lastpMouseX, lastpMouseY)) {
                Vector2d vel = new Vector2d(handObject.vx, handObject.vy);
                boneObject.onHit(vel, target);
            }
        }

        if (boneObject.isEndCondition()) {
            endtick++;
            if (endtick >= 0) {
                onClose();
            }
        }
    }

    @Override
    public void onClose() {
        super.onClose();
        if (boneObject.isEndCondition()) {
            PacketDistributor.sendToServer(new DislocationTryPacket(target.getUUID(), 0));
        }
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        handObject.mouseClicked();
        return super.mouseClicked(pMouseX, pMouseY, pButton);
    }

    @Override
    public void mouseMoved(double pMouseX, double pMouseY) {
        lastpMouseY = pMouseY;
        lastpMouseX = pMouseX;
        super.mouseMoved(pMouseX, pMouseY);
    }

    @Override
    public boolean mouseReleased(double pMouseX, double pMouseY, int pButton) {
        handObject.mouseReleased();
        return super.mouseReleased(pMouseX, pMouseY, pButton);
    }

    @Override
    protected void init() {
        super.init();
        handObject = new HandObject(this.width / 2, this.height / 2, this.width, this.height / 3 * 2);

        float dislocation = 0f;
        if (target.getData(ru.liko.trauma.common.capability.ModAttachments.TRAUMA_DATA) != null) {
            dislocation = target.getData(ru.liko.trauma.common.capability.ModAttachments.TRAUMA_DATA).legDislocation();
        }
        boneObject = new BoneObject(this.width / 2, this.height / 2 - 40, dislocation);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
