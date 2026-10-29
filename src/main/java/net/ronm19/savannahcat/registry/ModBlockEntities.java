package net.ronm19.savannahcat.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.ronm19.savannahcat.SavannahCatMod;
import net.ronm19.savannahcat.block.FoodWaterBowlBlockEntity;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,SavannahCatMod.MOD_ID);
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<FoodWaterBowlBlockEntity>> FOOD_WATER_BOWL=BLOCK_ENTITIES.register(
            "food_water_bowl",()->new BlockEntityType<>(FoodWaterBowlBlockEntity::new,ModBlocks.FOOD_WATER_BOWL.get()));
    private ModBlockEntities() {}
}
