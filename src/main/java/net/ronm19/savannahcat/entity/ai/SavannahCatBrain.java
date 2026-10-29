package net.ronm19.savannahcat.entity.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.ronm19.savannahcat.companion.CompanionAction;
import net.ronm19.savannahcat.companion.CompanionCommand;
import net.ronm19.savannahcat.companion.CompanionData;
import net.ronm19.savannahcat.companion.CompanionData.Trait;
import net.ronm19.savannahcat.companion.utility.CompanionUtilities;
import net.ronm19.savannahcat.companion.family.BreedingService;
import net.ronm19.savannahcat.companion.family.FamilyAI;
import net.ronm19.savannahcat.config.SavannahConfig;
import net.ronm19.savannahcat.entity.SavannahCatEntity;
import org.jspecify.annotations.Nullable;

/**
 * One server-authoritative perceive/remember/evaluate/decide/act coordinator.
 * Sensors are staggered and capped; only the selected activity writes navigation.
 * This is an equivalent coordinator, not a claim to use Minecraft's native Brain.
 */
public final class SavannahCatBrain {
    public enum State { WILD, FOLLOW_OWNER, STAY, RETREAT, OWNER_UNAVAILABLE, DOWNED, DEFEND, HUNT, HARVEST, REST }
    public enum HuntStatus {
        SEARCHING, PURSUING, COOLDOWN, NO_PREY, PROTECTED, POPULATION, RETURNING, BUSY;
        public String translationKey() { return "message.savannahcat.hunt." + name().toLowerCase(java.util.Locale.ROOT); }
    }
    public enum Activity { OBSERVE, EMERGENCY, DOWNED, DEFEND, STAY, HUNT, HARVEST, FOLLOW, RETURN_HOME,
        RECOVER, REST, SLEEP, HOME_LIFE, GROOM, STRETCH, PLAY, SOCIAL, EXPLORE, OWNER_UNAVAILABLE, FAMILY_FOLLOW, COURTSHIP, PARENT_CARE, BEDTIME }
    private final SavannahCatEntity cat;
    private final SafetyNavigator navigator;
    private final FelineCombat combat;
    private final CompanionSleep sleep;
    private final FamilyAI family;
    private final ExpiringMemory<UUID> recentAttacker = new ExpiringMemory<>();
    private final ExpiringMemory<UUID> threatMemory = new ExpiringMemory<>();
    private final ExpiringMemory<UUID> preyMemory = new ExpiringMemory<>();
    private final ExpiringMemory<UUID> failedPrey = new ExpiringMemory<>();
    private final ExpiringMemory<Vec3> ownerPosition = new ExpiringMemory<>();
    private final ExpiringMemory<Vec3> dangerousArea = new ExpiringMemory<>();
    private final ExpiringMemory<Vec3> safeRetreat = new ExpiringMemory<>();
    private final ExpiringMemory<Vec3> lastSuccessfulPath = new ExpiringMemory<>();
    private final ExpiringMemory<Vec3> homeReturn = new ExpiringMemory<>();
    private final List<UUID> perceived = new ArrayList<>();
    private ThreatAssessment assessment = ThreatAssessment.CALM;
    private Activity activity = Activity.OBSERVE;
    private long lastTick = Long.MIN_VALUE;
    private long nextSense;
    private long nextPreySearch;
    private long nextAmbient;
    private long ambientUntil;
    private long nextRetreatPath;
    private long huntingRetry;
    private int nextPreyCandidate;
    private long nextPreyValidation;
    private long nextRequestedHuntRefresh;
    private HuntStatus huntSearchStatus = HuntStatus.SEARCHING;
    private int sensorPasses;
    private int maximumPerceived;
    private boolean following;
    private boolean manualRest;
    private long externalMovementUntil;
    private @Nullable Vec3 ambientDestination;
    private boolean homeLifePaused;
    private Activity pausedActivity = Activity.OBSERVE;
    private CompanionAction pausedAction = CompanionAction.IDLE;
    private @Nullable Vec3 pausedDestination;
    private long pausedAmbientTicks;

    public SavannahCatBrain(SavannahCatEntity cat) {
        this.cat = cat;
        navigator = new SafetyNavigator(cat);
        combat = new FelineCombat(cat, navigator);
        sleep = new CompanionSleep(cat, navigator);
        family = new FamilyAI(cat, navigator);
    }

