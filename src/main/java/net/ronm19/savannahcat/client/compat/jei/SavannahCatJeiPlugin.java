package net.ronm19.savannahcat.client.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.ronm19.savannahcat.registry.ModItems;

/** Optional client integration discovered by JEI; ordinary crafting recipes remain native recipes. */
@JeiPlugin
public final class SavannahCatJeiPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.fromNamespaceAndPath("savannahcat", "jei_plugin");

    public SavannahCatJeiPlugin() {}

    @Override public Identifier getPluginUid() { return UID; }

    @Override public void registerRecipes(IRecipeRegistration registration) {
        // Server configuration is not read during JEI startup. These pages explain the controls
        // without registering configurable foods or world interactions as crafting recipes.
        registration.addIngredientInfo(ModItems.SAVANNAH_CAT_WHISTLE.get(),
                info("whistle.controls"), info("whistle.targets"), info("whistle.limits"));
        registration.addIngredientInfo(ModItems.SCRATCHING_POST.get(),
                info("post.use"), info("post.home"));
        registration.addIngredientInfo(ModItems.FOOD_WATER_BOWL.get(),
                info("bowl.contents"), info("bowl.food"), info("bowl.ownership"));
        registration.addIngredientInfo(ModItems.SAVANNAH_CAT_SPAWN_EGG.get(),
                info("cat.taming"), info("cat.healing"), info("cat.family"));
    }

    private static Component info(String key) { return Component.translatable("jei.savannahcat." + key); }
}
