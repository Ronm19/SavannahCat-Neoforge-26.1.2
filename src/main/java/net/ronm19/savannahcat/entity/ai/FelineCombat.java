package net.ronm19.savannahcat.entity.ai;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.ronm19.savannahcat.companion.CompanionAction;
import net.ronm19.savannahcat.companion.CompanionData;
import net.ronm19.savannahcat.config.SavannahConfig;
import net.ronm19.savannahcat.entity.SavannahCatEntity;
import org.jspecify.annotations.Nullable;

/** Bounded observe/stalk/pounce/strike/evade sequence. Every impact revalidates friendship, range and line of sight. */
public final class FelineCombat {
    public static final double MAX_OWNER_DISTANCE = 24.0;
    public enum Phase { OBSERVE, STALK, POUNCE, STRIKE, EVADE, REPOSITION }
    private final SavannahCatEntity cat;
    private final SafetyNavigator navigation;
    private @Nullable UUID targetId;
    private boolean hunting;
    private long pursuitStart;
    private long nextMelee;
    private long strikeAt;
    private long nextPounce;
    private long nextEvade;
    private long leapUntil;
    private long rushUntil;
    private boolean pounceHit;
    private int lostSightTicks;
    private int pounceImpacts;
    private Phase phase = Phase.OBSERVE;

    public FelineCombat(SavannahCatEntity cat, SafetyNavigator navigation) {
        this.cat = cat;
        this.navigation = navigation;
    }

    public boolean engage(LivingEntity target, boolean hunt) {
        if (!(cat.level() instanceof ServerLevel level) || cat.isBaby() || cat.isDowned() || cat.isRecovering()) { clear(); return false; }
        if (!validTarget(target, hunt)) { clear(); return false; }
        long now = level.getGameTime();
        if (!target.getUUID().equals(targetId)) {
            targetId = target.getUUID(); hunting = hunt; pursuitStart = now;
            phase = Phase.OBSERVE; strikeAt = 0; pounceHit = false; lostSightTicks = 0;
        }
        cat.setSitting(false);
        cat.setTarget(target);
        cat.getLookControl().setLookAt(target, 20, 20);
        if (now - pursuitStart > SavannahConfig.MAX_CHASE_TICKS.get() || !withinPursuitBounds(target)) {
            clear(); return false;
        }
        boolean visible = cat.getSensing().hasLineOfSight(target);
        lostSightTicks = visible ? 0 : lostSightTicks + 1;
        if (lostSightTicks > 40) { clear(); return false; }
        if (phase == Phase.POUNCE && now <= leapUntil) {
            if (!pounceHit && visible && cat.getBoundingBox().inflate(.35).intersects(target.getBoundingBox())) {
                pounceHit = true;
                if (damage(target, cat.getPounceDamage())) {
                    pounceImpacts++;
                    rushUntil = now + 50;
                    cat.addEffect(new MobEffectInstance(MobEffects.SPEED, 50, 0, false, true));
                    cat.recordExperience(CompanionData.Experience.COMBAT);
                }
            }
            if (cat.horizontalCollision) leapUntil = now;
            return true;
        }
        if (phase == Phase.POUNCE) phase = Phase.REPOSITION;
        if (phase == Phase.EVADE && now <= leapUntil) return true;
        if (phase == Phase.EVADE) phase = Phase.REPOSITION;
        double distance = cat.distanceTo(target);
        boolean commandedHunt = hunting && CompanionRelations.isCommandHunt(cat);
        boolean movingPrey = target.getDeltaMovement().horizontalDistanceSqr() > .0064;
        int observeTicks = commandedHunt && movingPrey ? 0 : 12;
        if (!hunting && SavannahConfig.EVASION.get() && now >= nextEvade && cat.onGround()
                && distance < 3 && (cat.hurtTime > 0 || cat.getCompanionBrain().getThreatAssessment().hostiles() >= 2)) {
            if (evade(target)) { strikeAt = 0; return true; }
        }
        if (strikeAt > 0) {
            if (now >= strikeAt) {
                strikeAt = 0;
                if (visible && cat.isWithinMeleeAttackRange(target)) damage(target, (float)cat.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
                phase = Phase.REPOSITION;
            }
            return true;
        }
        if (visible && cat.isWithinMeleeAttackRange(target) && now >= nextMelee) {
            navigationStop(); phase = Phase.STRIKE;
            cat.setAction(CompanionAction.ATTACK, 12); nextMelee = now + 22;
            // A moving animal must be hit at actual contact, rather than after five stationary ticks.
            if (commandedHunt) { strikeAt = 0; damage(target, (float)cat.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)); }
            else strikeAt = now + 5;
            return true;
        }
        if (SavannahConfig.POUNCE.get() && visible && cat.onGround() && now >= nextPounce
                && distance >= SavannahConfig.POUNCE_MIN.get() && distance <= SavannahConfig.POUNCE_MAX.get()
                && now - pursuitStart >= observeTicks && launchPounce(target)) return true;
        if (now - pursuitStart < observeTicks) {
            phase = Phase.OBSERVE; navigationStop(); cat.setAction(CompanionAction.ALERT, 15); return true;
        }
        phase = Phase.STALK;
        cat.setAction(CompanionAction.STALK, 16);
        Vec3 offset = groupOffset(target.position(), 1.2);
        double speed = now < rushUntil ? 1.35
                : commandedHunt && (movingPrey || now - pursuitStart >= 28) ? 1.4 : hunting ? .72 : 1.1;
        // Formation offsets and native path tolerance must not stop the final approach outside bite range.
        // The floor also covers small configured pounce ranges and a disabled/cooling-down pounce.
        var owner = cat.getOwnerPlayer();
        if (distance <= Math.max(3.0, SavannahConfig.POUNCE_MIN.get())
                || owner != null && owner.distanceToSqr(offset) > MAX_OWNER_DISTANCE * MAX_OWNER_DISTANCE)
            navigation.navigateExact(target.position(), speed);
        else navigation.navigate(offset, speed);
        return true;
    }

