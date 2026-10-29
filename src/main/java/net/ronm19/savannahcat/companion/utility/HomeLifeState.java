package net.ronm19.savannahcat.companion.utility;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.ronm19.savannahcat.companion.CompanionAction;

/** Per-cat runtime session; only its persistent cooldowns live in CompanionUtilityData. */
public final class HomeLifeState {
    public enum Kind { POST, BOWL, CARPET }
    public enum Phase { APPROACH, USE }
    public BlockPos target;
    public BlockPos window;
    public Kind kind;
    public Phase phase;
    public List<Vec3> positions = List.of();
    public int positionIndex;
    public int failures;
    public long approachUntil;
    public long activeUntil;
    public long nextPath;
    public long nextSound;
    public CompanionAction action = CompanionAction.IDLE;
    private final Map<Long, Long> failedTargets = new LinkedHashMap<>();
    public boolean active() { return target != null; }
    public Vec3 position() { return positions.get(positionIndex); }
    public boolean recentlyFailed(BlockPos pos, long now) {
        failedTargets.entrySet().removeIf(entry -> entry.getValue() <= now);
        return failedTargets.containsKey(pos.asLong());
    }
    public void rememberFailure(BlockPos pos, long now) {
        failedTargets.put(pos.asLong(), now + 600);
        while (failedTargets.size() > 16) failedTargets.remove(failedTargets.keySet().iterator().next());
    }
    public int failedTargetCount() { return failedTargets.size(); }
    public void clearSession() {
        target = null; window = null; kind = null; phase = null; positions = List.of();
        positionIndex = 0; failures = 0; approachUntil = 0; activeUntil = 0; nextPath = 0; nextSound = 0; action = CompanionAction.IDLE;
    }
    public void reset() { clearSession(); failedTargets.clear(); }
}
