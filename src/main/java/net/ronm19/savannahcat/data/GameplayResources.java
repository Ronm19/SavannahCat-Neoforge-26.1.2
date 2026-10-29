package net.ronm19.savannahcat.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.ronm19.savannahcat.SavannahCatMod;
import net.ronm19.savannahcat.registry.ModSounds;

/** Canonical source for every mechanical JSON. Approved textures and Gecko artwork stay hand-authored. */
@EventBusSubscriber(modid = SavannahCatMod.MOD_ID)
public final class GameplayResources implements DataProvider {
    private final Path output;
    public GameplayResources(PackOutput output) { this.output = output.getOutputFolder(); }
    @SubscribeEvent public static void gather(GatherDataEvent.Client event) { event.createProvider(GameplayResources::new); }
    @Override public String getName() { return "Savannah Cat language, recipes, loot, tags and native models"; }

    @Override public CompletableFuture<?> run(CachedOutput cache) {
        var resources = resources();
        return CompletableFuture.allOf(resources.entrySet().stream()
                .map(entry -> DataProvider.saveStable(cache, entry.getValue(), output.resolve(entry.getKey())))
                .toArray(CompletableFuture[]::new));
    }

    public static Map<String, JsonElement> resources() {
        var result = new LinkedHashMap<String, JsonElement>();
        var language = new JsonObject();
        var words = Map.ofEntries(
            Map.entry("entity.savannahcat.savannah_cat", "Savannah Cat"),
            Map.entry("item.savannahcat.savannah_cat_spawn_egg", "Savannah Cat Spawn Egg"),
            Map.entry("item.savannahcat.savannah_cat_whistle", "Savannah Cat Whistle"),
            Map.entry("block.savannahcat.scratching_post", "Savannah Cat Scratching Post"),
            Map.entry("block.savannahcat.food_water_bowl", "Savannah Cat Food and Water Bowl"),
            Map.entry("jei.savannahcat.whistle.controls", "Crouch-use the whistle in the air to cycle Follow, Stay, Hunt, Harvest and Recall. Use it normally to issue the selected order."),
            Map.entry("jei.savannahcat.whistle.targets", "Aim at a cat you own for an individual order. Otherwise the selected mode chooses eligible owned companions. Recall cannot reach cats in unloaded chunks."),
            Map.entry("jei.savannahcat.whistle.limits", "Commands respect server settings, safety rules and range limits. Kittens cannot Hunt or Harvest."),
            Map.entry("jei.savannahcat.post.use", "Place the post in a safe area near your cats. Owned cats may approach and scratch it when free to relax; furniture activities follow server settings."),
            Map.entry("jei.savannahcat.post.home", "Crouch-use the post with an empty hand or a whistle to assign it as home for your nearest owned cat in range. Home assignment must be enabled on the server."),
            Map.entry("jei.savannahcat.bowl.contents", "Use an accepted food on the bowl to add one item, or a water bottle to add one water serving. Water buckets fill the water section when enabled. In Survival, empty bottles and buckets are returned."),
            Map.entry("jei.savannahcat.bowl.food", "Food must be edible and allowed by the server's healing-food list. The bowl holds one food type, up to 16 items, and up to 8 water servings. Refilling and visits must be enabled on the server."),
            Map.entry("jei.savannahcat.bowl.ownership", "A bowl placed or claimed by you is reserved for your cats. Its contents are optional comfort, not a survival requirement. Crouch-use with an empty hand or a whistle to assign home to your nearest owned cat in range."),
            Map.entry("jei.savannahcat.cat.taming", "When taming is enabled, feed a wild Savannah Cat an item from the server's taming-food list. Taming may take several attempts. These food settings can differ between servers."),
            Map.entry("jei.savannahcat.cat.healing", "When healing is enabled, feed an injured cat you own a food from the server's healing-food list. Healing takes priority over breeding. Crouch-feed a Downed cat you own an accepted healing food to revive it before its rescue time expires."),
            Map.entry("jei.savannahcat.cat.family", "Crouch-feed raw cod or raw salmon to safe, fully healthy adults for breeding, or to your kitten to help it grow. These interactions follow server settings and cooldowns. Only the owner can feed a tamed cat for breeding or growth; cats belonging to different players cannot breed."),
            Map.entry("message.savannahcat.tamed", "Your Savannah Cat trusts you!"),
            Map.entry("message.savannahcat.follow", "Savannah Cat: Follow"),
            Map.entry("message.savannahcat.stay", "Savannah Cat: Stay"),
            Map.entry("message.savannahcat.downed", "Your Savannah Cat is downed! Crouch and feed it to revive."),
            Map.entry("message.savannahcat.revived", "Your Savannah Cat is recovering. Keep it safe."),
            Map.entry("message.savannahcat.health", "Savannah Cat health: %s / %s HP"),
            Map.entry("message.savannahcat.home_set", "Home assigned to %s."),
            Map.entry("message.savannahcat.home_return", "Your Savannah Cat is returning home."),
            Map.entry("message.savannahcat.home_missing", "No home set. Crouch-use a compass on your cat or use home furniture."),
            Map.entry("message.savannahcat.no_owned_cat", "No owned Savannah Cat is nearby."),
            Map.entry("message.savannahcat.whistle_mode", "Whistle: %s. Use to issue; crouch-use to cycle."),
            Map.entry("message.savannahcat.whistle_result", "%s: %s accepted, %s unavailable"),
            Map.entry("message.savannahcat.hunt.searching", "Looking for wild prey."),
            Map.entry("message.savannahcat.hunt.pursuing", "Pursuing prey."),
            Map.entry("message.savannahcat.hunt.cooldown", "Catch cooldown: %s s"),
            Map.entry("message.savannahcat.hunt.no_prey", "No suitable wild prey nearby."),
            Map.entry("message.savannahcat.hunt.protected", "Protected animals; searching."),
            Map.entry("message.savannahcat.hunt.population", "Preserving wild population."),
            Map.entry("message.savannahcat.hunt.returning", "Returning closer to you."),
            Map.entry("message.savannahcat.hunt.busy", "Waiting until safe."),
            Map.entry("message.savannahcat.hunt.group_result", "%s pursuing, %s searching, %s waiting"),
            Map.entry("message.savannahcat.recall_none", "Recall: no accessible owned cats. Cats in unloaded chunks cannot answer."),
            Map.entry("message.savannahcat.bowl_owner", "This bowl belongs to another player."),
            Map.entry("message.savannahcat.bowl_contents", "Bowl: %s food, %s water. Use food or water to refill."),
            Map.entry("message.savannahcat.family.love","Ready to breed. Bring another safe adult nearby."),
            Map.entry("message.savannahcat.family.growth","Your kitten is growing."),
            Map.entry("message.savannahcat.family.owner_denied","Only this cat's owner may feed it for breeding or growth."),
            Map.entry("message.savannahcat.family.cooldown","This cat needs more time before breeding or growth feeding."),
            Map.entry("message.savannahcat.family.unsafe","This cat needs full health and a safe place first."),
            Map.entry("message.savannahcat.family.already_in_love","This cat is already ready to breed."),
            Map.entry("message.savannahcat.family.no_growth","This kitten cannot use growth food right now."),
            Map.entry("message.savannahcat.family.not_applicable","Crouch-use raw cod or salmon for breeding or growth."),
            Map.entry("command.savannahcat.follow", "Follow"), Map.entry("command.savannahcat.stay", "Stay / Sit"),
            Map.entry("command.savannahcat.hunt", "Hunt"), Map.entry("command.savannahcat.harvest", "Harvest"),
            Map.entry("command.savannahcat.recall", "Recall"));
        words.forEach(language::addProperty);
        var sounds=new JsonObject();
        for(var cue:ModSounds.Cue.values()) {
            language.addProperty(cue.subtitleKey(),cue.subtitle);
            var definition=new JsonObject();definition.addProperty("subtitle",cue.subtitleKey());
            var reference=new JsonObject();reference.addProperty("name","minecraft:"+cue.vanilla);reference.addProperty("type","event");
            if(cue==ModSounds.Cue.GROWL)reference.addProperty("pitch",.72);
            if(cue==ModSounds.Cue.SLEEPING)reference.addProperty("pitch",.9);
            var entries=new JsonArray();
            if(cue==ModSounds.Cue.AMBIENT)reference.addProperty("weight",3);
            entries.add(reference);
            if(cue==ModSounds.Cue.AMBIENT){var trill=new JsonObject();trill.addProperty("name","minecraft:entity.cat.beg_for_food");trill.addProperty("type","event");trill.addProperty("weight",1);entries.add(trill);}
            definition.add("sounds",entries);sounds.add(cue.path(),definition);
        }
        result.put("assets/savannahcat/lang/en_us.json", language);
        result.put("assets/savannahcat/sounds.json",sounds);
        add(result, "assets/savannahcat/items/savannah_cat_spawn_egg.json", """
            {"model":{"type":"minecraft:model","model":"savannahcat:item/savannah_cat_spawn_egg"}}
            """);
        add(result, "assets/savannahcat/models/item/savannah_cat_spawn_egg.json", """
            {"parent":"minecraft:item/generated","textures":{"layer0":"savannahcat:item/savannah_cat_spawn_egg"}}
            """);
        for (String name : new String[]{"scratching_post", "food_water_bowl", "savannah_cat_whistle"}) {
            String model = name.equals("savannah_cat_whistle") ? "item/" + name : "block/" + name;
            add(result, "assets/savannahcat/items/" + name + ".json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"savannahcat:" + model + "\"}}");
            if (!name.equals("savannah_cat_whistle")) {
                if(name.equals("food_water_bowl"))add(result,"assets/savannahcat/blockstates/food_water_bowl.json","""
                    {"variants":{
                      "has_food=false,has_water=false":{"model":"savannahcat:block/food_water_bowl_empty"},
                      "has_food=true,has_water=false":{"model":"savannahcat:block/food_water_bowl_food"},
                      "has_food=false,has_water=true":{"model":"savannahcat:block/food_water_bowl_water"},
                      "has_food=true,has_water=true":{"model":"savannahcat:block/food_water_bowl"}}}
                    """);
                else add(result, "assets/savannahcat/blockstates/" + name + ".json", "{\"variants\":{\"\":{\"model\":\"savannahcat:block/" + name + "\"}}}");
                add(result, "data/savannahcat/loot_table/blocks/" + name + ".json", """
                    {"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"savannahcat:%s"}],"conditions":[{"condition":"minecraft:survives_explosion"}]}]}
                    """.formatted(name));
            }
        }
        result.put("assets/savannahcat/models/block/scratching_post.json", model(Map.of("wood", "minecraft:block/oak_planks", "rope", "minecraft:block/white_wool"),
                cube(new int[]{1,0,1},new int[]{15,2,15},"wood"), cube(new int[]{6,2,6},new int[]{10,15,10},"rope"),
                cube(new int[]{5,15,5},new int[]{11,16,11},"wood")));
        result.put("assets/savannahcat/models/block/food_water_bowl.json",bowlModel(true,true));
        result.put("assets/savannahcat/models/block/food_water_bowl_empty.json",bowlModel(false,false));
        result.put("assets/savannahcat/models/block/food_water_bowl_food.json",bowlModel(true,false));
        result.put("assets/savannahcat/models/block/food_water_bowl_water.json",bowlModel(false,true));
        var whistle = model(Map.of("metal","minecraft:block/iron_block","copper","minecraft:block/copper_block","hole","minecraft:block/black_concrete"),
                cube(new int[]{3,4,6},new int[]{11,9,10},"metal"), cube(new int[]{10,5,6},new int[]{16,8,10},"copper"),
                cube(new int[]{7,9,7},new int[]{10,10,9},"hole"), cube(new int[]{0,5,7},new int[]{3,8,9},"copper"));
        add(result,"assets/savannahcat/models/item/savannah_cat_whistle.json",whistle.toString());
        recipe(result, "savannah_cat_whistle", new String[]{" II"," IC","S  "}, Map.of("I","minecraft:iron_ingot","C","minecraft:copper_ingot","S","minecraft:string"));
        recipe(result, "scratching_post", new String[]{" W "," S ","PPP"}, Map.of("W","minecraft:white_wool","S","minecraft:stick","P","minecraft:oak_planks"));
        recipe(result, "food_water_bowl", new String[]{"B B","BBB"}, Map.of("B","minecraft:brick"));
        add(result,"data/savannahcat/neoforge/biome_modifier/savannah_cat_spawns.json", "{\"type\":\"savannahcat:configured_spawns\"}");
        add(result,"data/savannahcat/loot_table/entities/savannah_cat.json", "{\"type\":\"minecraft:entity\",\"pools\":[]}");
        add(result,"data/minecraft/tags/block/mineable/axe.json", "{\"replace\":false,\"values\":[\"savannahcat:scratching_post\"]}");
        add(result,"data/minecraft/tags/block/mineable/pickaxe.json", "{\"replace\":false,\"values\":[\"savannahcat:food_water_bowl\"]}");
        return result;
    }

