package ru.liko.trauma.client.render;

import java.lang.reflect.Field;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import ru.liko.trauma.Config;
import ru.liko.trauma.Trauma;
import ru.liko.trauma.common.system.TraumaData;
import ru.liko.trauma.common.capability.ModAttachments;
import ru.liko.trauma.client.overlay.ModOverlays;

@EventBusSubscriber(modid = Trauma.MODID, value = Dist.CLIENT)
public class ShaderHandler {

    private static final ResourceLocation VIGNETTE_TEXTURE = ResourceLocation
            .withDefaultNamespace("textures/misc/vignette.png");
    private static final ResourceLocation SHADER_LOW_HEALTH = ResourceLocation.fromNamespaceAndPath(Trauma.MODID,
            "shaders/post/low_health.json");
    private static final ResourceLocation SHADER_BLUR = ResourceLocation.fromNamespaceAndPath(Trauma.MODID,
            "shaders/post/hit_blur.json");

    private static boolean shaderActive = false;
    private static boolean renderedThisFrame = false;
    private static Field passesField = null;

    // Client-side smooth blur
    private static float displayedBlurIntensity = 0f;
    private static float targetBlurIntensity = 0f;
    private static float lastPlayerHealth = -1f;

    @SuppressWarnings("unchecked")
    private static List<PostPass> getPasses(PostChain chain) {
        try {
            if (passesField == null) {
                for (Field f : PostChain.class.getDeclaredFields()) {
                    if (f.getType() == List.class) {
                        f.setAccessible(true);
                        passesField = f;
                        break;
                    }
                }
            }
            if (passesField != null) {
                return (List<PostPass>) passesField.get(chain);
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;

        TraumaData data = mc.player.getData(ModAttachments.TRAUMA_DATA);
        float bloodVolume = data.bloodVolume();
        boolean isLowHp = mc.player.getHealth() <= 6.0f;
        boolean needsLowHealthEffect = bloodVolume < TraumaData.MAX_BLOOD || isLowHp;

        // Immediate client-side blur: detect health drops without waiting for sync
        float currentHealth = mc.player.getHealth();
        if (lastPlayerHealth > 0 && currentHealth < lastPlayerHealth && Config.ENABLE_HIT_BLUR.get()) {
            float damage = lastPlayerHealth - currentHealth;
            float immediateBlur = (damage * 0.08f + 0.3f) * Config.HIT_BLUR_MULTIPLIER.get().floatValue(); // Зависит от
                                                                                                           // урона и
                                                                                                           // конфига
            targetBlurIntensity = Math.min(1.0f, targetBlurIntensity + immediateBlur);
        }
        lastPlayerHealth = currentHealth;

        // Отключаем влияние серверного блюра (serverBlur),
        // так как именно пакет с сервера вызывал второй (двойной) скачок блюра из-за
        // пинга.
        // Теперь весь эффект блюра при уроне обрабатывается строго локально без
        // задержек.

        // Целевой блюр затухает на 0.1 каждый тик.
        // При 20 TPS игры: 1.0 (макс блюр) упадет до 0 ровно за 10 тиков = 0.5 секунды.
        targetBlurIntensity = Math.max(0f, targetBlurIntensity - 0.1f);

        // Анимация визуального блюра
        if (targetBlurIntensity > displayedBlurIntensity) {
            // Impact: very fast appearance (0.7f), almost instant, but without sharp
            // скачка в 1 кадр
            displayedBlurIntensity += (targetBlurIntensity - displayedBlurIntensity) * 0.7f;
        } else {
            // Затухание: плавный шлейф растворения (0.3f)
            displayedBlurIntensity += (targetBlurIntensity - displayedBlurIntensity) * 0.3f;
        }

        if (displayedBlurIntensity < 0.005f) {
            displayedBlurIntensity = 0f;
        }

        boolean needsBlurEffect = displayedBlurIntensity > 0.01f && Config.ENABLE_HIT_BLUR.get();

        ResourceLocation targetShader = null;
        if (needsBlurEffect) {
            targetShader = SHADER_BLUR;
        } else if (needsLowHealthEffect) {
            targetShader = SHADER_LOW_HEALTH;
        }

        if (targetShader != null) {
            PostChain currentEffect = mc.gameRenderer.currentEffect();
            boolean effectMismatch = currentEffect == null || !currentEffect.getName().equals(targetShader.toString());

            if (!shaderActive || effectMismatch) {
                if (currentEffect != null) {
                    mc.gameRenderer.shutdownEffect();
                }
                mc.gameRenderer.loadEffect(targetShader);
                shaderActive = true;
            }
        } else {
            if (shaderActive) {
                mc.gameRenderer.shutdownEffect();
                shaderActive = false;
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL)
            return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;

        TraumaData data = mc.player.getData(ModAttachments.TRAUMA_DATA);
        float bloodVolume = data.bloodVolume();
        boolean isLowHp = mc.player.getHealth() <= 6.0f;
        boolean needsLowHealthEffect = bloodVolume < TraumaData.MAX_BLOOD || isLowHp;
        boolean needsBlurEffect = displayedBlurIntensity > 0.01f && Config.ENABLE_HIT_BLUR.get();

        // === Blur (hit effect) dynamic Radius control ===
        if (needsBlurEffect) {
            PostChain chain = mc.gameRenderer.currentEffect();
            if (chain != null && chain.getName().equals(SHADER_BLUR.toString())) {
                // Map displayedBlurIntensity (0..1) -> Radius (1..12)
                float blurRadius = 1.0f + displayedBlurIntensity * 11.0f;

                List<PostPass> passes = getPasses(chain);
                if (passes != null) {
                    for (PostPass pass : passes) {
                        Uniform radiusUniform = pass.getEffect().getUniform("Radius");
                        if (radiusUniform != null) {
                            radiusUniform.set(blurRadius);
                        }
                    }
                }
            }
        }

        if (needsLowHealthEffect) {

            // Dynamically control uniforms
            PostChain chain = mc.gameRenderer.currentEffect();
            if (chain != null && chain.getName().equals(SHADER_LOW_HEALTH.toString())) {
                float bloodLoss = 1.0f - (bloodVolume / TraumaData.MAX_BLOOD);
                bloodLoss = Math.clamp(bloodLoss, 0f, 1f);

                // Base blur radius: scales 0 to 5 with blood loss
                float baseRadius = bloodLoss * 5.0f;

                // Heartbeat boost (only at low HP)
                long timeSinceHeartbeat = System.currentTimeMillis() - ModOverlays.lastHeartbeatTime;
                float heartbeatBoost = 0.0f;
                if (timeSinceHeartbeat < 400 && mc.player.getHealth() <= 6.0f) {
                    float t = timeSinceHeartbeat / 400.0f;
                    heartbeatBoost = (1.0f - t) * 8.0f;
                }

                float finalRadius = baseRadius + heartbeatBoost;
                float saturation = 1.0f - (bloodLoss * 0.7f);

                List<PostPass> passes = getPasses(chain);
                if (passes != null) {
                    for (PostPass pass : passes) {
                        Uniform radiusUniform = pass.getEffect().getUniform("Radius");
                        if (radiusUniform != null) {
                            radiusUniform.set(Math.max(0.1f, finalRadius));
                        }
                        Uniform satUniform = pass.getEffect().getUniform("Saturation");
                        if (satUniform != null) {
                            satUniform.set(saturation);
                        }
                    }
                }
            }
        }

        renderedThisFrame = false;
    }

    // ==========================================
    // Red vignette overlay
    // ==========================================

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiLayerEvent.Post event) {
        if (!renderedThisFrame)
            renderBloodOverlay();
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (!renderedThisFrame)
            renderBloodOverlay();
    }

    private static void renderBloodOverlay() {
        if (!Config.ENABLE_BLOOD_OVERLAY.get())
            return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;

        TraumaData data = mc.player.getData(ModAttachments.TRAUMA_DATA);
        float bloodVolume = data.bloodVolume();
        boolean isLowHp = mc.player.getHealth() <= 6.0f;

        // Base intensity calculation from blood loss
        float intensity = 1.0f - (bloodVolume / TraumaData.MAX_BLOOD);
        intensity = Math.clamp(intensity, 0f, 1f);

        // If not bleeding but low HP, give a small base intensity so heartbeat pulse
        // works
        if (intensity < 0.02f && isLowHp) {
            intensity = 0.1f;
        }

        if (intensity < 0.02f)
            return;

        renderedThisFrame = true;

        int screenWidth = mc.getWindow().getWidth();
        int screenHeight = mc.getWindow().getHeight();

        // Heartbeat vignette pulse (only at low HP)
        long timeSinceHeartbeat = System.currentTimeMillis() - ModOverlays.lastHeartbeatTime;
        float heartbeatPulse = 1.0f;
        if (timeSinceHeartbeat < 300 && mc.player.getHealth() <= 6.0f) {
            float t = timeSinceHeartbeat / 300.0f;
            heartbeatPulse = 1.0f + (1.0f - t) * 0.6f;
        }

        // === Setup render ===
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Matrix4f projMatrix = new Matrix4f().setOrtho(0.0F, screenWidth, screenHeight, 0.0F, 1000.0F, 21000F);
        RenderSystem.setProjectionMatrix(projMatrix, VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        stack.identity();
        stack.translation(0.0F, 0.0F, -11000F);
        RenderSystem.applyModelViewMatrix();

        PoseStack poseStack = new PoseStack();
        Matrix4f pose = poseStack.last().pose();
        float z = -90;

        // --- RED VIGNETTE ---
        float vigAlpha = intensity * heartbeatPulse;
        int alphaInt = (int) (vigAlpha * 180);
        if (alphaInt > 220)
            alphaInt = 220;

        if (alphaInt > 0) {
            RenderSystem.setShaderTexture(0, VIGNETTE_TEXTURE);
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                    DefaultVertexFormat.POSITION_TEX_COLOR);
            builder.addVertex(pose, 0, screenHeight, z).setUv(0, 1).setColor(180, 0, 0, alphaInt);
            builder.addVertex(pose, screenWidth, screenHeight, z).setUv(1, 1).setColor(180, 0, 0, alphaInt);
            builder.addVertex(pose, screenWidth, 0, z).setUv(1, 0).setColor(180, 0, 0, alphaInt);
            builder.addVertex(pose, 0, 0, z).setUv(0, 0).setColor(180, 0, 0, alphaInt);
            BufferUploader.drawWithShader(builder.buildOrThrow());
        }

        // Restore
        stack.popMatrix();
        RenderSystem.applyModelViewMatrix();
        var window = mc.getWindow();
        RenderSystem.setProjectionMatrix(
                new Matrix4f().setOrtho(0.0F,
                        (float) (window.getWidth() / window.getGuiScale()),
                        (float) (window.getHeight() / window.getGuiScale()),
                        0.0F, 1000.0F, 21000F),
                VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