    public void rememberThreat(LivingEntity attacker) {
        if (!attacker.isAlive() || (attacker instanceof Player player ? cat.isOwnedBy(player)
                : CompanionRelations.isFriendly(cat, attacker))) return;
        long now = cat.level().getGameTime();
        recentAttacker.remember(attacker.getUUID(), now, 100);
        dangerousArea.remember(attacker.position(), now, 200);
        nextSense = 0;
    }

    public void tick() {
        if (!(cat.level() instanceof ServerLevel level) || lastTick == level.getGameTime()) return;
        long now = level.getGameTime();
        lastTick = now;
        navigator.noteProgress();
        expireMemories(now);
        if (nextSense == 0 || now >= nextSense) perceive(level, now);
        ServerPlayer owner = cat.getOwnerPlayer();
        sleep.observe(owner, now);
        if (owner != null) ownerPosition.remember(owner.position(), now, 160);
        if (cat.isDowned()) {
            select(Activity.DOWNED);
            combat.clear();
            cat.getNavigation().stop();
            requestAction(CompanionAction.DOWNED, 30);
            return;
        }
        boolean emergency = immediateHazard() || assessment.overwhelming();
        if (cat.isBaby()) {
            combat.clear();
            select(family.tickJuvenile(owner, assessment, immediateHazard(), sleep, now));
            return;
        }
        if (emergency) {
            select(Activity.EMERGENCY);
            combat.clear();
            if (!cat.isNoAi()) retreat(now);
            return;
        }
        if (cat.isRecovering()) {
            select(Activity.RECOVER);
            combat.clear();
            if (!cat.isNoAi()) {
                if (cat.getAction() == CompanionAction.REVIVE) {
                    cat.getNavigation().stop();
                    cat.getMoveControl().setWait();
                    cat.setXxa(0); cat.setZza(0);
                } else if (owner != null && cat.distanceToSqr(owner) > 6 * 6) follow(owner, now);
                else rest(now, CompanionAction.REST);
            }
            return;
        }
        LivingEntity defenderTarget = resolve(assessment.defenseTarget());
        if (defenderTarget == null) defenderTarget = family.childDefenseTarget(now);
        if (defenderTarget != null && SavannahConfig.COMBAT.get()) {
            select(Activity.DEFEND);
            if (assessment.ownerEmergency()) cat.tryProtectiveInstinct(defenderTarget);
            if (!cat.isNoAi() && combat.engage(defenderTarget, false)) return;
        }
        // Optional bedtime uses the existing sleep controller and may approach a sleeping owner before ordinary Follow.
        if (!cat.isNoAi() && homeReturn.get(now) == null && sleep.tryBedtime(owner, now)) {
            combat.clear(); select(sleep.isSleeping() ? Activity.SLEEP : Activity.BEDTIME); return;
        }
        // Explicit Stay survives defensive interruptions and returns to its saved anchor.
        if (cat.isStayCommand()) {
            combat.clear();
            if (!cat.isNoAi() && BreedingService.tickCourtship(cat)) { select(Activity.COURTSHIP); return; }
            BlockPos anchor = cat.getCompanionData().getStayAnchor();
            if (!cat.isNoAi() && (anchor == null || cat.position().distanceToSqr(Vec3.atBottomCenterOf(anchor)) <= 2.5 * 2.5)
                    && sleep.tryRest(owner, now)) { select(Activity.SLEEP); return; }
            if (!cat.isNoAi() && net.ronm19.savannahcat.companion.utility.HomeLifeCoordinator.tick(cat)) { select(Activity.HOME_LIFE); return; }
            select(Activity.STAY);
            if (!cat.isNoAi()) stay(now);
            return;
        }
        // Preserve the existing explicit Sitting/NBT hook until a real movement request interrupts it.
        if (cat.isSitting() && (!cat.getNavigation().isDone() || cat.getDeltaMovement().horizontalDistanceSqr() > .0001)) {
            cat.setSitting(false);
            manualRest = false;
            ambientUntil = 0;
            externalMovementUntil = now + 80;
        }
        if (cat.isSitting() && activity != Activity.REST && activity != Activity.SLEEP && activity != Activity.RECOVER && activity != Activity.STAY) manualRest = true;
        if (manualRest) {
            boolean movementRequested = !cat.getNavigation().isDone() || cat.getDeltaMovement().horizontalDistanceSqr() > .0001
                    || owner != null && cat.distanceToSqr(owner) > Math.pow(SavannahConfig.FOLLOW_START.get(), 2);
            if (cat.isSitting() && !movementRequested) {
                select(Activity.REST);
                if (!cat.isNoAi()) cat.holdSittingPosition();
                return;
            }
            manualRest = false;
            cat.setSitting(false);
            if (movementRequested) externalMovementUntil = now + 80;
        }
        if (now < externalMovementUntil && !cat.getNavigation().isDone()) {
            select(Activity.EXPLORE);
            return;
        }
        Vec3 home = homeReturn.get(now);
        if (home != null) {
            select(Activity.RETURN_HOME);
            combat.clear();
            if (cat.position().distanceToSqr(home) < 4) {
                homeReturn.clear();
                rest(now, CompanionAction.REST);
            } else if (!cat.isNoAi()) {
                cat.setSitting(false);
                navigate(home, 1.0);
            }
            return;
        }
        if (cat.isTamed() && owner == null) {
            combat.clear();
            if (!cat.isNoAi() && BreedingService.tickCourtship(cat)) { select(Activity.COURTSHIP); return; }
            if (!cat.isNoAi() && sleep.tryRest(null, now)) { select(Activity.SLEEP); return; }
            if (!cat.isNoAi() && net.ronm19.savannahcat.companion.utility.HomeLifeCoordinator.tick(cat)) { select(Activity.HOME_LIFE); return; }
            select(Activity.OWNER_UNAVAILABLE);
            if (!cat.isNoAi()) {
                Vec3 remembered = ownerPosition.get(now);
                if (remembered != null && cat.position().distanceToSqr(remembered) > 9) navigate(remembered, 1);
                else cat.getNavigation().stop();
            }
            return;
        }
        // Follow/return distance takes precedence over optional work or ambient curiosity.
        if (owner != null) {
            double stop = SavannahConfig.FOLLOW_STOP.get();
            double start = Math.max(SavannahConfig.FOLLOW_START.get(), stop + 1);
            if (cat.getCommand() == CompanionCommand.HARVEST) start = Math.max(start, SavannahConfig.HARVEST_RANGE.get() + 2);
            if (cat.getCommand() == CompanionCommand.HUNT) start = Math.max(start, FelineCombat.MAX_OWNER_DISTANCE);
            if (cat.distanceToSqr(owner) > start * start || following && cat.distanceToSqr(owner) > stop * stop) {
                select(Activity.FOLLOW);
                combat.clear();
                if (!cat.isNoAi()) follow(owner, now);
                return;
            }
            if (following) cat.getNavigation().stop();
            following = false;
            navigator.endFollowing();
        }
        if (!cat.isNoAi() && BreedingService.tickCourtship(cat)) {
            combat.clear(); select(Activity.COURTSHIP); return;
        }
        if (!cat.isNoAi() && sleep.tryRest(owner, now)) {
            combat.clear(); select(Activity.SLEEP); return;
        }
        boolean huntMode = cat.getCommand() == CompanionCommand.HUNT;
        boolean wildHunt = !cat.isTamed();
        if ((huntMode || wildHunt) && SavannahConfig.HUNTING.get()) {
            UUID rememberedPrey = preyMemory.get(now);
            LivingEntity prey = resolve(rememberedPrey);
            if (rememberedPrey != null && (prey == null || !combat.withinPursuitBounds(prey)
                    || CompanionRelations.hasProtectedPreyIdentity(cat, prey)
                    || now >= nextPreyValidation && !CompanionRelations.mayHunt(cat, prey))) {
                preyMemory.clear(); combat.clear(); prey = null;
                nextPreySearch = 0; huntingRetry = 0;
            }
            if (prey != null && now >= nextPreyValidation) nextPreyValidation = now + 10;
            if (prey == null && now >= nextPreySearch && now >= huntingRetry) {
                nextPreySearch = now + (huntMode ? 20 : 80);
                if (huntMode || cat.getRandom().nextInt(100) < trait(Trait.PREY_DRIVE) * .2) prey = choosePrey(level, now);
            }
            if (prey != null) {
                select(Activity.HUNT);
                if (!cat.isNoAi() && combat.engage(prey, true)) return;
                preyMemory.clear();
                failedPrey.remember(prey.getUUID(), now, 100);
                huntingRetry = now + (huntMode ? 20 : 200);
            }
        }
        combat.clear();
        if (!cat.isNoAi()) {
            if (cat.isTamed() && cat.getCommand() == CompanionCommand.HARVEST) select(Activity.HARVEST);
            if (CompanionUtilities.tick(cat)) {
                if (cat.getCompanionData().getUtilityData().homeLife.active()) select(Activity.HOME_LIFE);
                else if (cat.getCommand() != CompanionCommand.HARVEST) select(Activity.REST);
                return;
            }
        }
        if (cat.getCommand() == CompanionCommand.HARVEST && cat.isTamed()) {
            select(Activity.HARVEST);
            if (!cat.isNoAi()) cat.getNavigation().stop();
            return;
        }
        if (!cat.isTamed() && cautiousOfStranger(now)) return;
        if (!cat.isNoAi() && family.tickParentCare(now)) { select(Activity.PARENT_CARE); return; }
        if (!cat.isNoAi()) ambient(owner, now);
    }