    /** Acquisition and pursuit share the same cheap distance limits. */
    boolean withinPursuitBounds(LivingEntity target) {
        var owner = cat.getOwnerPlayer();
        double range = Math.max(SavannahConfig.COMBAT_RANGE.get(), SavannahConfig.POUNCE_MAX.get() + 2);
        return cat.distanceToSqr(target) <= range * range
                && (owner == null || owner.distanceToSqr(target) <= MAX_OWNER_DISTANCE * MAX_OWNER_DISTANCE);
    }

    private boolean validTarget(LivingEntity target, boolean hunt) {
        if (cat.isBaby() || !target.isAlive() || target.level() != cat.level() || CompanionRelations.isFriendly(cat, target)) return false;
        // Expensive pen/population checks occurred at acquisition; recheck essential protections on every hit.
        if (hunt) return !CompanionRelations.hasProtectedPreyIdentity(cat, target);
        return SavannahConfig.COMBAT.get() && CompanionRelations.canFight(cat, target);
    }

    private boolean launchPounce(LivingEntity target) {
        ServerLevel level = (ServerLevel)cat.level();
        Vec3 delta = target.position().subtract(cat.position());
        if (Math.abs(delta.y) > 1.2 || !SafetyNavigator.safeDestination(level, cat, target.position())) return false;
        Vec3 direction = new Vec3(delta.x, 0, delta.z).normalize();
        // Match vanilla air drag/gravity, with enough horizontal impulse for the selected 5-7 block range.
        double horizontalSpeed = Math.max(.5, Math.min(.85, (delta.horizontalDistance() - .35) / ((1 - Math.pow(.91, 15)) / .09)));
        Vec3 predicted = cat.position();
        Vec3 velocity = direction.scale(horizontalSpeed).add(0, .60, 0);
        // A short ballistic arc, not a teleport. The occupied prey is allowed; blocks still reject the leap.
        for (int tick = 1; tick <= 15; tick++) {
            predicted = predicted.add(velocity);
            Vec3 point = predicted;
            var box = cat.getBoundingBox().move(point.subtract(cat.position()));
            if (level.getBlockCollisions(cat, box).iterator().hasNext()) return false;
            velocity = new Vec3(velocity.x * .91, (velocity.y - .08) * .98, velocity.z * .91);
        }
        nextPounce = level.getGameTime() + SavannahConfig.POUNCE_COOLDOWN.get();
        leapUntil = level.getGameTime() + 18;
        pounceHit = false; phase = Phase.POUNCE; navigationStop();
        cat.setAction(CompanionAction.POUNCE, 18);
        cat.setOnGround(false); // The launch must use air friction on its first physical travel tick.
        cat.setDeltaMovement(direction.scale(horizontalSpeed).add(0, .60, 0));
        cat.needsSync = true;
        return true;
    }

