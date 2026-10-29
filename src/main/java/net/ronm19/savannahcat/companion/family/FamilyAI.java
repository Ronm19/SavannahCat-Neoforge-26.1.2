package net.ronm19.savannahcat.companion.family;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import net.ronm19.savannahcat.companion.CompanionAction;
import net.ronm19.savannahcat.companion.CompanionCommand;
import net.ronm19.savannahcat.companion.CompanionData.Trait;
import net.ronm19.savannahcat.companion.utility.HomeLifeCoordinator;
import net.ronm19.savannahcat.config.SavannahConfig;
import net.ronm19.savannahcat.entity.SavannahCatEntity;
import net.ronm19.savannahcat.entity.ai.CompanionRelations;
import net.ronm19.savannahcat.entity.ai.CompanionSleep;
import net.ronm19.savannahcat.entity.ai.SafetyNavigator;
import net.ronm19.savannahcat.entity.ai.SavannahCatBrain.Activity;
import net.ronm19.savannahcat.entity.ai.ThreatAssessment;
import org.jspecify.annotations.Nullable;

/** Loaded-family references and bounded, noncombat juvenile decisions. Commands outrank optional family care. */
public final class FamilyAI {
    private final SavannahCatEntity cat;
    private final SafetyNavigator navigation;
    private long nextAmbient, ambientUntil, nextCareSearch, careUntil;
    private long nextChildrenScan;
    private @Nullable UUID caredChild;
    private final ArrayList<UUID> children = new ArrayList<>(4);
    private @Nullable Vec3 lastGuardianPosition;
    private long guardianMemoryUntil;
    private @Nullable Vec3 ambientPoint;
    private CompanionAction ambientAction = CompanionAction.IDLE;
    private boolean ownerFollowing, guardianFollowing;
    private long playHopUntil;

    public FamilyAI(SavannahCatEntity cat, SafetyNavigator navigation) { this.cat = cat; this.navigation = navigation; }

