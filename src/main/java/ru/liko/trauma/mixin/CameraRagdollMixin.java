package ru.liko.trauma.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.liko.trauma.client.ragdoll.ClientRagdollManager;

/**
 * Mixin into GameRenderer.renderLevel() to override camera position and
 * rotation
 * AFTER Camera.setup() has fully completed and returned, placing the camera at
 * the ragdoll head position.
 *
 * This approach (injecting into GameRenderer rather than Camera) is proven to
 * work
 * in prototype-physics and avoids subtle issues where Camera.setup() internal
 * flow
 * may not preserve overrides set within the method itself.
 *
 * IMPORTANT: This class must NOT reference any javax.vecmath types (Quat4f,
 * Vector3f).
 * All vecmath access is delegated to
 * ClientRagdollManager.getCameraDataForEntity().
 */
@Mixin(GameRenderer.class)
public class CameraRagdollMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private Camera mainCamera;

    // Smooth interpolation state — same approach as prototype-physics
    @Unique
    private Quaternionf trauma$oldQ = new Quaternionf();
    @Unique
    private double trauma$lastX = Double.NaN;
    @Unique
    private double trauma$lastY = Double.NaN;
    @Unique
    private double trauma$lastZ = Double.NaN;

    /**
     * Inject into GameRenderer.renderLevel() right AFTER Camera.setup() is called.
     * At this point the vanilla camera position (entity pos + eye height) has been
     * set.
     * We override it with the ragdoll head world-space coordinates.
     */
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V", shift = At.Shift.AFTER))
    private void trauma$afterCameraSetup(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (minecraft.player == null)
            return;

        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);

        // getCameraDataForEntity returns float[7] = {x, y, z, qx, qy, qz, qw} or null
        float[] cam = ClientRagdollManager.getCameraDataForEntity(minecraft.player.getId(), partialTick);
        if (cam == null) {
            trauma$lastX = Double.NaN;
            return;
        }

        float headX = cam[0];
        float headY = cam[1];
        float headZ = cam[2];
        Quaternionf q = new Quaternionf(cam[3], cam[4], cam[5], cam[6]);

        // Initialize smoothing state on first frame
        if (Double.isNaN(trauma$lastX)) {
            trauma$lastX = headX;
            trauma$lastY = headY;
            trauma$lastZ = headZ;
            trauma$oldQ.set(q);
        }

        // Smooth head position (lerp factor 0.2)
        trauma$lastX += (headX - trauma$lastX) * 0.2;
        trauma$lastY += (headY - trauma$lastY) * 0.2;
        trauma$lastZ += (headZ - trauma$lastZ) * 0.2;

        // Smooth rotation (slerp factor 0.2)
        trauma$oldQ.slerp(q, 0.2f);

        Camera camera = mainCamera;
        var camType = minecraft.options.getCameraType();

        // Target = smoothed head position in absolute world coordinates
        float targetX = (float) trauma$lastX;
        float targetY = (float) trauma$lastY;
        float targetZ = (float) trauma$lastZ;

        if (!camType.isFirstPerson()) {
            // Third person: orbit around head position
            float distance = 4.0f;
            if (camType.isMirrored())
                distance = -distance;
            Vector3f offset = new Vector3f(0f, 0f, distance);
            offset.rotate(q);
            targetX += offset.x;
            targetY += offset.y;
            targetZ += offset.z;
        } else {
            // First person: small local offset so the camera is at the "eyes"
            // instead of the center of the head box
            Vector3f eyeOffset = new Vector3f(0f, 0.15f, 0.15f);
            eyeOffset.rotate(trauma$oldQ);
            targetX += eyeOffset.x;
            targetY += eyeOffset.y;
            targetZ += eyeOffset.z;
        }

        // Set camera directly to ragdoll head position — NO lerp with vanilla
        ((CameraAccessor) camera).trauma$invokeSetPosition(targetX, targetY, targetZ);

        // Apply ragdoll rotation for first person
        if (camType.isFirstPerson()) {
            Quaternionf fixedQ = new Quaternionf(trauma$oldQ);
            // Flip Z because physics model is inverted compared to MC camera
            fixedQ.rotateZ((float) Math.PI);
            camera.rotation().set(fixedQ);

            // Calculate pitch and yaw to set to camera (helps fix culling/vanilla
            // overrides)
            Vector3f euler = fixedQ.getEulerAnglesXYZ(new Vector3f());
            float pitch = (float) Math.toDegrees(euler.x);
            float yaw = (float) Math.toDegrees(euler.y); // actually wait

            ((CameraAccessor) camera).trauma$invokeSetRotation(yaw, pitch);
        }
    }
}
