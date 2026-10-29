package net.ronm19.savannahcat.companion.utility;

import java.util.ArrayList;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import net.ronm19.savannahcat.config.SavannahConfig;
import net.ronm19.savannahcat.entity.SavannahCatEntity;

/** Phantom's vanilla flight control follows moveTargetPoint, rather than ground navigation. */
public final class PhantomDeterrenceGoal extends Goal {
    private final Phantom phantom;
    private int nextScan, fleeUntil;
    private Vec3 destination;
    public PhantomDeterrenceGoal(Phantom phantom) { this.phantom = phantom; setFlags(EnumSet.of(Flag.MOVE)); }
    @Override public boolean canUse() {
        if (!SavannahConfig.DETERRENCE.get() || phantom.tickCount < nextScan) return false;
        nextScan = phantom.tickCount + 20;
        var cats = new ArrayList<SavannahCatEntity>();
        phantom.level().getEntities(EntityTypeTest.forClass(SavannahCatEntity.class), phantom.getBoundingBox().inflate(16.0D),
                cat -> cat.isAlive() && !cat.isDowned(), cats, 8);
        if (cats.isEmpty()) return false;
        Vec3 away = phantom.position().subtract(cats.getFirst().position());
        if (away.horizontalDistanceSqr() < 0.01D) away = new Vec3(1, 0, 0);
        destination = phantom.position().add(away.normalize().scale(12.0D)).add(0, 3, 0);
        var box = phantom.getBoundingBox().move(destination.subtract(phantom.position()));
        if (!phantom.level().hasChunkAt(net.minecraft.core.BlockPos.containing(destination))
                || !phantom.level().getWorldBorder().isWithinBounds(box)
                || !phantom.level().noCollision(phantom, box) || phantom.level().containsAnyLiquid(box)) return false;
        return true;
    }
    @Override public void start() { phantom.setTarget(null); fleeUntil = phantom.tickCount + 40; }
    @Override public boolean canContinueToUse() { return SavannahConfig.DETERRENCE.get() && phantom.tickCount < fleeUntil; }
    @Override public void tick() {
        phantom.setTarget(null);
        phantom.moveTargetPoint = destination;
    }
    @Override public boolean requiresUpdateEveryTick() { return true; }
}
