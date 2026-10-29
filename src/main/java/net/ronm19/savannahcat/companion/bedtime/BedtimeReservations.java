package net.ronm19.savannahcat.companion.bedtime;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.ronm19.savannahcat.entity.SavannahCatEntity;

/** Server-thread-only, bounded UUID leases. No entity or level references are retained in values. */
public final class BedtimeReservations {
    private record Lease(UUID owner, BlockPos bed, BedtimePlaces.Spot spot, long until) {}
    private static final Map<ServerLevel, Map<UUID, Lease>> LEVELS = new WeakHashMap<>();
    private BedtimeReservations() {}

    private static Map<UUID, Lease> leases(ServerLevel level, long now) {
        var leases = LEVELS.computeIfAbsent(level, key -> new HashMap<>());
        leases.entrySet().removeIf(entry -> entry.getValue().until() < now
                || !(level.getEntity(entry.getKey()) instanceof SavannahCatEntity cat) || !cat.isAlive() || cat.isRemoved());
        return leases;
    }
    public static boolean reserve(ServerLevel level, UUID cat, UUID owner, BedtimePlaces.Bed bed, BedtimePlaces.Spot spot, long now, int maxTop) {
        var leases = leases(level, now);
        if (leases.size() >= 512 && !leases.containsKey(cat)) return false;
        int top = 0;
        for (var entry : leases.entrySet()) if (!entry.getKey().equals(cat)) {
            Lease lease = entry.getValue();
            if (lease.spot().bounds().inflate(.40, 0, .40).intersects(spot.bounds())) return false;
            if (lease.bed().equals(bed.head()) && lease.spot().onBed()) top++;
        }
        if (spot.onBed() && top >= maxTop) return false;
        leases.put(cat, new Lease(owner, bed.head(), spot, now + 80));
        return true;
    }
    public static void release(ServerLevel level, UUID cat) { var leases = LEVELS.get(level); if (leases != null) leases.remove(cat); }
    public static int size(ServerLevel level, long now) { return leases(level, now).size(); }
    public static boolean heldBy(ServerLevel level, UUID cat, long now) { return leases(level, now).containsKey(cat); }
    public static void clear() { LEVELS.clear(); }
}
