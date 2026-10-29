package net.ronm19.savannahcat.entity.ai;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.ronm19.savannahcat.companion.utility.HomeLifeState;
import net.ronm19.savannahcat.entity.SavannahCatEntity;

/** Reserve native LOOK while furniture contact or a settled bedtime pose depends on a fixed orientation. */
public final class HomeLifeLookGoal extends Goal {
    private final SavannahCatEntity cat;
    public HomeLifeLookGoal(SavannahCatEntity cat) {
        this.cat = cat;
        setFlags(EnumSet.of(Flag.LOOK));
    }
    @Override public boolean canUse() {
        return cat.isAlive() && !cat.isDowned()
                && (cat.getCompanionData().getUtilityData().homeLife.phase == HomeLifeState.Phase.USE
                    || cat.getCompanionBrain().getSleep().getBedtime().isHoldingPosition());
    }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() { tick(); }
    @Override public void tick() {
        var sleep = cat.getCompanionBrain().getSleep();
        if (sleep.getBedtime().isHoldingPosition()) {
            var spot = sleep.getBedtime().getSpot();
            if (spot != null) {
                var forward = net.minecraft.world.phys.Vec3.directionFromRotation(0, spot.yaw());
                cat.setYRot(spot.yaw()); cat.setYBodyRot(spot.yaw()); cat.setYHeadRot(spot.yaw());
                cat.getLookControl().setLookAt(cat.getX() + forward.x * 2, cat.getEyeY(), cat.getZ() + forward.z * 2);
            }
            return;
        }
        var state = cat.getCompanionData().getUtilityData().homeLife;
        if (state.target == null) return;
        var target = state.window == null ? state.target : state.window;
        cat.getLookControl().setLookAt(target.getX() + .5,
                target.getY() + (state.kind == HomeLifeState.Kind.POST ? 1.15 : .2), target.getZ() + .5);
    }
}
