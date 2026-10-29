package net.ronm19.savannahcat.companion.family;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.ronm19.savannahcat.entity.SavannahCatEntity;
import net.ronm19.savannahcat.entity.ai.SafetyNavigator;

/** Loaded-time growth, safe in-place stage transitions and finite love timers. Never replaces the entity. */
public final class FamilyLifecycle {
    private final SavannahCatEntity cat;
    private long lastTick = Long.MIN_VALUE;
    private long nextGrowthRetry;
    public FamilyLifecycle(SavannahCatEntity cat) { this.cat = cat; }
    public void tick() {
        if (!(cat.level() instanceof ServerLevel level) || !cat.isAlive() || lastTick == level.getGameTime()) return;
        long now = level.getGameTime(); lastTick = now;
        NaturalFamilySpawns.tick(cat);
        var data = cat.getFamilyData();
        if (data.getLoveUntil() > 0 && !data.isInLove(now)) data.clearLove();
        if (!data.isBaby() || cat.isDowned() || cat.isRecovering()) return;
        if (now < nextGrowthRetry) return;
        int previous = data.getGrowthRemaining(), previousStage = data.getGrowthStage();
        if (previous <= 1) { if (!tryMature()) nextGrowthRetry = now + 20; return; }
        data.setGrowthRemaining(previous - 1);
        if (data.getGrowthStage() != previousStage) {
            if (!canOccupy(cat, cat.getFamilyDimensions(data.getGrowthStage()))) {
                data.setGrowthRemaining(previous); nextGrowthRetry = now + 20; return;
            }
            cat.syncFamilyState();
        }
    }
    public boolean tryMature() {
        if (!(cat.level() instanceof ServerLevel) || !cat.isAlive() || cat.isDowned() || cat.isRecovering()) return false;
        if (!cat.getFamilyData().isBaby()) return true;
        if (!canOccupy(cat, cat.getFamilyDimensions(4))) return false;
        cat.getFamilyData().setGrowthRemaining(0);
        cat.getFamilyData().clearLove();
        cat.syncFamilyState();
        return true;
    }
    public boolean accelerateGrowth() {
        var data = cat.getFamilyData();
        if (!(cat.level() instanceof ServerLevel) || !cat.isAlive() || !data.isBaby() || data.getGrowthRemaining() <= 1
                || cat.isDowned() || cat.isRecovering()) return false;
        int old = data.getGrowthRemaining();
        int reduction = Math.min(600, Math.max(1, old / 20));
        data.setGrowthRemaining(Math.max(1, old - reduction));
        if (!canOccupy(cat, cat.getFamilyDimensions(data.getGrowthStage()))) { data.setGrowthRemaining(old); return false; }
        cat.syncFamilyState();
        return true;
    }
    public static boolean canOccupy(SavannahCatEntity cat, EntityDimensions dimensions) {
        if (!(cat.level() instanceof ServerLevel level)) return false;
        var box = dimensions.scale(cat.getScale()).makeBoundingBox(cat.position());
        if (box.minY < level.getMinY() || box.maxY > level.getMaxY() + 1 || !level.getWorldBorder().isWithinBounds(box)) return false;
        // Check the complete volume before collision/fluid APIs can inspect neighboring chunks.
        for (int x = Mth.floor(box.minX) >> 4; x <= Mth.floor(box.maxX - .001) >> 4; x++)
            for (int z = Mth.floor(box.minZ) >> 4; z <= Mth.floor(box.maxZ - .001) >> 4; z++)
                if (!level.hasChunk(x, z)) return false;
        if (!level.noCollision(cat, box) || level.containsAnyLiquid(box)) return false;
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(box.minX), Mth.floor(box.minY + .001), Mth.floor(box.minZ),
                Mth.floor(box.maxX - .001), Mth.floor(box.maxY - .001), Mth.floor(box.maxZ - .001)))
            if (!level.hasChunkAt(pos) || SafetyNavigator.hazardous(level.getBlockState(pos)) || !level.getFluidState(pos).isEmpty()) return false;
        return true;
    }
}
