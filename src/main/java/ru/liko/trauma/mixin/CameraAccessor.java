package ru.liko.trauma.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor mixin for Camera, exposing the protected setPosition method
 * so it can be called from the GameRenderer mixin.
 */
@Mixin(Camera.class)
public interface CameraAccessor {

    @Invoker("setPosition")
    void trauma$invokeSetPosition(double x, double y, double z);

    @Invoker("setRotation")
    void trauma$invokeSetRotation(float yRot, float xRot);
}
