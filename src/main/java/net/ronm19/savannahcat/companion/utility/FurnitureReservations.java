package net.ronm19.savannahcat.companion.utility;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Server-thread-only, expiring leases; no entity references or chunk tickets are retained. */
public final class FurnitureReservations {
    private record Lease(UUID cat, long until) {}
    private static final Map<ServerLevel, Map<Long, Lease>> LEVELS = new WeakHashMap<>();
    private FurnitureReservations() {}
    private static Map<Long, Lease> entries(ServerLevel level, long now) {
        var entries = LEVELS.computeIfAbsent(level, key -> new LinkedHashMap<>());
        entries.entrySet().removeIf(entry -> entry.getValue().until() <= now);
        return entries;
    }
    public static boolean available(ServerLevel level, BlockPos pos, UUID cat, long now) {
        var lease = entries(level, now).get(pos.asLong());
        return lease == null || lease.cat().equals(cat);
    }
    public static boolean reserve(ServerLevel level, BlockPos pos, UUID cat, long now) {
        var entries = entries(level, now);
        var current = entries.get(pos.asLong());
        if (current != null && !current.cat().equals(cat) || current == null && entries.size() >= 256) return false;
        entries.put(pos.asLong(), new Lease(cat, now + 80));
        return true;
    }
    public static void release(ServerLevel level, BlockPos pos, UUID cat) {
        var entries = LEVELS.get(level);
        if (entries == null) return;
        var current = entries.get(pos.asLong());
        if (current != null && current.cat().equals(cat)) entries.remove(pos.asLong());
    }
    public static boolean heldBy(ServerLevel level, BlockPos pos, UUID cat) {
        var lease = entries(level, level.getGameTime()).get(pos.asLong());
        return lease != null && lease.cat().equals(cat);
    }
}
