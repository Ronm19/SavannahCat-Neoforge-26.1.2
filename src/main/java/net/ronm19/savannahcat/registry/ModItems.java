package net.ronm19.savannahcat.registry;

import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.ronm19.savannahcat.SavannahCatMod;
import net.ronm19.savannahcat.item.SavannahCatWhistleItem;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SavannahCatMod.MOD_ID);

    public static final DeferredItem<SpawnEggItem> SAVANNAH_CAT_SPAWN_EGG = ITEMS.registerItem(
            "savannah_cat_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.SAVANNAH_CAT.get())));
    public static final DeferredItem<SavannahCatWhistleItem> SAVANNAH_CAT_WHISTLE = ITEMS.registerItem(
            "savannah_cat_whistle", SavannahCatWhistleItem::new);
    public static final DeferredItem<BlockItem> SCRATCHING_POST = ITEMS.registerSimpleBlockItem(ModBlocks.SCRATCHING_POST);
    public static final DeferredItem<BlockItem> FOOD_WATER_BOWL = ITEMS.registerSimpleBlockItem(ModBlocks.FOOD_WATER_BOWL);

    private ModItems() {}
}
