package net.ronm19.savannahcat.companion.utility;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.ronm19.savannahcat.config.SavannahConfig;
import net.ronm19.savannahcat.entity.SavannahCatEntity;

/** A single strongest modest rod-wear benefit; normal fishing loot remains untouched. */
public final class FishingAssistance {
    private FishingAssistance() {}
    public static double strongestBonus(ServerPlayer owner, Iterable<SavannahCatEntity> cats) {
        if (!SavannahConfig.FISHING.get()) return 0.0D;
        double strongest = 0.0D, radius = SavannahConfig.FISHING_RADIUS.get();
        for (var cat : cats) {
            if (cat.level() != owner.level() || !cat.isOwnedBy(owner) || !cat.isAlive() || cat.isDowned() || cat.isRecovering()
                    || cat.distanceToSqr(owner) > radius * radius || !owner.hasLineOfSight(cat)) continue;
            strongest = Math.max(strongest, Math.min(SavannahConfig.MAX_FISHING_BONUS.get(),
                    SavannahConfig.FISHING_BONUS.get() + 0.01D * (cat.getBondLevel() - 1)));
        }
        return strongest;
    }
    public static int rodDamage(int vanillaDamage, double strongestBonus, double randomUnit) {
        return vanillaDamage > 0 && randomUnit < strongestBonus ? vanillaDamage - 1 : vanillaDamage;
    }
    public static int biteWait(int vanillaWaitTicks, double strongestBonus) {
        return vanillaWaitTicks <= 0 ? vanillaWaitTicks
                : Math.max(1, (int)Math.round(vanillaWaitTicks * (1.0D - Math.clamp(strongestBonus, 0.0D, 0.25D))));
    }
}
