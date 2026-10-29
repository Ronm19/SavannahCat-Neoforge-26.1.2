package net.ronm19.savannahcat.companion.family;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.ronm19.savannahcat.config.SavannahConfig;
import net.ronm19.savannahcat.entity.SavannahCatEntity;

/** Optional one-shot small wild family after a natural adult group settles; disabled by default. */
public final class NaturalFamilySpawns {
    private static final Map<ServerLevel,ExtraSpawns> EXTRAS=new WeakHashMap<>();
    private static final class ExtraSpawns { long tick; int count; }
    private NaturalFamilySpawns() {}
    public static void tick(SavannahCatEntity cat) {
        var data = cat.getFamilyData();
        if (data.isNaturalFamilyChecked() || !(cat.level() instanceof ServerLevel level)) return;
        if (cat.getSpawnType() != EntitySpawnReason.NATURAL || cat.isTamed() || cat.isBaby() || !SavannahConfig.NATURAL_KITTENS.get()) {
            data.setNaturalFamilyChecked(); return;
        }
        // Natural companions may be added before their first physical ground contact.
        if (!cat.onGround() && cat.tickCount < 40) return;
        if (cat.tickCount < 10) return;
        data.setNaturalFamilyChecked();
        if (!BreedingService.safeAdult(cat)) return;
        if(!hasPassiveCapacity(level))return;
        var group = new ArrayList<SavannahCatEntity>(6);
        level.getEntities(EntityTypeTest.forClass(SavannahCatEntity.class), cat.getBoundingBox().inflate(8),
                other -> other.isAlive(), group, 6);
        if (group.size() >= 6) return;
        for (var parent : group) {
            if (parent == cat || parent.isBaby() || parent.isTamed() || parent.getSpawnType() != EntitySpawnReason.NATURAL
                    || parent.getFamilyData().isNaturalFamilyChecked() || !BreedingService.safeAdult(parent)
                    || !cat.getSensing().hasLineOfSight(parent)) continue;
            parent.getFamilyData().setNaturalFamilyChecked();
            if (cat.getRandom().nextInt(8) != 0) return;
            var baby = BreedingService.createOffspring(level, cat, parent);
            if (baby != null) {
                baby.getFamilyData().setNaturalFamilyChecked();
                if (!level.addFreshEntity(baby)) baby.discard();
                else EXTRAS.get(level).count++;
            }
            return;
        }
    }
    private static boolean hasPassiveCapacity(ServerLevel level) {
        var nativeState=level.getChunkSource().getLastSpawnState();
        if(nativeState==null)return false;
        var budget=EXTRAS.computeIfAbsent(level,ignored->new ExtraSpawns());
        long now=level.getGameTime();
        if(budget.tick!=now){budget.tick=now;budget.count=0;}
        int nativeMaximum=MobCategory.CREATURE.getMaxInstancesPerChunk()*nativeState.getSpawnableChunkCount()/(17*17);
        // Deliberately stay below one player's passive allowance even with several players online.
        // This conservative optional addition cannot exceed either the native global or local cap.
        int maximum=Math.min(nativeMaximum,MobCategory.CREATURE.getMaxInstancesPerChunk());
        return nativeState.getMobCategoryCounts().getInt(MobCategory.CREATURE)+budget.count<maximum;
    }
}
