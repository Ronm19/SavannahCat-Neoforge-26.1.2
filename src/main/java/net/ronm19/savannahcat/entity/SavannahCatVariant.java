package net.ronm19.savannahcat.entity;

import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.ronm19.savannahcat.SavannahCatMod;

/** Explicit network ids and saved names stay stable if enum declarations are reordered. */
public enum SavannahCatVariant implements StringRepresentable {
    BROWN_SPOTTED(0, "brown"),
    SILVER_SPOTTED(1, "silver"),
    BLACK(2, "black"),
    BLACK_SMOKE(3, "black_smoke");

    private static final SavannahCatVariant[] VARIANTS = values();

    private final int id;
    private final String serializedName;
    private final Identifier texture;

    SavannahCatVariant(int id, String serializedName) {
        this.id = id;
        this.serializedName = serializedName;
        this.texture = Identifier.fromNamespaceAndPath(SavannahCatMod.MOD_ID,
                "textures/entity/savannah_cat/" + serializedName + ".png");
    }

    public int getId() {
        return id;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public Identifier getTexture() {
        return texture;
    }

    public static SavannahCatVariant byId(int id) {
        for (SavannahCatVariant variant : VARIANTS) {
            if (variant.id == id) {
                return variant;
            }
        }
        return BROWN_SPOTTED;
    }

    public static SavannahCatVariant byName(String name) {
        for (SavannahCatVariant variant : VARIANTS) {
            if (variant.serializedName.equals(name)) {
                return variant;
            }
        }
        return BROWN_SPOTTED;
    }

    /** An independent uniform choice for each entity, including members of one spawn group. */
    public static SavannahCatVariant random(RandomSource random) {
        return VARIANTS[random.nextInt(VARIANTS.length)];
    }
}
