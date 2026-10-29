package net.ronm19.savannahcat.entity.ai;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.ronm19.savannahcat.companion.CompanionAction;
import net.ronm19.savannahcat.companion.CompanionCommand;
import net.ronm19.savannahcat.companion.CompanionData.Trait;
import net.ronm19.savannahcat.config.SavannahConfig;
import net.ronm19.savannahcat.entity.SavannahCatEntity;
import net.ronm19.savannahcat.companion.bedtime.BedtimeCompanion;
import net.ronm19.savannahcat.companion.utility.HomeLifeCoordinator;
import org.jspecify.annotations.Nullable;

/** Bounded, fatigue-driven peaceful sleep. It never changes health, downed timers or glow. */
public final class CompanionSleep {
    private final SavannahCatEntity cat;
    private final SafetyNavigator navigation;
    private final BedtimeCompanion bedtime;
    private boolean bedtimeSleeping;
    private long bedtimeWakeAt;
    private long sleepingUntil;
    private long ownerStillSince;
    private long catStillSince;
    private long lastFatigueTick = -1;
    private long nextSiteSearch;
    private long seekingUntil;
    private long lastWake = Long.MIN_VALUE / 2;
    private @Nullable UUID observedOwner;
    private @Nullable Vec3 previousOwnerPosition;
    private @Nullable Vec3 previousCatPosition;
    private @Nullable Vec3 fatiguePosition;
    private @Nullable Vec3 restingPlace;
    private CompanionCommand observedCommand;

    public CompanionSleep(SavannahCatEntity cat, SafetyNavigator navigation) {
        this.cat = cat;
        this.navigation = navigation;
        bedtime = new BedtimeCompanion(cat, navigation);
        observedCommand = cat.getCommand();
    }

    /** Observe once per coordinator tick, even while another activity has priority. */
    public void observe(@Nullable ServerPlayer owner, long now) {
        bedtime.observe(owner);
        boolean ownerMoved = false;
        if (nearOwner(owner)) {
            if (!owner.getUUID().equals(observedOwner) || previousOwnerPosition == null) ownerStillSince = now;
            else if (owner.position().distanceToSqr(previousOwnerPosition) > .0001
                    || owner.getDeltaMovement().horizontalDistanceSqr() > .0001) {
                ownerStillSince = now;
                ownerMoved = true;
            }
            observedOwner = owner.getUUID();
            previousOwnerPosition = owner.position();
        } else {
            observedOwner = null;
            previousOwnerPosition = null;
            ownerStillSince = now;
        }
        if (previousCatPosition == null || cat.position().distanceToSqr(previousCatPosition) > .0025) catStillSince = now;
        previousCatPosition = cat.position();
        if (observedCommand != cat.getCommand()) {
            wake(now);
            observedCommand = cat.getCommand();
        }
        if (bedtime.hasActivity() || bedtimeSleeping) {
            // Bedtime approaches and brief morning wake delays deliberately extend the ordinary six-block context.
            if (!safeBedtimeContext(owner) || bedtimeSleeping && (!cat.onGround() || cat.getAction() != CompanionAction.SLEEP)) wake(now);
        } else if ((isSleeping() || restingPlace != null) && (ownerMoved || !safeContext(owner)
                || isSleeping() && (now >= sleepingUntil || cat.getAction() != CompanionAction.SLEEP))) wake(now);
        if (lastFatigueTick < 0) { lastFatigueTick = now; fatiguePosition = cat.position(); }
        if (now - lastFatigueTick >= 20) {
            int fatigue = cat.getCompanionData().getFatigue();
            if (isSleeping()) cat.getCompanionData().setFatigue(fatigue - 2);
            else if (!cat.isDowned() && !cat.isRecovering()) {
                boolean moved = fatiguePosition != null && cat.position().distanceToSqr(fatiguePosition) > .16;
                // Activity tires faster; quiet awake time still accumulates fatigue slowly. No experience is awarded.
                if (moved || Math.floorDiv(now, cat.isBaby() ? 120 : 200) != Math.floorDiv(lastFatigueTick, cat.isBaby() ? 120 : 200))
                    cat.getCompanionData().setFatigue(fatigue + (cat.isBaby() && moved ? 2 : 1));
            }
            fatiguePosition = cat.position();
            lastFatigueTick = now;
        }
    }

