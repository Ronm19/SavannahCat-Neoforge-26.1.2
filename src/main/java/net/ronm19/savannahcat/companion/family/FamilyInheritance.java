package net.ronm19.savannahcat.companion.family;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.ronm19.savannahcat.companion.CompanionData.Trait;
import net.ronm19.savannahcat.entity.SavannahCatEntity;
import net.ronm19.savannahcat.entity.SavannahCatVariant;
import org.jspecify.annotations.Nullable;

/** Predictable parental coats; biological relationships and permanent ownership are separate facts. */
public final class FamilyInheritance {
    public record Birth(SavannahCatVariant coat, @Nullable UUID owner, DyeColor collar, @Nullable GlobalPos home,
            UUID parentA, UUID parentB, UUID guardian, int[] traits) {}
    private FamilyInheritance() {}
    public static boolean compatibleOwners(SavannahCatEntity a, SavannahCatEntity b) {
        return a.getOwnerUUID() == null || b.getOwnerUUID() == null || Objects.equals(a.getOwnerUUID(), b.getOwnerUUID());
    }
    public static SavannahCatVariant chooseCoat(SavannahCatVariant a, SavannahCatVariant b, RandomSource random) {
        return a == b ? a : random.nextBoolean() ? a : b;
    }
    public static Birth plan(SavannahCatEntity a, SavannahCatEntity b, RandomSource random) {
        if (!compatibleOwners(a, b)) throw new IllegalArgumentException("Cross-owner breeding is not allowed");
        SavannahCatEntity donor = random.nextBoolean() ? a : b;
        SavannahCatEntity owned = donor.isTamed() ? donor : a.isTamed() ? a : b;
        UUID owner = a.getOwnerUUID() == null ? b.getOwnerUUID() : a.getOwnerUUID();
        int[] traits = new int[Trait.values().length];
        for (var trait : Trait.values()) traits[trait.ordinal()] = Math.clamp((a.getCompanionData().getPersonality(trait)
                + b.getCompanionData().getPersonality(trait)) / 2 + random.nextInt(17) - 8, 0, 100);
        return new Birth(donor.getVariant(), owner, owner == null ? DyeColor.RED : owned.getCollarColor(),
                owner == null ? null : owned.getCompanionData().getHome(), a.getUUID(), b.getUUID(),
                owner == null ? a.getUUID() : owned.getUUID(), traits);
    }
}
