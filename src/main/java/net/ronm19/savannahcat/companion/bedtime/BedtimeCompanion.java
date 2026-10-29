package net.ronm19.savannahcat.companion.bedtime;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.ronm19.savannahcat.companion.CompanionAction;
import net.ronm19.savannahcat.companion.CompanionCommand;
import net.ronm19.savannahcat.companion.CompanionData.Trait;
import net.ronm19.savannahcat.config.SavannahConfig;
import net.ronm19.savannahcat.entity.SavannahCatEntity;
import net.ronm19.savannahcat.entity.ai.SafetyNavigator;
import org.jspecify.annotations.Nullable;

/** An optional destination/participation helper for the ONE existing CompanionSleep controller. */
public final class BedtimeCompanion {
    public enum Step { INACTIVE, APPROACH, SETTLED, OWNER_WOKE, INVALID }
    private final SavannahCatEntity cat;
    private final SafetyNavigator navigation;
    private BedtimePlaces.@Nullable Bed bed;
    private BedtimePlaces.@Nullable Spot spot;
    private @Nullable UUID observedOwner;
    private @Nullable BlockPos observedBed;
    private boolean observedSleeping, consumed, settled;
    private int lastSleepTimer, decisionCount;
    private String lastReason = "none";
    private long approachUntil, nextValidation, lastHop;

    public BedtimeCompanion(SavannahCatEntity cat, SafetyNavigator navigation) { this.cat = cat; this.navigation = navigation; }

    /** Called even when emergencies or explicit commands take precedence, so a declined entry cannot reroll. */
    public void observe(@Nullable ServerPlayer owner) {
        UUID id = owner == null ? null : owner.getUUID();
        boolean sleeping = owner != null && owner.isSleeping();
        BlockPos at = sleeping ? owner.getSleepingPos().orElse(null) : null;
        int timer = sleeping ? owner.getSleepTimer() : 0;
        if (sleeping && (!observedSleeping || !java.util.Objects.equals(observedOwner, id)
                || !java.util.Objects.equals(observedBed, at) || timer < lastSleepTimer)) consumed = false;
        observedOwner = id; observedSleeping = sleeping; observedBed = at; lastSleepTimer = timer;
    }

    public Step evaluate(@Nullable ServerPlayer owner, long now) {
        observe(owner);
        if (!(cat.level() instanceof ServerLevel level) || owner == null || !cat.isOwnedBy(owner)
                || owner.level() != level || !owner.isAlive() || owner.isSpectator() || !eligible()) return invalid("eligibility");
        if (spot != null) {
            if (bed == null || !bedExists(level, owner, bed)) return invalid("bed_removed");
            if (!owner.isSleeping()) return Step.OWNER_WOKE;
            var currentBed = BedtimePlaces.resolve(level, owner);
            if (currentBed.isEmpty() || !bed.equals(currentBed.get())) return invalid("owner_changed_bed");
            // Re-entering the same bed during the short wake grace continues this accepted entry.
            consumed = true;
            if (now >= nextValidation) {
                nextValidation = now + 10;
                var checked = settled ? actualSpot() : spot;
                if (!BedtimePlaces.sessionSafe(level, cat, owner, bed, checked)) return invalid("spot_unsafe");
            }
            if (!BedtimeReservations.reserve(level, cat.getUUID(), owner.getUUID(), bed, spot, now, SavannahConfig.BEDTIME_MAX_ON_BED.get())) return invalid("reservation");
            if (settled) {
                if (cat.position().distanceToSqr(spot.position()) > .35 * .35) return invalid("moved");
                orient(); return Step.SETTLED;
            }
        } else {
            if (!owner.isSleeping() || consumed) return Step.INACTIVE;
            // One decision for this exact owner bed entry, even if too far or space is unavailable.
            consumed = true;
            if (!cat.onGround() || cat.distanceToSqr(owner) > Math.pow(SavannahConfig.BEDTIME_RANGE.get(), 2)) return Step.INACTIVE;
            decisionCount++;
            double preference = Math.clamp(.75 + cat.getBondLevel() * .035
                    + cat.getPersonality(Trait.SOCIABILITY) * .0025 - cat.getPersonality(Trait.INDEPENDENCE) * .002
                    + (cat.isBaby() ? .12 : 0) - cat.distanceTo(owner) * .008, .35, 1.35);
            double chance = SavannahConfig.BEDTIME_CHANCE.get();
            // 0/1 are explicit deterministic controls; intermediate values remain personality influenced.
            if (chance <= 0 || chance < 1 && cat.getRandom().nextDouble() >= Math.clamp(chance * preference, 0, 1)) return Step.INACTIVE;
            var resolved = BedtimePlaces.resolve(level, owner);
            if (resolved.isEmpty()) return Step.INACTIVE;
            bed = resolved.get();
            boolean topFirst = SavannahConfig.BEDTIME_MAX_ON_BED.get() > 0 && cat.getRandom().nextDouble() < SavannahConfig.BEDTIME_ON_BED.get();
            for (var candidate : BedtimePlaces.candidates(level, cat, owner, bed, topFirst)) {
                if (cat.isStayCommand() && SavannahConfig.BEDTIME_RESPECT_STAY.get()
                        && cat.position().distanceToSqr(candidate.position()) > .10 * .10) continue;
                if (BedtimeReservations.reserve(level, cat.getUUID(), owner.getUUID(), bed, candidate, now, SavannahConfig.BEDTIME_MAX_ON_BED.get())) {
                    spot = candidate; break;
                }
            }
            if (spot == null) { bed = null; lastReason = "no_available_spot"; return Step.INACTIVE; }
            approachUntil = now + 240; nextValidation = now + 10; lastReason = "approaching";
        }
        if (now >= approachUntil) return invalid("approach_timeout");
        double distance = cat.position().distanceToSqr(spot.position());
        if (isHoldingPosition()) {
            cat.getNavigation().stop(); cat.getMoveControl().setWait(); cat.holdSittingPosition(); orient();
            // Traffic may cross an approach, but sleeping starts only after every final visible footprint is clear.
            if (!BedtimePlaces.safe(level, cat, owner, bed, actualSpot())) {
                lastReason = "waiting_for_family";
                cat.getMoveControl().setWantedPosition(spot.position().x, spot.position().y, spot.position().z, .10);
                return Step.APPROACH;
            }
            settled = true; lastReason = "settled"; return Step.SETTLED;
        }
        if (cat.isStayCommand() && SavannahConfig.BEDTIME_RESPECT_STAY.get()) return invalid("stay");
        cat.setSitting(false);
        if (cat.getAction() == CompanionAction.SLEEP || cat.getAction() == CompanionAction.REST) cat.clearAction();
        if (distance < 1.3 * 1.3) {
            cat.getNavigation().stop();
            cat.getMoveControl().setWantedPosition(spot.position().x, spot.position().y, spot.position().z, distance < .08 * .08 ? .10 : distance < .12 ? .25 : .55);
            // Native collision/gravity resolve the small bed step; no teleport, pose rewrite or forced landing.
            if (spot.onBed() && cat.onGround() && spot.position().y - cat.getY() > .2 && now - lastHop > 25) {
                lastHop = now; cat.getJumpControl().jump();
            }
        } else navigation.navigateExact(spot.position(), .85);
        return Step.APPROACH;
    }