    /** Optional owner-bed entry is coordinated here; the helper never owns a second sleeping state. */
    public boolean tryBedtime(@Nullable ServerPlayer owner, long now) {
        if (cat.isNoAi() || !safeBedtimeContext(owner)) {
            if (bedtime.hasActivity() || bedtimeSleeping) wake(now);
            return false;
        }
        boolean wasActive = bedtime.hasActivity();
        var step = bedtime.evaluate(owner, now);
        if (step == BedtimeCompanion.Step.INACTIVE || step == BedtimeCompanion.Step.INVALID) {
            if (wasActive || bedtimeSleeping) wake(now);
            return false;
        }
        HomeLifeCoordinator.interrupt(cat);
        restingPlace = null;
        if (step == BedtimeCompanion.Step.APPROACH) {
            if (isSleeping()) {
                sleepingUntil = 0; bedtimeSleeping = false; bedtimeWakeAt = 0;
                if (cat.getAction() == CompanionAction.SLEEP) cat.clearAction();
            }
            cat.setSitting(false);
            return true;
        }
        if (step == BedtimeCompanion.Step.OWNER_WOKE) {
            // A fast vanilla night skip does not force an unfinished approach or a token sleep animation.
            if (!bedtimeSleeping) { wake(now); return false; }
            if (bedtimeWakeAt == 0) bedtimeWakeAt = now + 5 + cat.getRandom().nextInt(26);
            if (now >= bedtimeWakeAt) { wake(now); return false; }
        } else {
            bedtimeWakeAt = 0;
            if (!bedtimeSleeping) {
                bedtimeSleeping = true;
                cat.getCompanionData().setNextSleep(now + Math.max(1200, SavannahConfig.SLEEP_COOLDOWN.get()));
            }
        }
        // Extended multiplayer sleep remains one continuous clip/action, independent of the ordinary nap duration.
        sleepingUntil = now + 40;
        cat.setSitting(false);
        cat.getMoveControl().setWait();
        cat.holdSittingPosition();
        cat.setAction(CompanionAction.SLEEP, 40);
        return true;
    }

    private boolean safeBedtimeContext(@Nullable ServerPlayer owner) {
        return SavannahConfig.SLEEPING.get() && owner != null && cat.isOwnedBy(owner)
                && owner.level() == cat.level() && owner.isAlive() && !owner.isSpectator()
                && cat.isAlive() && !cat.isDowned() && !cat.isRecovering() && !cat.isPassenger() && !cat.isLeashed()
                && !cat.isInLiquid() && !cat.isOnFire() && cat.getAirSupply() >= 100 && cat.hurtTime == 0
                && !cat.getFamilyData().isInLove(cat.level().getGameTime()) && !cat.getCompanionBrain().isThreatened()
                && cat.getTarget() == null && !cat.getAction().critical() && !cat.getAction().interaction()
                && cat.getCommand() != CompanionCommand.HUNT && cat.getCommand() != CompanionCommand.HARVEST;
    }

