package net.ronm19.savannahcat.companion.bedtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ronm19.savannahcat.entity.SavannahCatEntity;
import net.ronm19.savannahcat.entity.ai.SafetyNavigator;

/** Loaded-only bed geometry. Full approved animation envelopes are larger than the entity hitboxes. */
public final class BedtimePlaces {
    public record Bed(BlockPos head, BlockPos foot, Direction facing) {}
    public record Spot(Vec3 position, float yaw, AABB bounds, boolean onBed) {}
    private record Rated(Spot spot, double score) {}
    private BedtimePlaces() {}

    public static Optional<Bed> resolve(ServerLevel level, ServerPlayer owner) {
        var sleeping = owner.getSleepingPos();
        if (!owner.isSleeping() || sleeping.isEmpty() || !level.hasChunkAt(sleeping.get())) return Optional.empty();
        BlockPos at = sleeping.get();
        var state = level.getBlockState(at);
        // Only beds exposing the standard two-part contract can establish a safe mattress footprint.
        if (!state.isBed(level, at, owner) || !state.hasProperty(BedBlock.PART)) return Optional.empty();
        Direction facing = state.getBedDirection(level, at);
        if (!facing.getAxis().isHorizontal()) return Optional.empty();
        BlockPos head = state.getValue(BedBlock.PART) == BedPart.HEAD ? at : at.relative(facing);
        BlockPos foot = head.relative(facing.getOpposite());
        if (!validHalf(level, owner, head, facing, BedPart.HEAD)
                || !validHalf(level, owner, foot, facing, BedPart.FOOT)) return Optional.empty();
        return Optional.of(new Bed(head.immutable(), foot.immutable(), facing));
    }

    private static boolean validHalf(ServerLevel level, ServerPlayer owner, BlockPos pos, Direction facing, BedPart part) {
        if (!level.hasChunkAt(pos)) return false;
        var state = level.getBlockState(pos);
        return state.isBed(level, pos, owner) && state.hasProperty(BedBlock.PART)
                && state.getValue(BedBlock.PART) == part && state.getBedDirection(level, pos) == facing;
    }

    public static boolean valid(ServerLevel level, ServerPlayer owner, Bed bed) {
        return resolve(level, owner).filter(bed::equals).isPresent();
    }

    /** Includes standing/entry/wake, with margins around the sampled adult and baby mesh envelopes. */
    public static AABB envelope(SavannahCatEntity cat, Vec3 feet, float yaw) {
        double halfWidth = cat.isBaby() ? .37 : .45;
        double halfLength = cat.isBaby() ? .79 : .99;
        double height = cat.isBaby() ? 1.24 : 1.80;
        double angle = Math.toRadians(yaw), c = Math.abs(Math.cos(angle)), s = Math.abs(Math.sin(angle));
        double x = c * halfWidth + s * halfLength, z = c * halfLength + s * halfWidth;
        return new AABB(feet.x - x, feet.y + .012, feet.z - z, feet.x + x, feet.y + height, feet.z + z);
    }

    public static List<Spot> candidates(ServerLevel level, SavannahCatEntity cat, ServerPlayer owner, Bed bed, boolean topFirst) {
        List<Rated> floor = new ArrayList<>(), top = new ArrayList<>();
        float yaw = bed.facing().toYRot();
        // Local snapshot only. Refuse a crowded area rather than ignore unexamined occupants.
        var occupants = level.getEntitiesOfClass(LivingEntity.class, new AABB(bed.head()).inflate(6), e -> e.isAlive() && e != cat);
        if (occupants.size() > 48) return List.of();
        for (int across = -4; across <= 4; across++) for (int along = -3; along <= 2; along++) {
            BlockPos horizontal = bed.head().relative(bed.facing().getClockWise(), across).relative(bed.facing(), along);
            for (int dy = 1; dy >= -1; dy--) {
                var landing = SafetyNavigator.landingAt(level, cat, horizontal.offset(0, dy + 1, 0));
                if (landing.isEmpty()) continue;
                Vec3 feet = landing.get();
                BlockPos support = BlockPos.containing(feet.add(0, -.02, 0));
                boolean mattress = isMattress(level, owner, support, bed.facing());
                if (!mattress && level.getBlockState(support).isBed(level, support, owner)) continue;
                // Mattress points span the midpoint of its two sections, preserving full transition length.
                if (mattress) feet = feet.add(-bed.facing().getStepX() * .5, 0, -bed.facing().getStepZ() * .5);
                Spot spot = new Spot(feet, yaw, envelope(cat, feet, yaw), mattress);
                if (!safe(level, cat, owner, bed, spot, occupants, false)) continue;
                double score = cat.position().distanceToSqr(feet) * .06 + feet.distanceToSqr(Vec3.atCenterOf(bed.head())) * .2;
                var under = level.getBlockState(BlockPos.containing(feet.add(0, -.02, 0)));
                if (under.is(BlockTags.WOOL_CARPETS)) score -= 6;
                var home = cat.getCompanionData().getHome();
                if (home != null && home.dimension().equals(level.dimension()) && home.pos().distToCenterSqr(feet) < 16) score -= 2;
                if (cat.isBaby()) for (var other : occupants) if (other instanceof SavannahCatEntity family
                        && cat.getFamilyData().hasParent(family.getUUID()) && family.isOwnedBy(owner)
                        && family.getCompanionBrain().getSleep().hasActivity()) score += Math.min(16, family.position().distanceToSqr(feet)) * .5 - 5;
                // Stable UUID tie-break spreads siblings without changing targets every scan.
                score += Math.floorMod(cat.getUUID().hashCode() ^ horizontal.hashCode(), 97) * .003;
                (mattress ? top : floor).add(new Rated(spot, score));
            }
        }
        floor.sort(Comparator.comparingDouble(Rated::score)); top.sort(Comparator.comparingDouble(Rated::score));
        List<Spot> result = new ArrayList<>();
        for (Rated rated : topFirst ? top : floor) result.add(rated.spot());
        for (Rated rated : topFirst ? floor : top) result.add(rated.spot());
        return result;
    }