    private void perceive(ServerLevel level, long now) {
        int interval = SavannahConfig.SENSOR_INTERVAL.get();
        int stagger = Math.floorMod(cat.getId(), interval);
        nextSense = now + interval - Math.floorMod(now - stagger, interval);
        sensorPasses++;
        perceived.clear();
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
                cat.getBoundingBox().inflate(SavannahConfig.SENSE_RADIUS.get()),
                entity -> entity != cat && entity.isAlive() && !entity.isSpectator());
        nearby.sort(Comparator.comparingDouble(cat::distanceToSqr));
        List<LivingEntity> visible = new ArrayList<>();
        int inspected = 0;
        for (LivingEntity entity : nearby) {
            if (inspected++ >= SavannahConfig.MAX_SCANNED_ENTITIES.get()) break;
            boolean audible = cat.distanceToSqr(entity) < 6 * 6
                    && (entity.getDeltaMovement().horizontalDistanceSqr() > .002 || entity.hurtTime > 0);
            if (cat.getSensing().hasLineOfSight(entity) || audible) {
                perceived.add(entity.getUUID());
                visible.add(entity);
            }
        }
        maximumPerceived = Math.max(maximumPerceived, perceived.size());
        LivingEntity attacker = resolve(recentAttacker.get(now));
        if (attacker != null && visible.stream().noneMatch(e -> e == attacker)
                && cat.distanceToSqr(attacker) < 32 * 32) visible.add(attacker);
        assessment = ThreatAssessment.evaluate(cat, cat.getOwnerPlayer(), visible, attacker);
        if (assessment.closestThreat() != null) {
            threatMemory.remember(assessment.closestThreat(), now, interval * 3 + 20);
        }
    }

    private @Nullable LivingEntity choosePrey(ServerLevel level, long now) {
        if (getHuntCooldownTicks() > 0) { huntSearchStatus = HuntStatus.COOLDOWN; return null; }
        huntSearchStatus = HuntStatus.NO_PREY;
        if (perceived.isEmpty()) return null;
        int start = Math.floorMod(nextPreyCandidate, perceived.size());
        int examined = 0;
        // Keep the expensive scan cap, but resume beyond rejected pens on the next search.
        for (int offset = 0; offset < perceived.size() && examined < 4; offset++) {
            int index = (start + offset) % perceived.size();
            UUID id = perceived.get(index);
            nextPreyCandidate = (index + 1) % perceived.size();
            LivingEntity candidate = resolve(id);
            if (candidate == null || id.equals(failedPrey.get(now)) || !CompanionRelations.isPreyType(cat, candidate)) continue;
            if (cat.distanceToSqr(candidate) > 20 * 20 || !combat.withinPursuitBounds(candidate)
                    || !cat.getSensing().hasLineOfSight(candidate)) continue;
            if (CompanionRelations.hasProtectedPreyIdentity(cat, candidate)) { huntSearchStatus = HuntStatus.PROTECTED; continue; }
            examined++;
            var eligibility = CompanionRelations.preyEligibility(cat, candidate);
            if (eligibility != CompanionRelations.PreyEligibility.ALLOWED) {
                huntSearchStatus = eligibility == CompanionRelations.PreyEligibility.LOW_POPULATION ? HuntStatus.POPULATION : HuntStatus.PROTECTED;
                continue;
            }
            boolean claimed = false;
            int checked = 0;
            for (UUID otherId : perceived) {
                LivingEntity other = resolve(otherId);
                if (!(other instanceof SavannahCatEntity ally)) continue;
                if (checked++ >= SavannahConfig.MAX_NEARBY_CATS.get()) break;
                if (candidate.getUUID().equals(ally.getCompanionBrain().combat.getTargetId())) {
                    claimed = true; break;
                }
            }
            if (!claimed) {
                preyMemory.remember(candidate.getUUID(), now, SavannahConfig.MAX_CHASE_TICKS.get());
                nextPreyValidation = now + 10;
                huntSearchStatus = HuntStatus.PURSUING;
                return candidate;
            }
        }
        return null;
    }

    private void follow(ServerPlayer owner, long now) {
        following = true;
        navigator.beginFollowing();
        cat.setSitting(false);
        cat.getLookControl().setLookAt(owner, 10, cat.getMaxHeadXRot());
        // Stable per-cat slots put companions beside/behind the owner instead of directly in their path.
        double spacing = Math.max(2.2, SavannahConfig.FOLLOW_STOP.get()
                + (trait(Trait.INDEPENDENCE) - trait(Trait.SOCIABILITY)) * .008);
        double angle = (cat.getUUID().hashCode() & 15) * Math.PI / 8;
        Vec3 look = owner.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 slot = owner.position().subtract(look.scale(spacing * .6))
                .add(Math.cos(angle) * spacing, 0, Math.sin(angle) * spacing);
        if (!SafetyNavigator.safeDestination((ServerLevel)cat.level(), cat, slot)) slot = owner.position();
        navigate(slot, cat.distanceToSqr(owner) > 12 * 12 ? 1.2 : 1.0);
        if (!assessment.overwhelming()) navigator.tryCatchUp(owner);
    }

    private void stay(long now) {
        BlockPos anchor = cat.getCompanionData().getStayAnchor();
        if (anchor == null) {
            anchor = cat.blockPosition();
            cat.getCompanionData().setStayAnchor(anchor);
        }
        Vec3 point = Vec3.atBottomCenterOf(anchor);
        if (cat.position().distanceToSqr(point) > 2.5 * 2.5) {
            cat.setSitting(false);
            navigate(point, 1);
        } else if (isSafeToRest()) rest(now, CompanionAction.REST);
    }

    private void retreat(long now) {
        cat.setSitting(false);
        requestAction(CompanionAction.RETREAT, 25);
        if (now < nextRetreatPath && !cat.getNavigation().isDone()) return;
        nextRetreatPath = now + 20;
        LivingEntity danger = resolve(assessment.closestThreat());
        if (danger == null) danger = resolve(recentAttacker.get(now));
        Vec3 awayFrom = danger == null ? dangerousArea.get(now) : danger.position();
        Vec3 destination = awayFrom == null ? DefaultRandomPos.getPos(cat, 10, 3)
                : DefaultRandomPos.getPosAway(cat, 12, 3, awayFrom);
        if (destination != null && SafetyNavigator.safeDestination((ServerLevel)cat.level(), cat, destination)) {
            safeRetreat.remember(destination, now, 80);
            navigate(destination, 1.4);
        } else {
            Vec3 remembered = safeRetreat.get(now);
            if (remembered != null) navigate(remembered, 1.3);
        }
    }

    private boolean cautiousOfStranger(long now) {
        Player stranger = null;
        for (UUID id : perceived) {
            if (resolve(id) instanceof Player player && cat.distanceToSqr(player) < 8 * 8) { stranger = player; break; }
        }
        if (stranger == null || stranger.isSpectator() || stranger.isCreative()) return false;
        boolean peaceful = stranger.isCrouching() || SavannahConfig.isFood(stranger.getMainHandItem(), true)
                || SavannahConfig.isFood(stranger.getOffhandItem(), true);
        double caution = 3 + (100 - trait(Trait.CONFIDENCE)) * .04;
        if (cat.distanceToSqr(stranger) < caution * caution && !peaceful) {
            select(Activity.OBSERVE);
            if (!cat.isNoAi()) {
                cat.setSitting(false);
                Vec3 pos = DefaultRandomPos.getPosAway(cat, 7, 2, stranger.position());
                if (pos != null) navigate(pos, 1.1);
                cat.getLookControl().setLookAt(stranger, 15, 15);
                requestAction(CompanionAction.ALERT, 20);
            }
            return true;
        }
        return false;
    }

    private void ambient(@Nullable ServerPlayer owner, long now) {
        if (!SavannahConfig.AMBIENT.get()) { select(Activity.OBSERVE); return; }
        if (now - sleep.getLastWake() < 40) { select(Activity.OBSERVE); return; }
        if (now < ambientUntil) {
            if (activity == Activity.EXPLORE || activity == Activity.PLAY || activity == Activity.SOCIAL) {
                if (ambientDestination != null) navigate(ambientDestination, activity == Activity.PLAY ? 1.15 : .8);
            } else if (activity == Activity.REST) rest(now, CompanionAction.REST);
            return;
        }
        if (now < nextAmbient) { select(Activity.OBSERVE); return; }
        nextAmbient = now + SavannahConfig.AMBIENT_INTERVAL.get() + cat.getRandom().nextInt(80);
        ambientDestination = null;
        int roll = cat.getRandom().nextInt(100);
        if (cat.getHealth() < cat.getMaxHealth() * .7 || roll < 15) {
            select(Activity.REST); ambientUntil = now + 80;
            rest(now, CompanionAction.REST);
        } else if (roll < 30) {
            select(Activity.GROOM); ambientUntil = now + 50; cat.getNavigation().stop(); requestAction(CompanionAction.GROOM, 50);
        } else if (roll < 40) {
            select(Activity.STRETCH); ambientUntil = now + 32; cat.getNavigation().stop(); requestAction(CompanionAction.STRETCH, 32);
        } else if (roll < 40 + trait(Trait.SOCIABILITY) / 4) {
            LivingEntity friend = owner;
            if (friend == null) {
                for (UUID id : perceived) {
                    if (resolve(id) instanceof SavannahCatEntity other && other.getId() < cat.getId()
                            && !other.isDowned() && cat.distanceToSqr(other) < 8 * 8) { friend = other; break; }
                }
            }
            select(Activity.SOCIAL); ambientUntil = now + 40;
            if (friend != null) {
                ambientDestination = friend.position().add((cat.getId() & 1) == 0 ? 2 : -2, 0, 2);
                cat.getLookControl().setLookAt(friend, 10, 10);
            }
            requestAction(CompanionAction.SOCIAL, 35);
        } else if (roll > 100 - trait(Trait.PLAYFULNESS) / 3) {
            select(Activity.PLAY); ambientUntil = now + 35;
            ambientDestination = DefaultRandomPos.getPos(cat, 3, 1);
            requestAction(CompanionAction.PLAY, 35);
        } else {
            select(Activity.EXPLORE); ambientUntil = now + 70;
            int distance = owner == null ? 4 + trait(Trait.CURIOSITY) / 20 : 2 + trait(Trait.INDEPENDENCE) / 35;
            ambientDestination = DefaultRandomPos.getPos(cat, distance, 2);
            Vec3 danger = dangerousArea.get(now);
            if (ambientDestination != null && danger != null && ambientDestination.distanceToSqr(danger) < 6 * 6) ambientDestination = null;
            requestAction(CompanionAction.INVESTIGATE, 30);
        }
        if (activity != Activity.REST) cat.setSitting(false);
    }

    private void rest(long now, CompanionAction action) {
        if (cat.onGround() && !immediateHazard()) {
            cat.setSitting(true);
            cat.holdSittingPosition();
            // Extending the same action does not change its sequence or restart its animation.
            cat.setAction(action, 40);
        }
    }

    private void navigate(Vec3 position, double speed) {
        if (navigator.navigate(position, speed)) lastSuccessfulPath.remember(position, cat.level().getGameTime(), 200);
    }

    private void requestAction(CompanionAction action, int duration) {
        if (cat.getAction() != action) cat.setAction(action, duration);
    }

    private void select(Activity next) {
        if (activity != next) {
            if (activity == Activity.HARVEST && next != Activity.HARVEST)
                net.ronm19.savannahcat.companion.utility.HarvestCoordinator.interrupt(cat);
            if (activity == Activity.HOME_LIFE && next != Activity.HOME_LIFE) {
                net.ronm19.savannahcat.companion.utility.HomeLifeCoordinator.interrupt(cat);
                homeLifePaused = false;
            }
            if (next != Activity.SLEEP && next != Activity.BEDTIME && sleep.hasActivity()) sleep.wake(cat.level().getGameTime());
            if (activity == Activity.FOLLOW && next != Activity.FOLLOW) {
                following = false;
                navigator.endFollowing();
            }
            activity = next;
            ambientUntil = 0;
            ambientDestination = null;
            boolean urgent = next == Activity.FOLLOW || next == Activity.FAMILY_FOLLOW || next == Activity.DEFEND || next == Activity.EMERGENCY
                    || next == Activity.DOWNED || next == Activity.HUNT || next == Activity.HARVEST;
            if (urgent && cat.getAction() != CompanionAction.DOWNED
                    && !(next == Activity.EMERGENCY && cat.getAction() == CompanionAction.RETREAT)) cat.clearAction();
            if (next != Activity.REST && next != Activity.SLEEP && next != Activity.STAY) cat.setSitting(false);
        }
    }

    private int trait(Trait trait) { return SavannahConfig.PERSONALITY.get() ? cat.getPersonality(trait) : 50; }

    private boolean immediateHazard() {
        return SavannahConfig.HAZARD_AVOIDANCE.get() && (cat.isOnFire() || cat.isInLiquid()
                || cat.getAirSupply() < 60 || SafetyNavigator.hazardous(cat.level().getBlockState(cat.blockPosition())));
    }

    private void expireMemories(long now) {
        recentAttacker.get(now); threatMemory.get(now); preyMemory.get(now); ownerPosition.get(now);
        dangerousArea.get(now); safeRetreat.get(now); lastSuccessfulPath.get(now); homeReturn.get(now);
        UUID attacker = recentAttacker.get(now);
        if (attacker != null && resolve(attacker) == null) recentAttacker.clear();
    }

    private @Nullable LivingEntity resolve(@Nullable UUID id) {
        if (id == null || !(cat.level() instanceof ServerLevel level)) return null;
        return level.getEntity(id) instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    public boolean returnHome() {
        var home = cat.getCompanionData().getHome();
        if (!cat.isTamed() || home == null || !home.dimension().equals(cat.level().dimension())
                || !(cat.level() instanceof ServerLevel level) || cat.isDowned()
                || !level.hasChunkAt(home.pos()) || !SafetyNavigator.safeDestination(level, cat, Vec3.atBottomCenterOf(home.pos()))) return false;
        wakeFromCommand();
        homeReturn.remember(Vec3.atBottomCenterOf(home.pos()), level.getGameTime(), 1200);
        if (cat.isStayCommand()) cat.getCompanionData().setStayAnchor(home.pos());
        return true;
    }
    /** Supersede a compass journey only after a replacement owner order is accepted. */
    public void cancelHomeReturn() { homeReturn.clear(); }
    /** A new accepted mode supersedes an old return/chase; repeating a mode retains its useful path. */
    public void onCommandChanged() {
        following = false;
        navigator.endFollowing();
        combat.clear();
        preyMemory.clear();
        nextPreySearch = 0;
        huntingRetry = 0;
        nextPreyCandidate = 0;
        nextSense = 0;
        nextPreyValidation = 0;
        nextRequestedHuntRefresh = 0;
        huntSearchStatus = HuntStatus.SEARCHING;
        failedPrey.clear();
    }
    /** Refresh an idle accepted order without restarting a valid pursuit or any saved ability cooldown. */
    public void requestIdleHuntSearch() {
        if (!CompanionRelations.isCommandHunt(cat) || cat.isDowned() || cat.isRecovering()) return;
        LivingEntity target = cat.getTarget();
        if (combat.hasHuntTarget() && target != null && target.isAlive()
                && !CompanionRelations.hasProtectedPreyIdentity(cat, target) && combat.withinPursuitBounds(target)) return;
        if (combat.isControlledLeap() || activity == Activity.DEFEND || activity == Activity.EMERGENCY) return;
        long now = cat.level().getGameTime();
        if (now < nextRequestedHuntRefresh) return;
        nextRequestedHuntRefresh = now + 20;
        nextSense = 0; nextPreySearch = 0; huntingRetry = 0;
        failedPrey.clear();
    }
    public long getHuntCooldownTicks() {
        long lastHunt = cat.getCompanionData().getLastHunt();
        return lastHunt < 0 ? 0 : Math.max(0L, SavannahConfig.HUNT_COOLDOWN.get() - (cat.level().getGameTime() - lastHunt));
    }
    /** Constant-time feedback from the actual bounded search; never run another diagnostic scan. */
    public HuntStatus getHuntStatus() {
        if (cat.isDowned() || cat.isRecovering() || isThreatened() || cat.getOwnerPlayer() == null
                || activity == Activity.DEFEND || activity == Activity.RECOVER) return HuntStatus.BUSY;
        if (activity == Activity.FOLLOW || activity == Activity.RETURN_HOME) return HuntStatus.RETURNING;
        if (getHuntCooldownTicks() > 0) return HuntStatus.COOLDOWN;
        var target = cat.getTarget();
        if (combat.hasHuntTarget() && target != null && target.isAlive()) return HuntStatus.PURSUING;
        return huntSearchStatus == HuntStatus.PURSUING || huntSearchStatus == HuntStatus.COOLDOWN ? HuntStatus.SEARCHING : huntSearchStatus;
    }
    public boolean recallToOwner(ServerPlayer owner) { return navigator.recallToOwner(owner); }
    public void pauseForHomeLife() {
        if (homeLifePaused) return;
        homeLifePaused = true;
        pausedActivity = switch (activity) {
            case REST, GROOM, STRETCH, PLAY, SOCIAL, EXPLORE -> activity;
            default -> Activity.OBSERVE;
        };
        pausedAction = cat.getAction();
        pausedDestination = ambientDestination;
        pausedAmbientTicks = Math.max(0, ambientUntil - cat.level().getGameTime());
        select(Activity.HOME_LIFE);
    }
    public void resumeAfterHomeLife() {
        if (!homeLifePaused) return;
        homeLifePaused = false;
        activity = pausedActivity;
        ambientDestination = pausedDestination;
        ambientUntil = cat.level().getGameTime() + Math.min(80, pausedAmbientTicks);
        if (pausedAmbientTicks > 0 && pausedAction != CompanionAction.IDLE && !pausedAction.critical() && !pausedAction.interaction())
            cat.setAction(pausedAction, (int)Math.min(80, pausedAmbientTicks));
        pausedDestination = null;
    }
    public void wakeFromCommand() {
        net.ronm19.savannahcat.companion.utility.HarvestCoordinator.interrupt(cat);
        long now = cat.level().getGameTime();
        net.ronm19.savannahcat.companion.utility.HomeLifeCoordinator.interrupt(cat);
        homeLifePaused = false;
        if (activity == Activity.HOME_LIFE) activity = Activity.OBSERVE;
        sleep.wake(now);
        ambientUntil = 0;
        ambientDestination = null;
        manualRest = false;
        externalMovementUntil = 0;
        nextAmbient = now + 80;
    }
    public boolean isThreatened() {
        return immediateHazard() || recentAttacker.isPresent(cat.level().getGameTime())
                || resolve(threatMemory.get(cat.level().getGameTime())) != null;
    }
    public boolean isSafeToRest() { return cat.isAlive() && cat.onGround() && !isThreatened() && cat.getTarget() == null && !cat.isDowned(); }
    public State getState() {
        return switch (activity) {
            case EMERGENCY -> State.RETREAT;
            case DOWNED -> State.DOWNED;
            case DEFEND -> State.DEFEND;
            case STAY -> State.STAY;
            case HUNT -> State.HUNT;
            case HARVEST -> State.HARVEST;
            case FOLLOW, RETURN_HOME, FAMILY_FOLLOW -> State.FOLLOW_OWNER;
            case OWNER_UNAVAILABLE -> State.OWNER_UNAVAILABLE;
            case RECOVER, REST, SLEEP, HOME_LIFE, BEDTIME -> State.REST;
            default -> cat.isTamed() ? State.FOLLOW_OWNER : State.WILD;
        };
    }
    public Activity getActivity() { return activity; }
    public @Nullable UUID getRecentAttacker() { return recentAttacker.get(cat.level().getGameTime()); }
    public SafetyNavigator getNavigator() { return navigator; }
    public FelineCombat getCombat() { return combat; }
    public CompanionSleep getSleep() { return sleep; }
    public FamilyAI getFamilyAI() { return family; }
    public ThreatAssessment getThreatAssessment() { return assessment; }
    public int getSensorPasses() { return sensorPasses; }
    public int getMaximumPerceived() { return maximumPerceived; }
    public boolean isControlledLeap() { return combat.isControlledLeap(); }
}