    /** Called only after survival, defense and owner-return priorities. Owns navigation while finding a resting spot. */
    public boolean tryRest(@Nullable ServerPlayer owner, long now) {
        if (isSleeping()) {
            cat.setSitting(false);
            cat.holdSittingPosition();
            cat.setAction(CompanionAction.SLEEP, 40); // Same action extends its timer without restarting the clip.
            return true;
        }
        if (!safeContext(owner) || now < cat.getCompanionData().getNextSleep()
                || now - (nearOwner(owner) ? ownerStillSince : catStillSince) < Math.max(20, SavannahConfig.SLEEP_OWNER_STILL_TICKS.get() - (cat.isBaby() ? 20 : 0))
                || cat.getCompanionData().getFatigue() < threshold()
                || !relaxedAction()) return false;
        if (restingPlace == null) {
            if (!cat.getNavigation().isDone() || cat.getDeltaMovement().horizontalDistanceSqr() > .001) return false;
            if (now >= nextSiteSearch) {
                nextSiteSearch = now + 200;
                restingPlace = choosePlace(nearOwner(owner) ? owner : null);
                seekingUntil = now + 100;
            }
        }
        if (restingPlace != null && now >= seekingUntil) restingPlace = null;
        if (restingPlace != null && cat.position().distanceToSqr(restingPlace) > .75 * .75) {
            cat.setSitting(false);
            cat.clearAction();
            if (!navigation.navigate(restingPlace, .7)) { restingPlace = null; return false; }
            return true;
        }
        restingPlace = null;
        cat.getNavigation().stop();
        cat.getMoveControl().setWait();
        int minimum = SavannahConfig.SLEEP_MIN_TICKS.get();
        int maximum = Math.max(minimum, SavannahConfig.SLEEP_MAX_TICKS.get());
        double preference = Math.clamp((cat.getCompanionData().getFatigue() + trait(Trait.INDEPENDENCE)
                + 100 - trait(Trait.PLAYFULNESS)) / 300D, 0, 1);
        sleepingUntil = now + minimum + Math.round((maximum - minimum) * preference);
        cat.getCompanionData().setNextSleep(sleepingUntil + Math.max(1200, SavannahConfig.SLEEP_COOLDOWN.get()));
        cat.setSitting(false);
        cat.holdSittingPosition();
        cat.setAction(CompanionAction.SLEEP, 40);
        return true;
    }

    private boolean safeContext(@Nullable ServerPlayer owner) {
        if (!SavannahConfig.SLEEPING.get() || !SavannahConfig.AMBIENT.get() || cat.isDowned() || cat.isRecovering()
                || !cat.isAlive() || !cat.onGround() || cat.isPassenger() || cat.isLeashed()
                || cat.isInLiquid() || cat.isOnFire() || cat.getAirSupply() < 100 || cat.hurtTime > 0
                || cat.getFamilyData().isInLove(cat.level().getGameTime()) || !cat.getCompanionBrain().isSafeToRest()) return false;
        if (cat.getCommand() == CompanionCommand.HUNT || cat.getCommand() == CompanionCommand.HARVEST) return false;
        if (cat.isTamed()) {
            if (nearOwner(owner)) return true;
            if (owner != null && !cat.isStayCommand()) return false;
            var home = cat.getCompanionData().getHome();
            var anchor = cat.getCompanionData().getStayAnchor();
            return home != null && home.dimension().equals(cat.level().dimension()) && home.pos().distSqr(cat.blockPosition()) <= 64
                    || cat.isStayCommand() && anchor != null && anchor.distSqr(cat.blockPosition()) <= 6.25;
        }
        // An unfamiliar nearby visitor keeps a wild cat alert instead of asleep at their feet.
        return cat.level().getNearestPlayer(cat, 8) == null;
    }

    private boolean relaxedAction() {
        return switch (cat.getAction()) {
            case IDLE, REST, SLEEP, GROOM, STRETCH -> true;
            default -> false;
        };
    }

    private boolean nearOwner(@Nullable ServerPlayer owner) { return owner != null && cat.distanceToSqr(owner) <= 36; }
    private int trait(Trait trait) { return cat.getPersonality(trait); }