    public static boolean safe(ServerLevel level, SavannahCatEntity cat, ServerPlayer owner, Bed bed, Spot spot) {
        var occupants = level.getEntitiesOfClass(LivingEntity.class, spot.bounds().inflate(1), e -> e.isAlive() && e != cat);
        return occupants.size() <= 48 && safe(level, cat, owner, bed, spot, occupants, false);
    }

    /** Retain a disjoint lease while another accepted family member is still walking to its own spot. */
    public static boolean sessionSafe(ServerLevel level, SavannahCatEntity cat, ServerPlayer owner, Bed bed, Spot spot) {
        var occupants = level.getEntitiesOfClass(LivingEntity.class, spot.bounds().inflate(1), e -> e.isAlive() && e != cat);
        return occupants.size() <= 48 && safe(level, cat, owner, bed, spot, occupants, true);
    }

    private static boolean safe(ServerLevel level, SavannahCatEntity cat, ServerPlayer owner, Bed bed, Spot spot, List<LivingEntity> occupants, boolean allowPassingFamily) {
        AABB bounds = spot.bounds();
        if (!level.getWorldBorder().isWithinBounds(bounds) || bounds.minY < level.getMinY() || bounds.maxY > level.getMaxY()) return false;
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(bounds.minX), Mth.floor(spot.position().y - .05), Mth.floor(bounds.minZ),
                Mth.floor(bounds.maxX - .001), Mth.floor(bounds.maxY), Mth.floor(bounds.maxZ - .001))) {
            if (!level.hasChunkAt(pos) || SafetyNavigator.hazardous(level.getBlockState(pos)) || !level.getFluidState(pos).isEmpty()) return false;
        }
        if (!level.noCollision(cat, bounds) || level.containsAnyLiquid(bounds) || !SafetyNavigator.clearAt(level, cat, spot.position())) return false;
        // The sleeping player's native hitbox is .2x.2; reserve the rendered body across BOTH bed halves.
        AABB playerBody = new AABB(bed.head()).minmax(new AABB(bed.foot())).inflate(.03, .9, .03);
        if (bounds.intersects(playerBody)) return false;
        for (LivingEntity other : occupants) {
            if (other instanceof SavannahCatEntity family && family.isOwnedBy(owner)) {
                var sleep = family.getCompanionBrain().getSleep();
                var trip = sleep.getBedtime(); var destination = trip.getSpot();
                if (!sleep.isBedtimeSleeping() && destination != null
                        && BedtimeReservations.heldBy(level, family.getUUID(), level.getGameTime())
                        && !destination.bounds().inflate(.05, 0, .05).intersects(bounds)
                        && allowPassingFamily) continue;
            }
            AABB occupied = other instanceof SavannahCatEntity companion
                    ? envelope(companion, companion.position(), companion.getYRot()) : other.getBoundingBox();
            if (other != owner && bounds.intersects(occupied.inflate(.06, 0, .06))) return false;
            if (other instanceof ServerPlayer sleeper && sleeper.isSleeping() && sleeper != owner) {
                var otherBed = resolve(level, sleeper);
                if (otherBed.isPresent() && bounds.intersects(new AABB(otherBed.get().head()).minmax(new AABB(otherBed.get().foot())).inflate(.03, .9, .03))) return false;
            }
        }
        // Every corner/edge of the visible body needs level support, not just the tiny native hitbox.
        for (double x = bounds.minX + .015; x <= bounds.maxX; x += .20)
            for (double z = bounds.minZ + .015; z <= bounds.maxZ; z += .20)
                if (!supported(level, owner, x, spot.position().y, z, spot.onBed(), bed.facing())) return false;
        for (double x : new double[]{bounds.minX + .015, bounds.maxX - .015})
            for (double z : new double[]{bounds.minZ + .015, bounds.maxZ - .015})
                if (!supported(level, owner, x, spot.position().y, z, spot.onBed(), bed.facing())) return false;
        return true;
    }

    private static boolean supported(ServerLevel level, ServerPlayer owner, double x, double y, double z, boolean mattress, Direction facing) {
        BlockPos support = BlockPos.containing(x, y - .02, z);
        if (!level.hasChunkAt(support) || mattress && !isMattress(level, owner, support, facing)) return false;
        var state = level.getBlockState(support);
        if (SafetyNavigator.hazardous(state) || !level.getFluidState(support).isEmpty()) return false;
        for (AABB box : state.getCollisionShape(level, support).toAabbs())
            if (x >= support.getX() + box.minX && x <= support.getX() + box.maxX && z >= support.getZ() + box.minZ
                    && z <= support.getZ() + box.maxZ && Math.abs(support.getY() + box.maxY - y) < .025) return true;
        return false;
    }

    private static boolean isMattress(ServerLevel level, ServerPlayer owner, BlockPos pos, Direction facing) {
        if (!level.hasChunkAt(pos)) return false;
        var state = level.getBlockState(pos);
        return state.isBed(level, pos, owner) && state.hasProperty(BedBlock.PART) && state.getBedDirection(level, pos) == facing
                && (!state.hasProperty(BedBlock.OCCUPIED) || !state.getValue(BedBlock.OCCUPIED));
    }
}
