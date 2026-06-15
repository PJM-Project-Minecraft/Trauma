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
    /** Когда постпроцесс блюра выключен — краткий всплеск поверх HUD без захвата main-буфера (небо/туман). */
    private static float hitScreenFlash = 0f;
    private static boolean hitFlashRenderedThisFrame = false;

    // Continuous low-health post-effect strength (0..1). Smoothed so the effect framebuffer
    // is loaded/unloaded at most once per "low HP episode" instead of toggling every tick when
    // health/blood/trauma values oscillate around hard thresholds — that toggling is what
    // caused visible sky/fog flicker.
    private static float displayedLowHealthIntensity = 0f;
    private static float targetLowHealthIntensity = 0f;
    /** Активирующий порог (load post-chain) — выше выключающего, гистерезис. */
    private static final float LOW_HEALTH_ON_THRESHOLD = 0.02f;
    /** Выключающий порог — очень низкий, чтобы избежать дребезга. */
    private static final float LOW_HEALTH_OFF_THRESHOLD = 0.0025f;
    private static final float BLUR_OFF_THRESHOLD = 0.005f;

    /**
     * Returns true if the given post-chain was loaded by Trauma itself.
     * Used to avoid clobbering post-effects installed by other mods (e.g. NVG /
     * thermal vision shaders).
     */
    private static boolean isOurEffect(PostChain chain) {
        if (chain == null) return false;
        String name = chain.getName();
        return name.equals(SHADER_LOW_HEALTH.toString()) || name.equals(SHADER_BLUR.toString());
    }

    /**
     * Replace the current post-process effect with ours, but only if no other mod
     * currently owns the effect slot. If another mod's shader is active (e.g. an
     * NVG goggle effect), we yield and skip our own visual effect for this frame.
     *
     * @return true if our shader is now active, false if we yielded to another mod
     */
    private static boolean tryLoadOwnEffect(Minecraft mc, ResourceLocation targetShader) {
        PostChain currentEffect = mc.gameRenderer.currentEffect();
        if (currentEffect != null && !isOurEffect(currentEffect)) {
            // Foreign shader is active — let it be.
            return false;
        }
        boolean effectMismatch = currentEffect == null || !currentEffect.getName().equals(targetShader.toString());
        if (!shaderActive || effectMismatch) {
            mc.gameRenderer.loadEffect(targetShader);
        }
        return true;
    }

    /** Tear down our own post-effect, but only if it is still ours. */
    private static void shutdownOwnEffect(Minecraft mc) {
        PostChain currentEffect = mc.gameRenderer.currentEffect();
        if (isOurEffect(currentEffect)) {
            mc.gameRenderer.shutdownEffect();
        }
        // If a foreign shader replaced ours, don't touch it — it belongs to another mod.
        shaderActive = false;
    }

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

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /**
     * Low-health post-effect from vanilla HP only (blood/trauma simulation removed).
     */
    private static float computeLowHealthTarget(float health, float maxHealth) {
        if (!Config.LOW_HEALTH_USES_POST_PROCESS.get()) {
            return 0f;
        }
        if (maxHealth <= 0f) {
            return 0f;
        }
        float ratio = health / maxHealth;
        return clamp01((0.45f - ratio) / 0.25f);
    }

    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;

        float currentHealth = mc.player.getHealth();
        float maxHealth = mc.player.getMaxHealth();

        // ===== Low-health post-effect: continuous intensity with smoothing + hysteresis =====
        targetLowHealthIntensity = computeLowHealthTarget(currentHealth, maxHealth);

        if (targetLowHealthIntensity > displayedLowHealthIntensity) {
            // Быстрый attack — за ~3-4 тика дотягиваемся до цели
            displayedLowHealthIntensity += (targetLowHealthIntensity - displayedLowHealthIntensity) * 0.25f;
        } else {
            // Медленный release: ~30-60 тиков (1.5–3 с) — иначе при reген'е HP мерцает
            displayedLowHealthIntensity += (targetLowHealthIntensity - displayedLowHealthIntensity) * 0.04f;
        }
        if (displayedLowHealthIntensity < LOW_HEALTH_OFF_THRESHOLD && targetLowHealthIntensity <= 0f) {
            displayedLowHealthIntensity = 0f;
        }

        // Immediate client-side blur: detect health drops without waiting for sync
        if (lastPlayerHealth > 0 && currentHealth < lastPlayerHealth && Config.ENABLE_HIT_BLUR.get()) {
            float damage = lastPlayerHealth - currentHealth;
            float mult = Config.HIT_BLUR_MULTIPLIER.get().floatValue();
            float immediateBlur = (damage * 0.08f + 0.3f) * mult;
            if (Config.HIT_BLUR_USES_POST_PROCESS.get()) {
                targetBlurIntensity = Math.min(1.0f, targetBlurIntensity + immediateBlur);
            } else {
                hitScreenFlash = Math.min(1.0f, hitScreenFlash + immediateBlur * 1.05f);
            }
        }
        lastPlayerHealth = currentHealth;

        if (Config.HIT_BLUR_USES_POST_PROCESS.get()) {
            // Целевой блюр затухает на 0.1 каждый тик (~0.5 c при 20 TPS).
            targetBlurIntensity = Math.max(0f, targetBlurIntensity - 0.1f);

            if (targetBlurIntensity > displayedBlurIntensity) {
                displayedBlurIntensity += (targetBlurIntensity - displayedBlurIntensity) * 0.7f;
            } else {
                displayedBlurIntensity += (targetBlurIntensity - displayedBlurIntensity) * 0.3f;
            }

            if (displayedBlurIntensity < BLUR_OFF_THRESHOLD) {
                displayedBlurIntensity = 0f;
            }
        } else {
            targetBlurIntensity = 0f;
            displayedBlurIntensity = 0f;
            hitScreenFlash = Math.max(0f, hitScreenFlash - 0.16f);
        }

        boolean needsBlurEffect = displayedBlurIntensity > BLUR_OFF_THRESHOLD && Config.ENABLE_HIT_BLUR.get()
                && Config.HIT_BLUR_USES_POST_PROCESS.get();
        boolean needsLowHealthEffect = displayedLowHealthIntensity > LOW_HEALTH_ON_THRESHOLD
                || (shaderActive && displayedLowHealthIntensity > LOW_HEALTH_OFF_THRESHOLD);

        ResourceLocation targetShader = null;
        if (needsBlurEffect) {
            targetShader = SHADER_BLUR;
        } else if (needsLowHealthEffect) {
            targetShader = SHADER_LOW_HEALTH;
        }

        if (targetShader != null) {
            // Don't override post-effects owned by other mods (e.g. NVG / thermal vision).
            // tryLoadOwnEffect returns false if a foreign shader is currently active —
            // in that case we silently skip our overlay this frame so the other mod's
            // effect remains visible.
            shaderActive = tryLoadOwnEffect(mc, targetShader);
        } else {
            if (shaderActive) {
                shutdownOwnEffect(mc);
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

        float maxHp = mc.player.getMaxHealth();
        boolean isLowHp = maxHp > 0 && mc.player.getHealth() / maxHp <= 0.35f;

        boolean needsLowHealthEffect = displayedLowHealthIntensity > LOW_HEALTH_OFF_THRESHOLD;
        boolean needsBlurEffect = displayedBlurIntensity > BLUR_OFF_THRESHOLD && Config.ENABLE_HIT_BLUR.get()
                && Config.HIT_BLUR_USES_POST_PROCESS.get();

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
                float baseRadius = clamp01(displayedLowHealthIntensity) * 5.5f;

                long timeSinceHeartbeat = System.currentTimeMillis() - ModOverlays.lastHeartbeatTime;
                float heartbeatBoost = 0.0f;
                if (timeSinceHeartbeat < 400 && isLowHp) {
                    float t = timeSinceHeartbeat / 400.0f;
                    heartbeatBoost = (1.0f - t) * 8.0f;
                }

                float finalRadius = baseRadius + heartbeatBoost;
                float saturation = Math.clamp(1.0f - clamp01(displayedLowHealthIntensity) * 0.65f, 0.12f, 1f);

                float envelope = clamp01(displayedLowHealthIntensity);
                finalRadius *= envelope;
                saturation = 1.0f + (saturation - 1.0f) * envelope;

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
        hitFlashRenderedThisFrame = false;
    }

    // ==========================================
    // Red vignette overlay
    // ==========================================

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiLayerEvent.Post event) {
        renderHitFlashOverlayOnce();
        if (!renderedThisFrame)
            renderBloodOverlay();
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        renderHitFlashOverlayOnce();
        if (!renderedThisFrame)
            renderBloodOverlay();
    }

    private static void renderHitFlashOverlayOnce() {
        if (hitFlashRenderedThisFrame || hitScreenFlash <= 0.01f)
            return;
        if (!Config.ENABLE_HIT_BLUR.get() || Config.HIT_BLUR_USES_POST_PROCESS.get())
            return;

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getWidth();
        int screenHeight = mc.getWindow().getHeight();
        int alphaInt = (int) (hitScreenFlash * 95);
        if (alphaInt < 4)
            return;

        hitFlashRenderedThisFrame = true;

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

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        builder.addVertex(pose, 0, screenHeight, z).setColor(255, 255, 255, alphaInt);
        builder.addVertex(pose, screenWidth, screenHeight, z).setColor(255, 255, 255, alphaInt);
        builder.addVertex(pose, screenWidth, 0, z).setColor(255, 255, 255, alphaInt);
        builder.addVertex(pose, 0, 0, z).setColor(255, 255, 255, alphaInt);
        BufferUploader.drawWithShader(builder.buildOrThrow());

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

    private static void renderBloodOverlay() {
        if (!Config.ENABLE_BLOOD_OVERLAY.get())
            return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;

        float maxHp = mc.player.getMaxHealth();
        boolean isLowHp = maxHp > 0 && mc.player.getHealth() <= 6.0f;

        float intensity = 0f;
        if (maxHp > 0) {
            intensity = clamp01((0.40f - mc.player.getHealth() / maxHp) / 0.25f);
        }

        boolean pulseHeartbeat = isLowHp;

        if (intensity < 0.02f && isLowHp) {
            intensity = 0.1f;
        }

        if (intensity < 0.02f)
            return;

        renderedThisFrame = true;

        int screenWidth = mc.getWindow().getWidth();
        int screenHeight = mc.getWindow().getHeight();

        // Heartbeat vignette pulse
        long timeSinceHeartbeat = System.currentTimeMillis() - ModOverlays.lastHeartbeatTime;
        float heartbeatPulse = 1.0f;
        if (timeSinceHeartbeat < 300 && pulseHeartbeat) {
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
