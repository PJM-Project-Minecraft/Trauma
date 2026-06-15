package ru.liko.trauma.common.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.liko.trauma.Trauma;

import java.util.function.Supplier;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, Trauma.MODID);

    public static final Supplier<Item> BANDAGE = ITEMS.register("bandage",
            () -> new MedicalItem(new Item.Properties().stacksTo(16), MedicalItem.MedicalType.BANDAGE));

    public static final Supplier<Item> TOURNIQUET = ITEMS.register("tourniquet",
            () -> new MedicalItem(new Item.Properties().stacksTo(4), MedicalItem.MedicalType.TOURNIQUET));
}
