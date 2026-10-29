package net.ronm19.savannahcat.entity.ai;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.savannahcat.entity.SavannahCatEntity;

/** Reserves locomotion for the single coordinator. Decisions/actions run once in the entity's server tick. */
public final class CompanionBehaviorGoal extends Goal {
    private final SavannahCatEntity cat;
    public CompanionBehaviorGoal(SavannahCatEntity cat) {
        this.cat = cat;
        setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }
    @Override public boolean canUse() { return cat.isAlive(); }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
}
