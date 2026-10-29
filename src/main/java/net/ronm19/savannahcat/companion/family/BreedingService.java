package net.ronm19.savannahcat.companion.family;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import net.ronm19.savannahcat.companion.CompanionAction;
import net.ronm19.savannahcat.companion.CompanionCommand;
import net.ronm19.savannahcat.config.SavannahConfig;
import net.ronm19.savannahcat.entity.SavannahCatEntity;
import net.ronm19.savannahcat.entity.ai.SafetyNavigator;
import net.ronm19.savannahcat.registry.ModEntities;
import org.jspecify.annotations.Nullable;

/** Server-authoritative explicit feeding, bounded courtship and a single offspring transaction. */
public final class BreedingService {
    public enum FeedResult {
        NOT_APPLICABLE, LOVE, GROWTH, OWNER_DENIED, COOLDOWN, UNSAFE, ALREADY_IN_LOVE, NO_GROWTH;
        public boolean accepted() { return this == LOVE || this == GROWTH; }
        public String messageKey() { return "message.savannahcat.family." + name().toLowerCase(java.util.Locale.ROOT); }
    }
    private record Pair(UUID first, UUID second) {
        static Pair of(UUID a, UUID b) { return a.compareTo(b) <= 0 ? new Pair(a, b) : new Pair(b, a); }
    }
    // Only present during synchronous addFreshEntity callbacks; no entity/world references are retained.
    private static final Set<Pair> BIRTHING = new HashSet<>();
    private BreedingService() {}
    public static boolean isBreedingFood(ItemStack stack) { return stack.is(Items.COD) || stack.is(Items.SALMON); }
    public static FeedResult feed(SavannahCatEntity cat, Player player, ItemStack food) {
        if (!isBreedingFood(food) || !player.isShiftKeyDown()) return FeedResult.NOT_APPLICABLE;
        if (!(cat.level() instanceof ServerLevel level) || player.isSpectator() || !cat.isAlive()) return FeedResult.UNSAFE;
        if (cat.isTamed() && !cat.isOwnedBy(player)) return FeedResult.OWNER_DENIED;
        if (cat.isDowned() || cat.isRecovering() || cat.getHealth() < cat.getMaxHealth() - .001F
                || cat.getCompanionBrain().isThreatened() || cat.getTarget() != null || cat.isOnFire() || cat.isInLiquid()) return FeedResult.UNSAFE;
        long now = level.getGameTime(); var data = cat.getFamilyData();
        if (data.isBaby()) {
            if (!SavannahConfig.GROWTH_ACCELERATION.get() || !cat.isTamed()) return FeedResult.NO_GROWTH;
            if (now < data.getNextGrowthFeedAt()) return FeedResult.COOLDOWN;
            if (!cat.getFamilyLifecycle().accelerateGrowth()) return FeedResult.NO_GROWTH;
            data.setNextGrowthFeedAt(now + 100);
            cat.getCompanionBrain().wakeFromCommand();
            cat.setAction(CompanionAction.HEAL, 20); hearts(cat, 3);
            return FeedResult.GROWTH;
        }
        if (!SavannahConfig.BREEDING.get() || !safeAdult(cat)) return FeedResult.UNSAFE;
        if (now < data.getNextBreedAt()) return FeedResult.COOLDOWN;
        if (data.isInLove(now)) return FeedResult.ALREADY_IN_LOVE;
        cat.getCompanionBrain().wakeFromCommand();
        data.enterLove(player.getUUID(), now + 600);
        cat.setAction(CompanionAction.ACKNOWLEDGE, 20); hearts(cat, 7);
        return FeedResult.LOVE;
    }
    public static void cancelLove(SavannahCatEntity cat) { cat.getFamilyData().clearLove(); }
    public static boolean safeAdult(SavannahCatEntity cat) {
        return cat.isAlive() && !cat.getFamilyData().isBaby() && !cat.isDowned() && !cat.isRecovering()
                && cat.onGround() && !cat.isInLiquid() && !cat.isOnFire() && cat.hurtTime == 0
                && cat.getHealth() >= cat.getMaxHealth() - .001F && cat.getTarget() == null
                && !cat.getCompanionBrain().isThreatened() && !cat.getAction().critical() && !cat.getAction().interaction()
                && cat.getCommand() != CompanionCommand.HUNT && cat.getCommand() != CompanionCommand.HARVEST;
    }
    public static boolean canPair(SavannahCatEntity a, SavannahCatEntity b) {
        if (a == b || a.level() != b.level() || !SavannahConfig.BREEDING.get() || !safeAdult(a) || !safeAdult(b)
                || !FamilyInheritance.compatibleOwners(a, b)) return false;
        long now = a.level().getGameTime();
        return a.getFamilyData().isInLove(now) && b.getFamilyData().isInLove(now)
                && now >= a.getFamilyData().getNextBreedAt() && now >= b.getFamilyData().getNextBreedAt()
                && (!a.isTamed() || a.getOwnerUUID().equals(a.getFamilyData().getLoveCause()))
                && (!b.isTamed() || b.getOwnerUUID().equals(b.getFamilyData().getLoveCause()));
    }
    /** Called only after urgent decisions and necessary owner return. True reserves navigation for courtship. */
    public static boolean tickCourtship(SavannahCatEntity cat) {
        if (!(cat.level() instanceof ServerLevel level) || cat.isNoAi()) return false;
        long now = level.getGameTime(); var data = cat.getFamilyData();
        if (!data.isInLove(now) || !safeAdult(cat)) return false;
        SavannahCatEntity mate = data.mate == null ? null : level.getEntity(data.mate) instanceof SavannahCatEntity candidate ? candidate : null;
        if (mate != null && (!canPair(cat, mate) || now >= data.mateUntil || cat.distanceToSqr(mate) > 12 * 12)) {
            data.mate = null; data.closeTicks = 0; mate = null; data.nextMateSearch = now + 20;
        }
        if (mate == null && now >= data.nextMateSearch) {
            data.nextMateSearch = now + 20;
            var candidates = new ArrayList<SavannahCatEntity>();
            level.getEntities(EntityTypeTest.forClass(SavannahCatEntity.class), cat.getBoundingBox().inflate(8),
                    other -> other != cat && other.isAlive() && other.getFamilyData().isInLove(now)
                            && FamilyInheritance.compatibleOwners(cat, other), candidates, 8);
            candidates.sort(Comparator.comparingDouble(cat::distanceToSqr));
            for (var candidate : candidates) if (canPair(cat, candidate) && cat.getSensing().hasLineOfSight(candidate)) {
                mate = candidate; data.mate = candidate.getUUID(); data.mateUntil = now + 240; break;
            }
        }
        if (mate == null) return false;
        cat.setSitting(false);
        cat.getLookControl().setLookAt(mate, 15, 15);
        if (cat.distanceToSqr(mate) <= 2.5 * 2.5 && cat.getSensing().hasLineOfSight(mate)) {
            cat.getNavigation().stop(); cat.getMoveControl().setWait();
            cat.setAction(CompanionAction.SOCIAL, 20);
            if (++data.closeTicks >= 60 && cat.getUUID().compareTo(mate.getUUID()) < 0) {
                var baby = tryBreed(cat, mate);
                if (baby == null) { data.closeTicks = 40; data.nextMateSearch = now + 20; }
            }
            if (now % 20 == Math.floorMod(cat.getId(), 20)) hearts(cat, 1);
        } else {
            data.closeTicks = 0;
            if (cat.isStayCommand()) return false;
            cat.setAction(CompanionAction.INVESTIGATE, 20);
            cat.getCompanionBrain().getNavigator().navigate(mate.position(), .8);
        }
        return true;
    }
    public static @Nullable SavannahCatEntity tryBreed(SavannahCatEntity a, SavannahCatEntity b) {
        if (!(a.level() instanceof ServerLevel level) || !canPair(a, b) || a.distanceToSqr(b) > 2.5 * 2.5
                || !a.getSensing().hasLineOfSight(b)) return null;
        Pair pair = Pair.of(a.getUUID(), b.getUUID());
        if (BIRTHING.size() >= 128 || !BIRTHING.add(pair)) return null;
        try {
            var baby = createOffspring(level, a, b);
            if (baby == null) return null;
            long now = level.getGameTime();
            if (!level.addFreshEntity(baby)) { baby.discard(); return null; }
            UUID cause = a.getFamilyData().getLoveCause() == null ? b.getFamilyData().getLoveCause() : a.getFamilyData().getLoveCause();
            a.getFamilyData().clearLove(); b.getFamilyData().clearLove();
            a.getFamilyData().setNextBreedAt(now + SavannahConfig.BREEDING_COOLDOWN.get());
            b.getFamilyData().setNextBreedAt(now + SavannahConfig.BREEDING_COOLDOWN.get());
            if (cause != null) {
                var player = level.getServer().getPlayerList().getPlayer(cause);
                if (player != null) player.awardStat(Stats.ANIMALS_BRED);
            }
            hearts(a, 7); hearts(b, 7);
            return baby;
        } finally { BIRTHING.remove(pair); }
    }
    public static @Nullable SavannahCatEntity createOffspring(ServerLevel level, SavannahCatEntity a, SavannahCatEntity b) {
        if (a == b || a.level() != level || b.level() != level || !a.isAlive() || !b.isAlive()
                || a.isBaby() || b.isBaby() || a.isDowned() || b.isDowned() || a.isRecovering() || b.isRecovering()
                || !FamilyInheritance.compatibleOwners(a, b)) return null;
        var baby = ModEntities.SAVANNAH_CAT.get().create(level, EntitySpawnReason.BREEDING);
        if (baby == null) return null;
        var birth = FamilyInheritance.plan(a, b, a.getRandom());
        baby.getFamilyData().startBaby(SavannahConfig.GROWTH_TICKS.get());
        baby.getFamilyData().setParents(birth.parentA(), birth.parentB(), birth.guardian());
        baby.setVariant(birth.coat());
        baby.getCompanionData().initializeInheritedTraits(birth.traits());
        baby.getCompanionData().setHome(birth.home());
        baby.getCompanionData().setFatigue(55);
        if (birth.owner() != null) baby.inheritOwner(birth.owner(), birth.collar());
        baby.syncFamilyState();
        BlockPos anchor = a.blockPosition();
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = (attempt & 7) * Math.PI / 4;
            int radius = 1 + attempt / 8;
            BlockPos candidate = anchor.offset((int)Math.round(Math.cos(angle) * radius), 0, (int)Math.round(Math.sin(angle) * radius));
            var landing = SafetyNavigator.landingAt(level, baby, candidate);
            if (landing.isPresent()) {
                Vec3 position = landing.get(); baby.snapTo(position.x, position.y, position.z, a.getYRot(), 0);
                return baby;
            }
        }
        baby.discard();
        return null;
    }
    private static void hearts(SavannahCatEntity cat, int count) {
        if (cat.level() instanceof ServerLevel level) level.sendParticles(ParticleTypes.HEART, cat.getX(),
                cat.getY() + cat.getBbHeight() * .7, cat.getZ(), count, .25, .2, .25, .01);
    }
}
