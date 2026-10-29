package net.ronm19.savannahcat.registry;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.ronm19.savannahcat.SavannahCatMod;
import net.ronm19.savannahcat.block.CompanionFurnitureBlock;
import net.ronm19.savannahcat.block.FoodWaterBowlBlock;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SavannahCatMod.MOD_ID);
    public static final DeferredBlock<CompanionFurnitureBlock> SCRATCHING_POST = BLOCKS.registerBlock(
            "scratching_post", CompanionFurnitureBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredBlock<FoodWaterBowlBlock> FOOD_WATER_BOWL = BLOCKS.registerBlock(
            "food_water_bowl", FoodWaterBowlBlock::new,
            p -> p.mapColor(MapColor.TERRACOTTA_RED).strength(1.0F).sound(SoundType.DECORATED_POT).noOcclusion());
    private ModBlocks() {}
}
