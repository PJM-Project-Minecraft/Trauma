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

    public static final Supplier<Item> BLOOD_BAG = ITEMS.register("blood_bag",
            () -> new MedicalItem(new Item.Properties().stacksTo(1), MedicalItem.MedicalType.BLOOD_BAG));

    public static final Supplier<Item> SPLINT = ITEMS.register("splint",
            () -> new MedicalItem(new Item.Properties().stacksTo(4), MedicalItem.MedicalType.SPLINT));
}
