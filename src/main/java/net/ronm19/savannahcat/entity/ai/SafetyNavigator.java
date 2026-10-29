package net.ronm19.savannahcat.entity.ai;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ronm19.savannahcat.config.SavannahConfig;
import net.ronm19.savannahcat.entity.SavannahCatEntity;

/** Loaded-chunk navigation, shape-based landing validation and last-resort catch-up/explicit recall. */
public final class SafetyNavigator {
    private final SavannahCatEntity cat;
    private final Map<Long, Long> failedLandings = new LinkedHashMap<>();
    private long nextPathTick;
    private long nextTeleportTick;
    private Vec3 lastProgressPosition;
    private long lastProgressTick;
    private int failedPaths;
    private int pathRequests;
    private long followingSince = -1;
    private String landingDimension = "";

    public SafetyNavigator(SavannahCatEntity cat) {
        this.cat = cat;
        lastProgressPosition = cat.position();
    }

    public boolean navigate(Vec3 destination, double speed) {
        return navigate(destination, speed, false);
    }

    /** Furniture interaction requires the actual endpoint, rather than vanilla's one-block target tolerance. */
    public boolean navigateExact(Vec3 destination, double speed) { return navigate(destination, speed, true); }

    private boolean navigate(Vec3 destination, double speed, boolean exact) {
        if (!(cat.level() instanceof ServerLevel level)) return false;
        long now = level.getGameTime();
        if (now < nextPathTick) return !cat.getNavigation().isDone();
        nextPathTick = now + SavannahConfig.PATH_INTERVAL.get();
        if (!level.hasChunkAt(BlockPos.containing(destination)) || !safeDestination(level, cat, destination)) {
            failedPaths = Math.min(8, failedPaths + 1);
            return false;
        }
        pathRequests++;
        boolean accepted;
        if (exact) {
            var exactPath = cat.getNavigation().createPath(BlockPos.containing(destination), 0);
            accepted = exactPath != null && cat.getNavigation().moveTo(exactPath, speed);
        } else accepted = cat.getNavigation().moveTo(destination.x, destination.y, destination.z, speed);
        var path = cat.getNavigation().getPath();
        if (accepted && path != null && path.canReach()) failedPaths = 0;
        else failedPaths = Math.min(8, failedPaths + 1);
        return accepted;
    }

    public void noteProgress() {
        long now = cat.level().getGameTime();
        if (cat.position().distanceToSqr(lastProgressPosition) > 0.36D) {
            lastProgressPosition = cat.position();
            lastProgressTick = now;
        }
    }

    public boolean tryCatchUp(ServerPlayer owner) {
        if (cat.isStayCommand() || !SavannahConfig.TELEPORT.get()
                || cat.level() != owner.level() || cat.distanceToSqr(owner) < Mth.square(SavannahConfig.TELEPORT_DISTANCE.get())) return false;
        long now = cat.level().getGameTime();
        if (followingSince < 0) { beginFollowing(); return false; }
        if (failedPaths < 3 && now - lastProgressTick < 80) return false;
        return relocate(owner, false);
    }

    /** Explicit, owner-authorized recall works only for an already loaded cat; it does not search unloaded chunks. */
    public boolean recallToOwner(ServerPlayer owner) { return relocate(owner, true); }

    /** A separated loaded kitten may catch up only to its own loaded biological parent, after failed travel. */
    public boolean tryFamilyCatchUp(SavannahCatEntity parent) {
        if (!(cat.level() instanceof ServerLevel level) || parent.level() != level || !cat.isBaby()
                || !cat.getFamilyData().hasParent(parent.getUUID()) || !parent.isAlive() || parent.isDowned()
                || parent.isRecovering() || cat.isStayCommand() || cat.isDowned() || cat.isRecovering()
                || cat.isPassenger() || cat.isLeashed() || !SavannahConfig.TELEPORT.get()
                || cat.distanceToSqr(parent) < 16 * 16 || !parent.onGround() || parent.isInLiquid()
                || parent.isOnFire() || parent.getCompanionBrain().isThreatened()
                || !net.ronm19.savannahcat.companion.family.FamilyInheritance.compatibleOwners(cat, parent)) return false;
        long now = level.getGameTime();
        if (followingSince < 0) { beginFollowing(); return false; }
        if (now < nextTeleportTick || failedPaths < 3 && now - lastProgressTick < 80) return false;
        nextTeleportTick = now + SavannahConfig.TELEPORT_COOLDOWN.get();
        var point = findNear(level, parent.blockPosition(), parent.getId());
        if (point.isEmpty()) return false;
        cat.setSitting(false); cat.getNavigation().stop();
        cat.teleportTo(point.get().x, point.get().y, point.get().z); cat.setDeltaMovement(Vec3.ZERO);
        failedPaths = 0; lastProgressTick = now; return true;
    }