    private boolean evade(LivingEntity target) {
        ServerLevel level = (ServerLevel)cat.level();
        Vec3 away = cat.position().subtract(target.position()).multiply(1, 0, 1).normalize();
        double side = (cat.getId() & 1) == 0 ? 1 : -1;
        Vec3 direction = new Vec3(away.x - away.z * side * .6, 0, away.z + away.x * side * .6).normalize();
        Vec3 landing = cat.position().add(direction.scale(.5 * (1 - Math.pow(.91, 12)) / .09));
        if (!SafetyNavigator.safeDestination(level, cat, landing)
                || level.clip(new ClipContext(cat.getEyePosition(), landing.add(0, 1, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, cat)).getType() != HitResult.Type.MISS) return false;
        var landingBox = cat.getBoundingBox().move(landing.subtract(cat.position()));
        for (double x : new double[]{landingBox.minX + .001, landingBox.maxX - .001})
            for (double z : new double[]{landingBox.minZ + .001, landingBox.maxZ - .001}) {
                var support = SafetyNavigator.landingAt(level, cat, BlockPos.containing(x, cat.getY() + .05, z));
                if (support.isEmpty() || Math.abs(support.get().y - cat.getY()) > .6) return false;
            }
        Vec3 predicted = cat.position(), velocity = direction.scale(.5).add(0, .42, 0);
        for (int tick = 1; tick <= 11; tick++) {
            predicted = predicted.add(velocity);
            if (level.getBlockCollisions(cat, cat.getBoundingBox().move(predicted.subtract(cat.position()))).iterator().hasNext()) return false;
            velocity = new Vec3(velocity.x * .91, (velocity.y - .08) * .98, velocity.z * .91);
        }
        nextEvade = level.getGameTime() + SavannahConfig.EVADE_COOLDOWN.get();
        leapUntil = level.getGameTime() + 12;
        phase = Phase.EVADE; navigationStop();
        cat.setAction(CompanionAction.DODGE, 12);
        cat.setOnGround(false);
        cat.setDeltaMovement(direction.scale(.5).add(0, .42, 0));
        cat.needsSync = true;
        return true;
    }

    private boolean damage(LivingEntity target, float amount) {
        if (!(cat.level() instanceof ServerLevel level) || !validTarget(target, hunting)
                || !cat.getSensing().hasLineOfSight(target)
                || hunting && !CompanionRelations.mayHunt(cat, target)) return false;
        boolean alive = target.isAlive();
        boolean hit = target.hurtServer(level, cat.damageSources().mobAttack(cat), amount);
        if (hit && alive && !target.isAlive()) {
            if (hunting) {
                cat.getCompanionData().setLastHunt(level.getGameTime());
                cat.recordExperience(CompanionData.Experience.HUNT);
            } else cat.recordExperience(CompanionData.Experience.COMBAT);
        }
        return hit;
    }

    private Vec3 groupOffset(Vec3 target, double radius) {
        double angle = (cat.getUUID().hashCode() & 7) * Math.PI / 4;
        return target.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
    }

    private void navigationStop() {
        cat.getNavigation().stop();
        cat.getMoveControl().setWait();
        cat.setXxa(0); cat.setZza(0);
    }
    public boolean isControlledLeap() { return cat.level().getGameTime() <= leapUntil && leapUntil > 0; }
    public @Nullable UUID getTargetId() { return targetId; }
    public boolean hasHuntTarget() { return hunting && targetId != null; }
    public Phase getPhase() { return phase; }
    public long getNextPounce() { return nextPounce; }
    public long getNextEvade() { return nextEvade; }
    public int getPounceImpacts() { return pounceImpacts; }
    public void clear() { targetId = null; strikeAt = 0; phase = Phase.OBSERVE; cat.setTarget(null); }
}