    private boolean eligible() {
        return SavannahConfig.BEDTIME.get() && SavannahConfig.SLEEPING.get() && cat.isTamed() && cat.isAlive()
                && (!cat.isBaby() || SavannahConfig.BEDTIME_BABIES.get()) && !cat.isDowned() && !cat.isRecovering()
                && !cat.isPassenger() && !cat.isLeashed() && !cat.isInLiquid() && !cat.isOnFire() && cat.hurtTime == 0
                && !cat.getCompanionBrain().isThreatened() && cat.getTarget() == null
                && !cat.getAction().critical() && !cat.getAction().interaction()
                && !cat.getFamilyData().isInLove(cat.level().getGameTime())
                && cat.getCommand() != CompanionCommand.HUNT && cat.getCommand() != CompanionCommand.HARVEST;
    }
    private boolean bedExists(ServerLevel level, ServerPlayer owner, BedtimePlaces.Bed b) {
        if (!level.hasChunkAt(b.head()) || !level.hasChunkAt(b.foot())) return false;
        var head = level.getBlockState(b.head()); var foot = level.getBlockState(b.foot());
        return head.isBed(level, b.head(), owner) && foot.isBed(level, b.foot(), owner)
                && head.hasProperty(net.minecraft.world.level.block.BedBlock.PART) && foot.hasProperty(net.minecraft.world.level.block.BedBlock.PART)
                && head.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD
                && foot.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.FOOT
                && head.getBedDirection(level, b.head()) == b.facing() && foot.getBedDirection(level, b.foot()) == b.facing();
    }
    private void orient() { cat.setYRot(spot.yaw()); cat.setYBodyRot(spot.yaw()); cat.setYHeadRot(spot.yaw()); }
    private Step invalid(String reason) { if (hasActivity()) lastReason = reason; return hasActivity() ? Step.INVALID : Step.INACTIVE; }
    private BedtimePlaces.Spot actualSpot() {
        return new BedtimePlaces.Spot(cat.position(), spot.yaw(), BedtimePlaces.envelope(cat, cat.position(), spot.yaw()), spot.onBed());
    }
    public boolean hasActivity() { return spot != null; }
    public boolean isHoldingPosition() {
        return spot != null && cat.onGround() && cat.position().distanceToSqr(spot.position()) <= Math.pow(spot.onBed() ? .02 : .06, 2)
                && Math.abs(cat.getY() - spot.position().y) < .035;
    }
    public BedtimePlaces.@Nullable Spot getSpot() { return spot; }
    public BedtimePlaces.@Nullable Bed getBed() { return bed; }
    public boolean isOnBed() { return spot != null && spot.onBed(); }
    public int getDecisionCount() { return decisionCount; }
    public String getLastReason() { return lastReason; }
    public void release() {
        if (spot != null) {
            if (cat.level() instanceof ServerLevel level) BedtimeReservations.release(level, cat.getUUID());
            cat.getNavigation().stop(); cat.getMoveControl().setWait();
        }
        spot = null; bed = null; settled = false; approachUntil = 0; nextValidation = 0;
        // consumed deliberately survives: command/emergency/failed-path exits never reroll this bed entry.
    }
}
