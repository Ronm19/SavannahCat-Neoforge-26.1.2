package net.ronm19.savannahcat.registry;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.ronm19.savannahcat.SavannahCatMod;
import net.ronm19.savannahcat.entity.SavannahCatEntity;

public final class ModEntities {
    public static final DeferredRegister.Entities ENTITY_TYPES = DeferredRegister.createEntities(SavannahCatMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<SavannahCatEntity>> SAVANNAH_CAT =
            ENTITY_TYPES.registerEntityType("savannah_cat", SavannahCatEntity::new, MobCategory.CREATURE,
                    builder -> builder.sized(0.6F, 1.6F).eyeHeight(1.15F)
                            .clientTrackingRange(8).noLootTable());

    private ModEntities() {}
}
