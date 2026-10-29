package net.ronm19.savannahcat.companion.utility;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.ronm19.savannahcat.companion.CompanionAction;
import net.ronm19.savannahcat.companion.CompanionCommand;
import net.ronm19.savannahcat.companion.CompanionData.Trait;
import net.ronm19.savannahcat.config.SavannahConfig;
import net.ronm19.savannahcat.entity.SavannahCatEntity;
import net.ronm19.savannahcat.entity.ai.SafetyNavigator;
import net.ronm19.savannahcat.registry.ModBlocks;
import org.jspecify.annotations.Nullable;

/** One optional approach/use/finish session, driven exclusively by the main behavior coordinator. */
public final class HomeLifeCoordinator {
    private record Candidate(BlockPos pos, HomeLifeState.Kind kind, @Nullable BlockPos window, double score) {}
    private HomeLifeCoordinator() {}

    public static boolean tick(SavannahCatEntity cat) {
        if (!(cat.level() instanceof ServerLevel level)) return false;
        var data = cat.getCompanionData().getUtilityData();
        var session = data.homeLife;
        long now = level.getGameTime();
        if (!eligible(cat)) { interrupt(cat); return false; }
        if (session.active()) {
            if (!validTarget(cat, session) || !FurnitureReservations.reserve(level, session.target, cat.getUUID(), now)) {
                finish(cat, true); return false;
            }
            if (session.phase == HomeLifeState.Phase.USE) {
                if (now >= session.activeUntil) { finish(cat, true); return false; }
                hold(cat, session, now);
                return true;
            }
            if (now >= session.approachUntil) { fail(cat); return false; }
            Vec3 point = session.position();
            if (cat.position().distanceToSqr(point) <= arrival(cat) * arrival(cat)) return beginUse(cat, now);
            if (cat.isStayCommand()) { finish(cat, true); return false; }
            cat.setSitting(false);
            cat.setAction(CompanionAction.INVESTIGATE, 16);
            if (cat.position().distanceToSqr(point) < .8 * .8) {
                cat.getNavigation().stop();
                double speed = cat.isBaby() && cat.position().distanceToSqr(point) < .15 * .15 ? .25 : .45;
                cat.getMoveControl().setWantedPosition(point.x, point.y, point.z, speed);
            } else if (now >= session.nextPath) {
                session.nextPath = now + SavannahConfig.PATH_INTERVAL.get();
                boolean moved = cat.getCompanionBrain().getNavigator().navigateExact(point, .8);
                var path = cat.getNavigation().getPath();
                if (!moved || path == null || !path.canReach()) {
                    session.failures++;
                    if (session.positionIndex + 1 < session.positions.size()) session.positionIndex++;
                    else if (session.failures >= 3) { fail(cat); return false; }
                }
            }
            return true;
        }
        if (now < data.nextFurnitureAt || !relaxedAction(cat.getAction())) return false;
        int interval = SavannahConfig.UTILITY_INTERVAL.get();
        if (data.nextHomeScanAt == 0) data.nextHomeScanAt = cat.isNoAi() ? now : now + Math.floorMod(cat.getId(), Math.min(10, interval));
        if (now < data.nextHomeScanAt) return false;
        data.nextHomeScanAt = now + interval - Math.floorMod(now - cat.getId(), interval);
        var candidates = notice(cat, now);
        for (var candidate : candidates) {
            List<Vec3> positions = interactionPositions(cat, candidate);
            if (positions.isEmpty()) { session.rememberFailure(candidate.pos(), now); continue; }
            if (!FurnitureReservations.reserve(level, candidate.pos(), cat.getUUID(), now)) continue;
            session.target = candidate.pos(); session.kind = candidate.kind(); session.window = candidate.window();
            session.positions = positions; session.positionIndex = 0; session.phase = HomeLifeState.Phase.APPROACH;
            session.approachUntil = now + 160; session.nextPath = now;
            data.furnitureTarget = candidate.pos(); data.windowTarget = candidate.window(); data.carpetRestTarget = candidate.kind() == HomeLifeState.Kind.CARPET;
            cat.getCompanionBrain().pauseForHomeLife();
            if (cat.position().distanceToSqr(session.position()) <= arrival(cat) * arrival(cat)) return beginUse(cat, now);
            cat.setSitting(false);
            cat.setAction(CompanionAction.INVESTIGATE, 16);
            return true;
        }
        return false;
    }

