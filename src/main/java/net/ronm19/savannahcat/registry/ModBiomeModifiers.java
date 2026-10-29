package net.ronm19.savannahcat.registry;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.ronm19.savannahcat.SavannahCatMod;
import net.ronm19.savannahcat.config.SavannahConfig;

/** NeoForge applies modifiers after loading server config; spawn changes take effect on world restart. */
public final class ModBiomeModifiers {
    public static final DeferredRegister<MapCodec<? extends BiomeModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, SavannahCatMod.MOD_ID);
    public static final MapCodec<ConfiguredSpawns> SPAWNS_CODEC = MapCodec.unit(new ConfiguredSpawns());
    static { SERIALIZERS.register("configured_spawns", () -> SPAWNS_CODEC); }
    private ModBiomeModifiers() {}

    public record ConfiguredSpawns() implements BiomeModifier {
        @Override public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
            if (phase != Phase.ADD || !biome.is(BiomeTags.IS_SAVANNA) || !SavannahConfig.SPAWNING.get()
                    || SavannahConfig.SPAWN_WEIGHT.get() == 0) return;
            int minimum = Math.min(SavannahConfig.SPAWN_MIN.get(), SavannahConfig.SPAWN_MAX.get());
            int maximum = Math.max(SavannahConfig.SPAWN_MIN.get(), SavannahConfig.SPAWN_MAX.get());
            builder.getMobSpawnSettings().addSpawn(MobCategory.CREATURE, SavannahConfig.SPAWN_WEIGHT.get(),
                    new MobSpawnSettings.SpawnerData(ModEntities.SAVANNAH_CAT.get(), minimum, maximum));
        }
        @Override public MapCodec<? extends BiomeModifier> codec() { return SPAWNS_CODEC; }
    }
}
