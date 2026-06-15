package ru.liko.trauma.common.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.liko.trauma.Trauma;

import java.util.function.Supplier;

public class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister
            .create(Registries.CREATIVE_MODE_TAB, Trauma.MODID);

    public static final Supplier<CreativeModeTab> TRAUMA_TAB = CREATIVE_TABS.register("trauma_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.trauma"))
                    .icon(() -> new ItemStack(ModItems.BANDAGE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.BANDAGE.get());
                        output.accept(ModItems.TOURNIQUET.get());
                    })
                    .build());
}