    private static boolean eligible(SavannahCatEntity cat) {
        if (!SavannahConfig.HOME.get() || !cat.isAlive() || cat.isDowned() || cat.isRecovering()
                || cat.getAction() == CompanionAction.SLEEP || cat.getFamilyData().isInLove(cat.level().getGameTime())
                || cat.getTarget() != null || cat.getCompanionBrain().isThreatened()
                || cat.getAction().critical() || cat.getAction().interaction() || !cat.onGround() || cat.isInLiquid() || cat.isOnFire()
                || cat.getCommand() == CompanionCommand.HUNT || cat.getCommand() == CompanionCommand.HARVEST) return false;
        var owner = cat.getOwnerPlayer();
        if (!cat.isTamed()) return cat.level().getNearestPlayer(cat, 8) == null;
        if (owner != null && cat.distanceToSqr(owner) <= 36) return true;
        var home = cat.getCompanionData().getHome();
        return (owner == null || cat.isStayCommand()) && home != null && home.dimension().equals(cat.level().dimension())
                && cat.distanceToSqr(Vec3.atCenterOf(home.pos())) <= 16 * 16;
    }

    private static boolean relaxedAction(CompanionAction action) {
        return action == CompanionAction.IDLE || action == CompanionAction.REST || action == CompanionAction.GROOM
                || action == CompanionAction.STRETCH || action == CompanionAction.INVESTIGATE;
    }

