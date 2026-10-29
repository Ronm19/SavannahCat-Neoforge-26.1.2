package net.ronm19.savannahcat;

import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.ronm19.savannahcat.entity.SavannahCatEntity;
import net.ronm19.savannahcat.registry.ModEntities;
import net.ronm19.savannahcat.registry.ModBlocks;
import net.ronm19.savannahcat.registry.ModBiomeModifiers;
import net.ronm19.savannahcat.config.SavannahConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.ronm19.savannahcat.registry.ModItems;

/** Common entrypoint; client registration is isolated in the client package. */
@Mod(SavannahCatMod.MOD_ID)
public final class SavannahCatMod {
    public static final String MOD_ID = "savannahcat";

    public SavannahCatMod(IEventBus modEventBus,ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER,SavannahConfig.SPEC);
        ModBlocks.BLOCKS.register(modEventBus);
        net.ronm19.savannahcat.registry.ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModBiomeModifiers.SERIALIZERS.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        net.ronm19.savannahcat.registry.ModSounds.SOUNDS.register(modEventBus);
        modEventBus.addListener(SavannahCatMod::createAttributes);
        modEventBus.addListener(SavannahCatMod::registerSpawnPlacements);
        modEventBus.addListener(SavannahCatMod::addCreativeItems);
    }

    private static void createAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.SAVANNAH_CAT.get(), SavannahCatEntity.createAttributes().build());
    }

    private static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(ModEntities.SAVANNAH_CAT.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, SavannahCatEntity::checkSavannahCatSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private static void addCreativeItems(BuildCreativeModeTabContentsEvent event) {
        if(event.getTabKey()==CreativeModeTabs.TOOLS_AND_UTILITIES){event.accept(ModItems.SAVANNAH_CAT_WHISTLE);}
        if(event.getTabKey()==CreativeModeTabs.FUNCTIONAL_BLOCKS){event.accept(ModItems.SCRATCHING_POST);event.accept(ModItems.FOOD_WATER_BOWL);}
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.SAVANNAH_CAT_SPAWN_EGG);
        }
    }
}
