package net.ronm19.savannahcat.client.model;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.ronm19.savannahcat.entity.SavannahCatEntity;
import net.ronm19.savannahcat.entity.SavannahCatVariant;
import net.ronm19.savannahcat.companion.CompanionAction;
import org.jspecify.annotations.Nullable;

/** Separate authored adult/kitten assets; adult artwork is never rescaled or repainted. */
public final class SavannahCatModel extends GeoModel<SavannahCatEntity> {
    public static final Identifier ASSET = Identifier.fromNamespaceAndPath("savannahcat", "entity/savannah_cat");
    public static final Identifier BABY_ASSET = Identifier.fromNamespaceAndPath("savannahcat", "entity/savannah_cat_baby");
    public static final DataTicket<Identifier> COAT_TEXTURE = DataTicket.create("savannahcat:coat_texture", Identifier.class);
    public static final DataTicket<Boolean> BABY_MODEL = DataTicket.create("savannahcat:baby_model", Boolean.class);
    private static final Map<SavannahCatVariant, Identifier> SLEEPING_TEXTURES = sleepingTextures();
    private static final Map<SavannahCatVariant, Identifier> BABY_TEXTURES = babyTextures(false);
    private static final Map<SavannahCatVariant, Identifier> BABY_SLEEPING_TEXTURES = babyTextures(true);

    private static Map<SavannahCatVariant, Identifier> babyTextures(boolean sleeping) {
        var textures=new EnumMap<SavannahCatVariant, Identifier>(SavannahCatVariant.class);
        for(var variant:SavannahCatVariant.values()) textures.put(variant, Identifier.fromNamespaceAndPath("savannahcat",
            "textures/entity/savannah_cat_baby/"+variant.getSerializedName()+(sleeping?"_sleeping":"")+".png"));
        return Map.copyOf(textures);
    }

    private static Map<SavannahCatVariant, Identifier> sleepingTextures() {
        var textures=new EnumMap<SavannahCatVariant, Identifier>(SavannahCatVariant.class);
        for(var variant:SavannahCatVariant.values()) textures.put(variant, Identifier.fromNamespaceAndPath("savannahcat",
            "textures/entity/savannah_cat/"+variant.getSerializedName()+"_sleeping.png"));
        return Map.copyOf(textures);
    }

    /** Closed eyes belong only to genuine peaceful sleep, regardless of ownership. */
    public static Identifier selectTexture(SavannahCatVariant variant, CompanionAction action, boolean downed) {
        return selectTexture(variant,action,downed,false);
    }

    public static Identifier selectTexture(SavannahCatVariant variant, CompanionAction action, boolean downed,boolean baby) {
        boolean sleeping=action==CompanionAction.SLEEP && !downed;
        return baby ? (sleeping?BABY_SLEEPING_TEXTURES:BABY_TEXTURES).get(variant)
            : sleeping?SLEEPING_TEXTURES.get(variant):variant.getTexture();
    }

    @Override
    public Identifier getModelResource(GeoRenderState state) { return state.getOrDefaultGeckolibData(BABY_MODEL,false)?BABY_ASSET:ASSET; }
    @Override
    public Identifier getAnimationResource(SavannahCatEntity entity) { return entity.isBaby()?BABY_ASSET:ASSET; }
    @Override
    public Identifier getTextureResource(GeoRenderState state) {
        return state.getOrDefaultGeckolibData(COAT_TEXTURE, SavannahCatVariant.BROWN_SPOTTED.getTexture());
    }
    @Override
    public void addAdditionalStateData(SavannahCatEntity entity, @Nullable Object related, GeoRenderState state) {
        state.addGeckolibData(BABY_MODEL,entity.isBaby());
        state.addGeckolibData(COAT_TEXTURE, selectTexture(entity.getVariant(),entity.getAction(),entity.isDowned(),entity.isBaby()));
    }
}