    private boolean relocate(ServerPlayer owner, boolean explicit) {
        if (!(cat.level() instanceof ServerLevel source) || !cat.isOwnedBy(owner)
                || !cat.isAlive() || cat.isDowned() || cat.isRecovering() || cat.isPassenger() || cat.isLeashed()
                || !owner.isAlive() || owner.isSpectator() || !owner.onGround() || owner.isInLiquid() || owner.isOnFire()
                || !SavannahConfig.TELEPORT.get() || (!explicit && cat.isStayCommand())) return false;
        long now = source.getGameTime();
        if (now < nextTeleportTick) return false;
        nextTeleportTick = now + SavannahConfig.TELEPORT_COOLDOWN.get();
        ServerLevel targetLevel = owner.level();
        // Reject destinations near active monsters, rather than rescuing into fresh combat.
        if (targetLevel.hasEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(net.minecraft.world.entity.monster.Monster.class),
                owner.getBoundingBox().inflate(8), mob -> mob.isAlive() && mob.getSensing().hasLineOfSight(owner))) return false;
        Optional<Vec3> landing = findNear(targetLevel, owner.blockPosition(), owner.getId());
        if (landing.isEmpty()) return false;
        cat.setSitting(false);
        cat.getNavigation().stop();
        if (source == targetLevel) {
            Vec3 pos = landing.get();
            cat.teleportTo(pos.x, pos.y, pos.z);
            cat.setDeltaMovement(Vec3.ZERO);
            if (explicit) {
                net.ronm19.savannahcat.companion.family.BreedingService.cancelLove(cat);
                cat.getCompanionBrain().cancelHomeReturn();
            }
            if (explicit && cat.isStayCommand()) cat.getCompanionData().setStayAnchor(cat.blockPosition());
        } else {
            var moved = cat.teleport(new TeleportTransition(targetLevel, landing.get(), Vec3.ZERO,
                    cat.getYRot(), cat.getXRot(), TeleportTransition.DO_NOTHING));
            if (!(moved instanceof SavannahCatEntity transferred)) return false;
            if (explicit) {
                net.ronm19.savannahcat.companion.family.BreedingService.cancelLove(transferred);
                transferred.getCompanionBrain().cancelHomeReturn();
            }
            transferred.getCompanionBrain().getNavigator().nextTeleportTick = targetLevel.getGameTime() + SavannahConfig.TELEPORT_COOLDOWN.get();
            if (explicit && transferred.isStayCommand()) transferred.getCompanionData().setStayAnchor(transferred.blockPosition());
        }
        failedPaths = 0;
        lastProgressTick = now;
        return true;
    }

    private Optional<Vec3> findNear(ServerLevel level, BlockPos center, int seed) {
        long now = level.getGameTime();
        String dimension = level.dimension().identifier().toString();
        if (!dimension.equals(landingDimension)) { failedLandings.clear(); landingDimension = dimension; }
        failedLandings.entrySet().removeIf(entry -> entry.getValue() <= now);
        // Four radius-3/4 rings, cardinal/diagonal points; deterministic per cat to spread a group.
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = ((attempt + cat.getId() + seed) & 7) * Math.PI / 4;
            int radius = attempt < 8 ? 3 : 4;
            int dx = (int)Math.round(Math.cos(angle) * radius), dz = (int)Math.round(Math.sin(angle) * radius);
            for (int dy = 1; dy >= -2; dy--) {
                BlockPos pos = center.offset(dx, dy, dz);
                if (failedLandings.containsKey(pos.asLong())) continue;
                var point = landingAt(level, cat, pos);
                if (point.isPresent()) return point;
                failedLandings.put(pos.asLong(), now + 200);
                while (failedLandings.size() > 64) {
                    Iterator<Long> iterator = failedLandings.keySet().iterator(); iterator.next(); iterator.remove();
                }
            }
        }
        return Optional.empty();
    }

    /** Candidate is the block above the support. Slab/stair heights come from collision geometry, not isAir/isFaceSturdy. */
    public static Optional<Vec3> landingAt(ServerLevel level, SavannahCatEntity cat, BlockPos candidate) {
        BlockPos below = candidate.below();
        if (!level.hasChunkAt(candidate) || !level.hasChunkAt(below) || !level.getWorldBorder().isWithinBounds(candidate)) return Optional.empty();
        BlockState support = level.getBlockState(below);
        if (hazardous(support) || !level.getFluidState(below).isEmpty()) return Optional.empty();
        var shape = support.getCollisionShape(level, below);
        double top = -1;
        for (AABB box : shape.toAabbs()) {
            if (box.minX <= .5 && box.maxX >= .5 && box.minZ <= .5 && box.maxZ >= .5) top = Math.max(top, box.maxY);
        }
        if (top <= 0) return Optional.empty();
        Vec3 feet = new Vec3(candidate.getX() + .5, below.getY() + top, candidate.getZ() + .5);
        AABB destination = cat.getBoundingBox().move(feet.subtract(cat.position()));
        if (!clearAt(level, cat, feet)) return Optional.empty();
        for (BlockPos occupied : BlockPos.betweenClosed(Mth.floor(destination.minX), Mth.floor(destination.minY + .001), Mth.floor(destination.minZ),
                Mth.floor(destination.maxX - .001), Mth.floor(destination.maxY - .001), Mth.floor(destination.maxZ - .001))) {
            if (!level.hasChunkAt(occupied) || hazardous(level.getBlockState(occupied)) || !level.getFluidState(occupied).isEmpty()) return Optional.empty();
        }
        // Keep the full cat width supported; fence tops and thin ledges are unsuitable landings.
        double half = cat.getBbWidth() * .45;
        for (double x : new double[]{.5-half, .5+half}) for (double z : new double[]{.5-half, .5+half}) {
            boolean supported = false;
            for (AABB box : shape.toAabbs()) {
                if (x >= box.minX && x <= box.maxX && z >= box.minZ && z <= box.maxZ && box.maxY >= top - .02) supported = true;
            }
            if (!supported) return Optional.empty();
        }
        return Optional.of(feet);
    }

    public static boolean clearAt(ServerLevel level, SavannahCatEntity cat, Vec3 feet) {
        AABB box = cat.getBoundingBox().move(feet.subtract(cat.position()));
        if (box.minY < level.getMinY() || box.maxY > level.getMaxY() + 1 || !level.getWorldBorder().isWithinBounds(box)) return false;
        for (int x = Mth.floor(box.minX) >> 4; x <= (Mth.floor(box.maxX - .001) >> 4); x++)
            for (int z = Mth.floor(box.minZ) >> 4; z <= (Mth.floor(box.maxZ - .001) >> 4); z++)
                if (!level.hasChunk(x, z)) return false;
        return level.noCollision(cat, box) && !level.containsAnyLiquid(box);
    }

    public static boolean safeDestination(ServerLevel level, SavannahCatEntity cat, Vec3 destination) {
        BlockPos pos = BlockPos.containing(destination);
        if (!level.hasChunkAt(pos) || hazardous(level.getBlockState(pos)) || !level.getFluidState(pos).isEmpty()) return false;
        // Ground paths may descend at most two blocks at their selected endpoint.
        for (int drop = 0; drop <= 2; drop++) {
            BlockPos support = pos.below(drop + 1);
            if (!level.hasChunkAt(support)) return false;
            if (hazardous(level.getBlockState(support)) || !level.getFluidState(support).isEmpty()) return false;
            if (!level.getBlockState(support).getCollisionShape(level, support).isEmpty()) return true;
        }
        return false;
    }

    public static boolean hazardous(BlockState state) {
        return state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.SWEET_BERRY_BUSH);
    }

    public int getPathRequests() { return pathRequests; }
    public void beginFollowing() {
        if (followingSince < 0) {
            followingSince = cat.level().getGameTime();
            lastProgressTick = followingSince;
            lastProgressPosition = cat.position();
            failedPaths = 0;
        }
    }
    public void endFollowing() { followingSince = -1; failedPaths = 0; }
    public int getFailedCandidateCount() { return failedLandings.size(); }
    public int getFailedPaths() { return failedPaths; }
}
