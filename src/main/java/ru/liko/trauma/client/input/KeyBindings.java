package ru.liko.trauma.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    public static final String KEY_CATEGORY_TRAUMA = "key.category.trauma";
    public static final String KEY_SUPPRESS_BLEEDING = "key.trauma.suppress_bleeding";
    public static final String KEY_FIX_DISLOCATION = "key.trauma.fix_dislocation";
    public static final KeyMapping SUPPRESS_KEY = new KeyMapping(
            KEY_SUPPRESS_BLEEDING,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            KEY_CATEGORY_TRAUMA);

    public static final KeyMapping FIX_DISLOCATION_KEY = new KeyMapping(
            KEY_FIX_DISLOCATION,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            KEY_CATEGORY_TRAUMA);
}