    private int threshold() {
        int threshold = SavannahConfig.SLEEP_FATIGUE.get();
        if (cat.isBaby()) threshold -= 12;
        threshold -= Math.clamp(cat.getBondLevel() - 1, 0, 4);
        threshold += (trait(Trait.PLAYFULNESS) + trait(Trait.CURIOSITY) - 100) / 15;
        threshold -= (trait(Trait.CONFIDENCE) - 50) / 20;
        if (!cat.level().isBrightOutside()) threshold -= 5;
        if (isCarpet(cat.blockPosition()) || isCarpet(cat.blockPosition().below())) threshold -= 3;
        var home = cat.getCompanionData().getHome();
        if (home != null && home.dimension().equals(cat.level().dimension()) && home.pos().distSqr(cat.blockPosition()) <= 64) threshold -= 3;
        return Math.clamp(threshold, 20, 100);
    }

    private @Nullable Vec3 choosePlace(@Nullable ServerPlayer owner) {
        if (!(cat.level() instanceof ServerLevel level)) return null;
        Vec3 best = null;
        double bestScore = 0;
        BlockPos center = cat.blockPosition();
        var home = cat.getCompanionData().getHome();
        // One small loaded-only scan per 200 ticks. Existing geometry and utility furniture remain untouched.
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            BlockPos pos = center.offset(x, 0, z);
            if (!level.hasChunkAt(pos) || !isCarpet(pos) && !isCarpet(pos.below())) continue;
            BlockPos candidate = isCarpet(pos) ? pos.above() : pos;
            var landing = SafetyNavigator.landingAt(level, cat, candidate);
            if (landing.isEmpty() || owner != null && (landing.get().distanceToSqr(owner.position()) < 2.2 * 2.2
                    || landing.get().distanceToSqr(owner.position()) > 5.5 * 5.5)) continue;
            double score = 8 - cat.position().distanceTo(landing.get()) * .6;
            if (home != null && home.dimension().equals(level.dimension()) && home.pos().distSqr(pos) <= 64) score += 2 + trait(Trait.INDEPENDENCE) * .02;
            if (windowNearby(pos)) score += trait(Trait.CURIOSITY) * .04;
            if (owner != null) score += (6 - landing.get().distanceTo(owner.position())) * trait(Trait.SOCIABILITY) * .01;
            if (score > bestScore) { bestScore = score; best = landing.get(); }
        }
        return best;
    }

    private boolean isCarpet(BlockPos pos) { return cat.level().hasChunkAt(pos) && cat.level().getBlockState(pos).is(BlockTags.WOOL_CARPETS); }
    private boolean windowNearby(BlockPos pos) {
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            BlockPos at = pos.offset(x, 1, z);
            if (!cat.level().hasChunkAt(at)) continue;
            var state = cat.level().getBlockState(at);
            if (state.is(Blocks.GLASS) || state.is(Blocks.GLASS_PANE) || state.getBlock() instanceof net.minecraft.world.level.block.StainedGlassBlock
                    || state.getBlock() instanceof net.minecraft.world.level.block.StainedGlassPaneBlock) return true;
        }
        return false;
    }

    public void wake(long now) {
        if (isSleeping() || restingPlace != null || bedtime.hasActivity() || bedtimeSleeping) {
            cat.getCompanionData().setNextSleep(now + Math.max(1200, SavannahConfig.SLEEP_COOLDOWN.get()));
            lastWake = now;
        }
        sleepingUntil = 0;
        bedtimeSleeping = false;
        bedtimeWakeAt = 0;
        bedtime.release();
        restingPlace = null;
        cat.setSitting(false);
        if (cat.getAction() == CompanionAction.SLEEP) cat.clearAction();
    }
    public boolean isSleeping() { return sleepingUntil > 0; }
    public boolean hasActivity() { return isSleeping() || restingPlace != null || bedtime.hasActivity(); }
    public BedtimeCompanion getBedtime() { return bedtime; }
    public boolean isBedtimeSleeping() { return bedtimeSleeping; }
    public long getBedtimeWakeAt() { return bedtimeWakeAt; }
    public long getSleepingUntil() { return sleepingUntil; }
    public long getOwnerStillSince() { return ownerStillSince; }
    public long getLastWake() { return lastWake; }
}