    private static void add(Map<String,JsonElement> result,String path,String json) { result.put(path,JsonParser.parseString(json)); }
    private static JsonObject bowlModel(boolean food,boolean water) {
        var parts=new ArrayList<JsonObject>();
        parts.add(cube(new int[]{2,0,2},new int[]{14,2,14},"pot"));parts.add(cube(new int[]{2,2,2},new int[]{14,4,3},"pot"));
        parts.add(cube(new int[]{2,2,13},new int[]{14,4,14},"pot"));parts.add(cube(new int[]{2,2,3},new int[]{3,4,13},"pot"));
        parts.add(cube(new int[]{13,2,3},new int[]{14,4,13},"pot"));parts.add(cube(new int[]{7,2,3},new int[]{8,4,13},"pot"));
        if(water)parts.add(cube(new int[]{3,2,3},new int[]{7,3,13},"water"));
        if(food)parts.add(cube(new int[]{8,2,3},new int[]{13,3,13},"food"));
        return model(Map.of("pot","minecraft:block/terracotta","water","minecraft:block/blue_terracotta","food","minecraft:block/brown_terracotta"),parts.toArray(JsonObject[]::new));
    }
    private static JsonArray numbers(int[] values) { var out=new JsonArray();for(int value:values)out.add(value);return out; }
    private static JsonObject cube(int[] from,int[] to,String texture) {
        var cube=new JsonObject();cube.add("from",numbers(from));cube.add("to",numbers(to));var faces=new JsonObject();
        for(String face:new String[]{"north","south","east","west","up","down"}) {var data=new JsonObject();data.addProperty("texture","#"+texture);faces.add(face,data);}
        cube.add("faces",faces);return cube;
    }
    private static JsonObject model(Map<String,String> textures,JsonObject... elements) {
        var result=new JsonObject();result.addProperty("parent","minecraft:block/block");
        var tex=new JsonObject();textures.forEach(tex::addProperty);String particle=textures.containsKey("wood")?textures.get("wood"):textures.containsKey("pot")?textures.get("pot"):textures.get("metal");tex.addProperty("particle",particle);result.add("textures",tex);
        var cubes=new JsonArray();for(var element:elements)cubes.add(element);result.add("elements",cubes);return result;
    }
    private static void recipe(Map<String,JsonElement> result,String name,String[] rows,Map<String,String> ingredients) {
        var recipe=new JsonObject();recipe.addProperty("type","minecraft:crafting_shaped");recipe.addProperty("category","misc");
        var pattern=new JsonArray();for(String row:rows)pattern.add(row);recipe.add("pattern",pattern);
        var key=new JsonObject();ingredients.forEach(key::addProperty);recipe.add("key",key);
        var product=new JsonObject();product.addProperty("id","savannahcat:"+name);product.addProperty("count",1);recipe.add("result",product);
        result.put("data/savannahcat/recipe/"+name+".json",recipe);
    }
}