    /** Every baby returns here before the adult combat/work branches, even with family behaviors disabled. */
    public Activity tickJuvenile(@Nullable ServerPlayer owner, ThreatAssessment threats, boolean immediateHazard,
            CompanionSleep sleep, long now) {
        cat.setTarget(null);
        if (cat.isNoAi()) return Activity.OBSERVE;
        if (immediateHazard || threats.overwhelming() || SavannahConfig.KITTEN_SAFETY.get() && threats.closestThreat() != null) {
            sleep.wake(now); HomeLifeCoordinator.interrupt(cat); cat.setSitting(false);
            cat.setAction(CompanionAction.RETREAT, 25);
            var danger = resolve(threats.closestThreat());
            if (danger == null) danger = resolve(cat.getCompanionBrain().getRecentAttacker());
            Vec3 point = danger == null ? DefaultRandomPos.getPos(cat, 6, 2) : DefaultRandomPos.getPosAway(cat, 8, 2, danger.position());
            if (point != null) navigation.navigate(point, 1.25);
            return Activity.EMERGENCY;
        }
        if (cat.isRecovering()) {
            sleep.wake(now);
            if (cat.getAction() == CompanionAction.REVIVE) { cat.holdSittingPosition(); return Activity.RECOVER; }
            if (owner != null && cat.distanceToSqr(owner) > 36) { moveTo(owner.position(), .8); return Activity.RECOVER; }
            rest(); return Activity.RECOVER;
        }
        if (sleep.tryBedtime(owner, now)) {
            ownerFollowing = false; guardianFollowing = false;
            return sleep.isSleeping() ? Activity.SLEEP : Activity.BEDTIME;
        }
        if (cat.isStayCommand()) {
            BlockPos anchor = cat.getCompanionData().getStayAnchor();
            if (anchor == null) { anchor = cat.blockPosition(); cat.getCompanionData().setStayAnchor(anchor); }
            Vec3 point = Vec3.atBottomCenterOf(anchor);
            if (cat.position().distanceToSqr(point) > 6.25) { sleep.wake(now); moveTo(point, .9); return Activity.STAY; }
            if (sleep.tryRest(owner, now)) return Activity.SLEEP;
            if (HomeLifeCoordinator.tick(cat)) return Activity.HOME_LIFE;
            rest(); return Activity.STAY;
        }
        // A requested owner journey always wins over lingering with a parent or sibling.
        if (owner != null && (cat.distanceToSqr(owner) > 64 || ownerFollowing && cat.distanceToSqr(owner) > 3.5 * 3.5)) {
            ownerFollowing = true; guardianFollowing = false;
            sleep.wake(now); HomeLifeCoordinator.interrupt(cat); moveTo(owner.position(), 1.15);
            navigation.beginFollowing(); navigation.tryCatchUp(owner);
            return Activity.FOLLOW;
        }
        if (ownerFollowing) { cat.getNavigation().stop(); ownerFollowing = false; }
        SavannahCatEntity guardian = SavannahConfig.FAMILY_AI.get() ? guardian() : null;
        if (guardian != null && owner != null && guardian.distanceToSqr(owner) > 64) guardian = null;
        if (guardian != null) {
            lastGuardianPosition = guardian.position(); guardianMemoryUntil = now + 160;
            if (cat.distanceToSqr(guardian) > 3.5 * 3.5 || guardianFollowing && cat.distanceToSqr(guardian) > 1.8 * 1.8) {
                guardianFollowing = true;
                sleep.wake(now); HomeLifeCoordinator.interrupt(cat);
                Vec3 slot = guardian.position().add(Math.cos(cat.getId()) * 1.5, 0, Math.sin(cat.getId()) * 1.5);
                moveTo(slot, cat.distanceToSqr(guardian) > 64 ? 1.15 : .9);
                navigation.beginFollowing(); navigation.tryFamilyCatchUp(guardian);
                return Activity.FAMILY_FOLLOW;
            }
        } else if (owner == null && lastGuardianPosition != null && now < guardianMemoryUntil
                && cat.position().distanceToSqr(lastGuardianPosition) > 9) {
            moveTo(lastGuardianPosition, .85); return Activity.FAMILY_FOLLOW;
        }
        if (guardianFollowing) { cat.getNavigation().stop(); guardianFollowing = false; }
        navigation.endFollowing();
        if (sleep.tryRest(owner, now)) return Activity.SLEEP;
        if (HomeLifeCoordinator.tick(cat)) return Activity.HOME_LIFE;
        if (!SavannahConfig.FAMILY_AI.get() || !SavannahConfig.AMBIENT.get()) return Activity.OBSERVE;
        if (now < ambientUntil) {
            if (now <= playHopUntil) return Activity.PLAY;
            if (ambientPoint != null) moveTo(ambientPoint, .85);
            else { cat.holdSittingPosition(); cat.setAction(ambientAction, 25); }
            return ambientAction == CompanionAction.GROOM ? Activity.GROOM : ambientAction == CompanionAction.PLAY ? Activity.PLAY : Activity.SOCIAL;
        }
        if (now < nextAmbient || now - sleep.getLastWake() < 40 || cat.getAction().interaction()) return Activity.OBSERVE;
        nextAmbient = now + 100 + cat.getRandom().nextInt(100); ambientUntil = now + 35; ambientPoint = null;
        int roll = cat.getRandom().nextInt(100);
        if (roll < cat.getPersonality(Trait.PLAYFULNESS) * .55) {
            ambientAction = CompanionAction.PLAY;
            if (cat.getRandom().nextBoolean()) ambientPoint = DefaultRandomPos.getPos(cat, 2, 1);
            if (ambientPoint != null && guardian != null && ambientPoint.distanceToSqr(guardian.position()) > 36) ambientPoint = null;
            if (ambientPoint == null) playfulHop(now);
        } else if (roll < 70) ambientAction = CompanionAction.GROOM;
        else {
            ambientAction = CompanionAction.SOCIAL;
            SavannahCatEntity sibling = sibling();
            if (sibling != null) cat.getLookControl().setLookAt(sibling, 15, 15);
            else if (guardian != null) cat.getLookControl().setLookAt(guardian, 15, 15);
            else ambientAction = CompanionAction.INVESTIGATE;
        }
        cat.setSitting(false); cat.setAction(ambientAction, 35);
        return ambientAction == CompanionAction.GROOM ? Activity.GROOM : ambientAction == CompanionAction.PLAY ? Activity.PLAY : Activity.SOCIAL;
    }

