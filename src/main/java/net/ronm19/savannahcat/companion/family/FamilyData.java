package net.ronm19.savannahcat.companion.family;

import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** Bounded persistent family facts. Missing data means an existing mature adult. */
public final class FamilyData {
    private int growthRemaining;
    private int growthTotal;
    private long nextBreedAt;
    private long loveUntil;
    private long nextGrowthFeedAt;
    private @Nullable UUID loveCause;
    private @Nullable UUID parentA;
    private @Nullable UUID parentB;
    private @Nullable UUID guardian;
    private boolean naturalFamilyChecked;
    // Loaded-only courtship cache: never saved as permanent entity references.
    public @Nullable UUID mate;
    public long nextMateSearch;
    public int closeTicks;
    public long mateUntil;

    public boolean isBaby() { return growthRemaining > 0; }
    public int getGrowthRemaining() { return growthRemaining; }
    public int getGrowthTotal() { return growthTotal; }
    public int getGrowthStage() {
        if (!isBaby()) return 4;
        return Math.clamp((int)((1D - growthRemaining / (double)Math.max(1, growthTotal)) * 4), 0, 3);
    }
    public void startBaby(int ticks) { growthTotal = Math.clamp(ticks, 1, 2_400_000); growthRemaining = growthTotal; clearLove(); }
    public void setGrowthRemaining(int ticks) {
        growthRemaining = Math.clamp(ticks, 0, 2_400_000);
        if (growthRemaining > growthTotal) growthTotal = growthRemaining;
    }
    public long getNextBreedAt() { return nextBreedAt; }
    public void setNextBreedAt(long value) { nextBreedAt = Math.max(0, value); }
    public boolean isInLove(long now) { return !isBaby() && loveUntil > now; }
    public long getLoveUntil() { return loveUntil; }
    public @Nullable UUID getLoveCause() { return loveCause; }
    public void enterLove(@Nullable UUID player, long until) { loveCause = player; loveUntil = Math.max(0, until); resetCourtship(); }
    public void clearLove() { loveUntil = 0; loveCause = null; resetCourtship(); }
    public void resetCourtship() { mate = null; closeTicks = 0; mateUntil = 0; nextMateSearch = 0; }
    public long getNextGrowthFeedAt() { return nextGrowthFeedAt; }
    public void setNextGrowthFeedAt(long tick) { nextGrowthFeedAt = Math.max(0, tick); }
    public @Nullable UUID getParentA() { return parentA; }
    public @Nullable UUID getParentB() { return parentB; }
    public @Nullable UUID getGuardian() { return guardian; }
    public void setParents(@Nullable UUID a, @Nullable UUID b, @Nullable UUID preferred) { parentA = a; parentB = b; guardian = preferred; }
    public boolean hasParent(@Nullable UUID id) { return id != null && (id.equals(parentA) || id.equals(parentB)); }
    public boolean isNaturalFamilyChecked() { return naturalFamilyChecked; }
    public void setNaturalFamilyChecked() { naturalFamilyChecked = true; }
    public void save(ValueOutput output) {
        output.putInt("Version", 1);
        output.putInt("GrowthRemaining", growthRemaining); output.putInt("GrowthTotal", growthTotal);
        output.putLong("NextBreedAt", nextBreedAt); output.putLong("NextGrowthFeedAt", nextGrowthFeedAt);
        output.putLong("LoveUntil", loveUntil); output.storeNullable("LoveCause", UUIDUtil.CODEC, loveCause);
        output.storeNullable("ParentA", UUIDUtil.CODEC, parentA); output.storeNullable("ParentB", UUIDUtil.CODEC, parentB);
        output.storeNullable("Guardian", UUIDUtil.CODEC, guardian); output.putBoolean("NaturalFamilyChecked", naturalFamilyChecked);
    }
    public void load(ValueInput input) {
        growthRemaining = Math.clamp(input.getIntOr("GrowthRemaining", 0), 0, 2_400_000);
        growthTotal = Math.clamp(input.getIntOr("GrowthTotal", growthRemaining), growthRemaining, 2_400_000);
        nextBreedAt = Math.max(0, input.getLongOr("NextBreedAt", 0)); nextGrowthFeedAt = Math.max(0, input.getLongOr("NextGrowthFeedAt", 0));
        loveUntil = Math.max(0, input.getLongOr("LoveUntil", 0)); loveCause = input.read("LoveCause", UUIDUtil.CODEC).orElse(null);
        parentA = input.read("ParentA", UUIDUtil.CODEC).orElse(null); parentB = input.read("ParentB", UUIDUtil.CODEC).orElse(null);
        guardian = input.read("Guardian", UUIDUtil.CODEC).orElse(parentA);
        naturalFamilyChecked = input.getBooleanOr("NaturalFamilyChecked", false);
        if (isBaby()) { loveUntil = 0; loveCause = null; }
        resetCourtship();
    }
}
