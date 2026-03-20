package ru.liko.trauma.common.util;

import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import ru.liko.trauma.Trauma;

public class ToolHelper {

    public static final TagKey<Item> CUTTING_TOOLS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(Trauma.MODID, "cutting_tools"));

    public static boolean isCuttingTool(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.is(CUTTING_TOOLS);
    }
}