    public @Nullable SavannahCatEntity guardian() {
        var data = cat.getFamilyData();
        for (UUID id : new UUID[]{data.getGuardian(), data.getParentA(), data.getParentB()}) {
            var entity = resolve(id);
            if (entity instanceof SavannahCatEntity parent && !parent.isBaby() && !parent.isDowned()
                    && !parent.isRecovering() && FamilyInheritance.compatibleOwners(cat, parent)
                    && cat.distanceToSqr(parent) <= 48 * 48) return parent;
        }
        return null;
    }
    private @Nullable SavannahCatEntity sibling() {
        if (!(cat.level() instanceof ServerLevel level)) return null;
        var nearby = new ArrayList<SavannahCatEntity>(4);
        level.getEntities(EntityTypeTest.forClass(SavannahCatEntity.class), cat.getBoundingBox().inflate(5),
                other -> other != cat && other.isAlive() && other.isBaby()
                        && (cat.getFamilyData().hasParent(other.getFamilyData().getParentA())
                            || cat.getFamilyData().hasParent(other.getFamilyData().getParentB())), nearby, 4);
        return nearby.isEmpty() ? null : nearby.getFirst();
    }
    private void scanChildren(long now) {
        if (now < nextChildrenScan || !(cat.level() instanceof ServerLevel level)) return;
        nextChildrenScan = now + 20; children.clear();
        var nearby = new ArrayList<SavannahCatEntity>(4);
        level.getEntities(EntityTypeTest.forClass(SavannahCatEntity.class), cat.getBoundingBox().inflate(12),
                other -> other != cat && other.isAlive() && other.isBaby() && other.getFamilyData().hasParent(cat.getUUID()), nearby, 4);
        nearby.sort(Comparator.comparingDouble(cat::distanceToSqr));
        for (var child : nearby) children.add(child.getUUID());
    }
    /** Genuine attacks on a nearby child may interrupt Stay, just like genuine owner defense. */
    public @Nullable LivingEntity childDefenseTarget(long now) {
        if (!SavannahConfig.FAMILY_AI.get() || cat.isBaby()) return null;
        scanChildren(now);
        for (UUID id : children) if (resolve(id) instanceof SavannahCatEntity child) {
            LivingEntity attacker = resolve(child.getCompanionBrain().getRecentAttacker());
            if (attacker != null && cat.distanceToSqr(attacker) <= 18 * 18 && child.distanceToSqr(attacker) <= 8 * 8
                    && CompanionRelations.canFight(cat, attacker) && cat.getSensing().hasLineOfSight(attacker)) return attacker;
        }
        return null;
    }
    /** Quiet care never outranks owner travel, commands, courtship, work, danger or health recovery. */
    public boolean tickParentCare(long now) {
        if (!SavannahConfig.FAMILY_AI.get() || cat.isBaby() || cat.getCommand() != CompanionCommand.FOLLOW
                || cat.getAction().interaction() || cat.getFamilyData().isInLove(now)) return false;
        if (now < careUntil && resolve(caredChild) instanceof SavannahCatEntity child && child.isBaby()
                && cat.distanceToSqr(child) < 16) {
            cat.getNavigation().stop(); cat.getLookControl().setLookAt(child, 12, 12);
            cat.setAction(CompanionAction.SOCIAL, 20); return true;
        }
        if (now < nextCareSearch) return false;
        nextCareSearch = now + 400 + cat.getRandom().nextInt(200);
        scanChildren(now);
        for (UUID id : children) if (resolve(id) instanceof SavannahCatEntity child && cat.distanceToSqr(child) <= 16
                && !child.getCompanionBrain().isThreatened() && child.onGround()) {
            caredChild = id; careUntil = now + 35;
            cat.getNavigation().stop(); cat.getLookControl().setLookAt(child, 12, 12);
            cat.setAction(CompanionAction.SOCIAL, 35); return true;
        }
        return false;
    }
    private void moveTo(Vec3 point, double speed) {
        cat.setSitting(false);
        if (!cat.getAction().interaction()) cat.clearAction();
        navigation.navigate(point, speed);
    }
    private void playfulHop(long now) {
        if (!(cat.level() instanceof ServerLevel level) || !cat.onGround() || cat.isInLiquid()) return;
        Vec3 direction = Vec3.directionFromRotation(0, cat.getYRot()).scale(.12);
        Vec3 point = cat.position(), velocity = direction.add(0, .22, 0);
        for (int i = 0; i < 6; i++) {
            point = point.add(velocity);
            if (!SafetyNavigator.clearAt(level, cat, point)) return;
            velocity = new Vec3(velocity.x * .91, (velocity.y - .08) * .98, velocity.z * .91);
        }
        if (!SafetyNavigator.safeDestination(level, cat, point)) return;
        cat.getNavigation().stop(); cat.setSitting(false); cat.setOnGround(false);
        cat.setDeltaMovement(direction.add(0, .22, 0)); cat.needsSync = true; playHopUntil = now + 7;
    }
    private void rest() { cat.holdSittingPosition(); cat.setAction(CompanionAction.REST, 40); }
    private @Nullable LivingEntity resolve(@Nullable UUID id) {
        if (id == null || !(cat.level() instanceof ServerLevel level)) return null;
        return level.getEntity(id) instanceof LivingEntity living && living.isAlive() ? living : null;
    }
    public int rememberedChildren() { return children.size(); }
}
