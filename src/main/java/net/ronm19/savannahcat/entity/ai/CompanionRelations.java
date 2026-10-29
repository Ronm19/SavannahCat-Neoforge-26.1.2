package net.ronm19.savannahcat.entity.ai;

import java.util.UUID;
import java.util.ArrayList;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.ronm19.savannahcat.config.SavannahConfig;
import net.ronm19.savannahcat.entity.SavannahCatEntity;
import net.ronm19.savannahcat.companion.CompanionCommand;

/** Conservative, shared targeting rules. Native damage events still let region/protection mods veto hits. */
public final class CompanionRelations {
    public enum PreyEligibility { ALLOWED, PROTECTED, LOW_POPULATION, NOT_PREY, DISABLED }
    private CompanionRelations() {}

    public static boolean isFriendly(SavannahCatEntity cat, LivingEntity other) {
        if (other == cat || cat.isAlliedTo(other)) return true;
        UUID owner = cat.getOwnerUUID();
        if (owner != null && owner.equals(other.getUUID())) return true;
        // Never intentionally attack players, villagers, or any established pet.
        if (other instanceof Player || other.getType() == net.minecraft.world.entity.EntityType.VILLAGER
                || other.getType() == net.minecraft.world.entity.EntityType.WANDERING_TRADER) return true;
        if (other instanceof SavannahCatEntity companion) {
            return companion.isTamed() || cat.isTamed();
        }
        return other instanceof TamableAnimal pet && pet.isTame()
                || other instanceof OwnableEntity ownable && ownable.getOwnerReference() != null;
    }

    public static boolean canFight(SavannahCatEntity cat, LivingEntity target) {
        return !cat.isBaby() && target.isAlive() && !target.isInvulnerable() && !target.isSpectator()
                && !isFriendly(cat, target) && cat.canAttack(target)
                && !isExplosiveOrBoss(target)
                && (target instanceof Monster || target instanceof Mob mob && mob.getTarget() == cat);
    }

    public static boolean isExplosiveOrBoss(LivingEntity target) {
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).getPath();
        return id.equals("creeper") || id.equals("warden") || id.equals("ravager")
                || id.equals("wither") || id.equals("ender_dragon") || id.equals("elder_guardian");
    }

    /** Cheap permanent/attached-entity protections, before the bounded pen/population scan budget. */
    public static boolean hasProtectedPreyIdentity(SavannahCatEntity cat, LivingEntity prey) {
        if (isFriendly(cat, prey) || prey.hasCustomName() || prey.isBaby() || prey.isPassenger() || prey.isVehicle()) return true;
        return prey instanceof Mob mob && mob.isLeashed();
    }

    public static boolean isProtectedPrey(SavannahCatEntity cat, LivingEntity prey) {
        if (hasProtectedPreyIdentity(cat, prey)) return true;
        // A small bounded pen/crop inspection protects ordinary livestock enclosures even without ownership metadata.
        if (prey.level() instanceof ServerLevel level) {
            BlockPos anchor = prey.blockPosition();
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
                BlockPos pos = anchor.offset(x, 0, z);
                if (!level.hasChunkAt(pos)) return true;
                var state = level.getBlockState(pos);
                var above = level.getBlockState(pos.above());
                if (state.is(BlockTags.FENCES) || state.is(BlockTags.WALLS) || state.is(BlockTags.FENCE_GATES)
                        || above.is(BlockTags.FENCES) || above.is(BlockTags.WALLS)
                        || state.is(Blocks.FARMLAND) || level.getBlockState(pos.below()).is(Blocks.FARMLAND)) return true;
            }
        }
        return false;
    }

    /** Shared eligibility for bounded acquisition, periodic pursuit validation and actual impacts. */
    public static boolean mayHunt(SavannahCatEntity cat, LivingEntity prey) {
        return preyEligibility(cat, prey) == PreyEligibility.ALLOWED;
    }

    public static boolean isCommandHunt(SavannahCatEntity cat) {
        return cat.isTamed() && cat.getCommand() == CompanionCommand.HUNT;
    }

    public static boolean isPreyType(SavannahCatEntity cat, LivingEntity prey) {
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(prey.getType()).toString();
        return !(prey instanceof Monster) && (isCommandHunt(cat)
                ? SavannahConfig.COMMAND_PREY_TYPES.get() : SavannahConfig.PREY_TYPES.get()).contains(id);
    }

    public static PreyEligibility preyEligibility(SavannahCatEntity cat, LivingEntity prey) {
        if (!SavannahConfig.HUNTING.get()) return PreyEligibility.DISABLED;
        if (cat.isBaby() || !prey.isAlive() || prey.level() != cat.level()) return PreyEligibility.PROTECTED;
        if (!isPreyType(cat, prey)) return PreyEligibility.NOT_PREY;
        if (isProtectedPrey(cat, prey)) return PreyEligibility.PROTECTED;
        // A deliberate owner order may catch a lone wild animal; autonomous wild hunting stays conservative.
        if (isCommandHunt(cat)) return PreyEligibility.ALLOWED;
        int floor = SavannahConfig.PREY_POPULATION_FLOOR.get();
        // Only the threshold matters: stop once enough unnamed animals remain locally.
        var population = new ArrayList<LivingEntity>(floor + 1);
        prey.level().getEntities(EntityTypeTest.forClass(LivingEntity.class), prey.getBoundingBox().inflate(16),
                entity -> entity.isAlive() && entity.getType() == prey.getType() && !entity.hasCustomName(), population, floor + 1);
        return population.size() > floor ? PreyEligibility.ALLOWED : PreyEligibility.LOW_POPULATION;
    }
}