    private static List<Candidate> notice(SavannahCatEntity cat, long now) {
        ServerLevel level = (ServerLevel)cat.level();
        var data = cat.getCompanionData().getUtilityData();
        var home = cat.getCompanionData().getHome();
        List<Candidate> result = new ArrayList<>();
        BlockPos center = cat.blockPosition();
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) for (int y = -1; y <= 1; y++) {
            BlockPos pos = center.offset(x, y, z);
            if (!level.hasChunkAt(pos) || data.homeLife.recentlyFailed(pos, now)
                    || !FurnitureReservations.available(level, pos, cat.getUUID(), now)) continue;
            var state = level.getBlockState(pos);
            HomeLifeState.Kind kind;
            BlockPos window = null;
            if (SavannahConfig.FURNITURE.get() && state.is(ModBlocks.SCRATCHING_POST.get()) && now >= data.nextScratchAt) kind = HomeLifeState.Kind.POST;
            else if (SavannahConfig.FURNITURE.get() && state.is(ModBlocks.FOOD_WATER_BOWL.get()) && BowlService.canUse(cat, pos)) kind = HomeLifeState.Kind.BOWL;
            else if (state.is(BlockTags.WOOL_CARPETS) && home != null && home.dimension().equals(level.dimension()) && home.pos().distSqr(pos) <= 64) {
                kind = HomeLifeState.Kind.CARPET;
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    BlockPos at = pos.relative(direction, 2).above();
                    if (!level.hasChunkAt(at)) continue;
                    var glass = level.getBlockState(at);
                    if (glass.is(Blocks.GLASS) || glass.is(Blocks.GLASS_PANE)
                            || glass.getBlock() instanceof net.minecraft.world.level.block.StainedGlassBlock
                            || glass.getBlock() instanceof net.minecraft.world.level.block.StainedGlassPaneBlock) { window = at; break; }
                }
            } else continue;
            if (cat.isStayCommand() && cat.distanceToSqr(Vec3.atCenterOf(pos)) > 1.5 * 1.5) continue;
            double score = cat.distanceToSqr(Vec3.atCenterOf(pos));
            if (kind == HomeLifeState.Kind.POST) score -= (cat.getPersonality(Trait.PLAYFULNESS) + cat.getPersonality(Trait.CURIOSITY)) * .02;
            if (window != null) score -= 4;
            result.add(new Candidate(pos.immutable(), kind, window, score));
        }
        result.sort(Comparator.comparingDouble(Candidate::score));
        return result.subList(0, Math.min(8, result.size()));
    }

    private static List<Vec3> interactionPositions(SavannahCatEntity cat, Candidate candidate) {
        ServerLevel level = (ServerLevel)cat.level();
        List<Vec3> result = new ArrayList<>(4);
        List<BlockPos> feet = new ArrayList<>(4);
        if (candidate.kind() == HomeLifeState.Kind.CARPET) feet.add(candidate.pos().above());
        else for (Direction direction : Direction.Plane.HORIZONTAL) feet.add(candidate.pos().relative(direction));
        for (BlockPos pos : feet) {
            var landing = SafetyNavigator.landingAt(level, cat, pos);
            if (landing.isEmpty()) continue;
            Vec3 point = landing.get();
            if (cat.isBaby() && candidate.kind() != HomeLifeState.Kind.CARPET) {
                Vec3 center = Vec3.atBottomCenterOf(candidate.pos());
                double standOff = candidate.kind() == HomeLifeState.Kind.POST ? .72 : .70;
                Vec3 direction = point.subtract(center).multiply(1, 0, 1).normalize();
                point = new Vec3(center.x + direction.x * standOff, point.y, center.z + direction.z * standOff);
                if (!SafetyNavigator.clearAt(level, cat, point)) continue;
            }
            var owner = cat.getOwnerPlayer();
            if (!cat.isStayCommand() && owner != null && point.distanceToSqr(owner.position()) > 5.5 * 5.5) continue;
            if (cat.isStayCommand() && cat.position().distanceToSqr(point) > arrival(cat) * arrival(cat)) continue;
            result.add(point);
        }
        result.sort(Comparator.comparingDouble(cat.position()::distanceToSqr));
        return result;
    }

    private static boolean validTarget(SavannahCatEntity cat, HomeLifeState state) {
        if (!cat.level().hasChunkAt(state.target)) return false;
        var block = cat.level().getBlockState(state.target);
        return switch (state.kind) {
            case POST -> SavannahConfig.FURNITURE.get() && block.is(ModBlocks.SCRATCHING_POST.get());
            case BOWL -> SavannahConfig.FURNITURE.get() && block.is(ModBlocks.FOOD_WATER_BOWL.get())
                    && (state.phase == HomeLifeState.Phase.USE || BowlService.canUse(cat, state.target));
            case CARPET -> block.is(BlockTags.WOOL_CARPETS);
        };
    }

    private static boolean beginUse(SavannahCatEntity cat, long now) {
        var data = cat.getCompanionData().getUtilityData(); var state = data.homeLife;
        cat.setSitting(false); cat.getMoveControl().setWait(); cat.holdSittingPosition();
        int duration;
        if (state.kind == HomeLifeState.Kind.POST) {
            duration = 60; state.action = CompanionAction.SCRATCH;
            int interest = cat.getPersonality(Trait.PLAYFULNESS) + cat.getPersonality(Trait.CURIOSITY);
            data.nextScratchAt = now + 2200 - interest * 5 + cat.getRandom().nextInt(200);
            cat.setAction(state.action, duration);
        } else if (state.kind == HomeLifeState.Kind.BOWL) {
            if (!BowlService.interact(cat, state.target)) { finish(cat, true); return false; }
            duration = 48; state.action = cat.getAction();
        } else {
            duration = 40; state.action = cat.getRandom().nextBoolean() ? CompanionAction.GROOM : CompanionAction.REST;
            cat.setAction(state.action, duration);
        }
        state.phase = HomeLifeState.Phase.USE; state.activeUntil = now + duration; state.nextSound = now + 12;
        data.furnitureActiveUntil = state.activeUntil;
        data.nextFurnitureAt = state.activeUntil + 160;
        hold(cat, state, now);
        return true;
    }

    private static void hold(SavannahCatEntity cat, HomeLifeState state, long now) {
        cat.setSitting(false); cat.getMoveControl().setWait(); cat.holdSittingPosition();
        cat.setAction(state.action, Math.max(2, (int)(state.activeUntil - now + 1)));
        BlockPos look = state.window == null ? state.target : state.window;
        double dx = look.getX() + .5 - cat.getX(), dz = look.getZ() + .5 - cat.getZ();
        float yaw = (float)(Math.atan2(dz, dx) * 180 / Math.PI - 90);
        cat.setYRot(yaw); cat.setYBodyRot(yaw); cat.setYHeadRot(yaw);
        cat.getLookControl().setLookAt(look.getX() + .5, look.getY() + (state.kind == HomeLifeState.Kind.POST ? 1.15 : .2), look.getZ() + .5);
        if (state.kind == HomeLifeState.Kind.POST && now >= state.nextSound) {
            state.nextSound = now + 15;
            cat.getCompanionAudio().scratch();
        }
    }

    private static void fail(SavannahCatEntity cat) {
        var state = cat.getCompanionData().getUtilityData().homeLife;
        if (state.target != null) state.rememberFailure(state.target, cat.level().getGameTime());
        finish(cat, true);
    }
    private static void finish(SavannahCatEntity cat, boolean resume) {
        var data = cat.getCompanionData().getUtilityData(); var state = data.homeLife;
        if (!state.active()) return;
        FurnitureReservations.release((ServerLevel)cat.level(), state.target, cat.getUUID());
        if (cat.getAction() == state.action || cat.getAction() == CompanionAction.INVESTIGATE) cat.clearAction();
        cat.getNavigation().stop(); cat.getMoveControl().setWait();
        state.clearSession(); data.furnitureTarget = null; data.furnitureActiveUntil = 0;
        data.nextFurnitureAt = Math.max(data.nextFurnitureAt, cat.level().getGameTime() + 160);
        if (resume) cat.getCompanionBrain().resumeAfterHomeLife();
    }
    public static void interrupt(SavannahCatEntity cat) { if (cat.level() instanceof ServerLevel) finish(cat, false); }
    private static double arrival(SavannahCatEntity cat) { return cat.isBaby() ? .025 : .20; }
}
