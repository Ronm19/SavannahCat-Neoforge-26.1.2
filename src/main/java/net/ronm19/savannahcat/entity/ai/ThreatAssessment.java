package net.ronm19.savannahcat.entity.ai;

import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.ronm19.savannahcat.companion.CompanionData.Trait;
import net.ronm19.savannahcat.entity.SavannahCatEntity;
import org.jspecify.annotations.Nullable;

/** Cached contextual strength estimate, with hard limits that courage/bond cannot override. */
public record ThreatAssessment(@Nullable UUID closestThreat, @Nullable UUID defenseTarget,
        double pressure, int hostiles, int allies, boolean overwhelming, boolean ownerEmergency) {
    public static final ThreatAssessment CALM = new ThreatAssessment(null, null, 0, 0, 0, false, false);

    public static ThreatAssessment evaluate(SavannahCatEntity cat, @Nullable ServerPlayer owner,
            List<LivingEntity> visible, @Nullable LivingEntity rememberedAttacker) {
        UUID threat = null, defend = null;
        double closest = Double.MAX_VALUE, pressure = 0;
        int hostileCount = 0, allies = 0;
        boolean absoluteDanger = false;
        LivingEntity ownerAttacker = owner == null ? null : owner.getLastHurtByMob();
        LivingEntity ownerTarget = owner == null ? null : owner.getLastHurtMob();
        boolean recentOwnerHurt = owner != null && owner.tickCount - owner.getLastHurtByMobTimestamp() < 100;
        boolean recentOwnerAttack = owner != null && owner.tickCount - owner.getLastHurtMobTimestamp() < 100;
        for (LivingEntity candidate : visible) {
            if (candidate instanceof SavannahCatEntity companion && companion.isTamed()
                    && !companion.isBaby() && !companion.isDowned() && !companion.isRecovering()
                    && java.util.Objects.equals(cat.getOwnerUUID(), companion.getOwnerUUID())) {
                allies++;
                continue;
            }
            if (CompanionRelations.isFriendly(cat, candidate) && candidate != rememberedAttacker) continue;
            double distance = cat.distanceToSqr(candidate);
            boolean hostile = candidate instanceof Monster
                    || candidate == rememberedAttacker
                    || candidate instanceof Mob mob && (mob.getTarget() == cat || owner != null && mob.getTarget() == owner);
            if (!hostile) continue;
            hostileCount++;
            double damage = candidate.getAttribute(Attributes.ATTACK_DAMAGE) == null ? 2 : candidate.getAttributeValue(Attributes.ATTACK_DAMAGE);
            double contribution = (.55 + candidate.getHealth() / 45D + damage / 12D)
                    * (distance < 36 ? 1.3 : distance < 144 ? .8 : .3);
            pressure += contribution;
            if (distance < closest) { closest = distance; threat = candidate.getUUID(); }
            if (CompanionRelations.isExplosiveOrBoss(candidate) && distance < 18 * 18) absoluteDanger = true;
            if (candidate instanceof net.minecraft.world.entity.player.Player && candidate == rememberedAttacker) absoluteDanger = true;
            boolean genuineDefense = owner != null && ((candidate == ownerAttacker && recentOwnerHurt)
                    || (candidate == ownerTarget && recentOwnerAttack)
                    || candidate instanceof Mob mob && mob.getTarget() == owner);
            if ((genuineDefense || candidate == rememberedAttacker) && CompanionRelations.canFight(cat, candidate)
                    && (owner == null || owner.distanceToSqr(candidate) < 24 * 24)) {
                defend = candidate.getUUID();
            }
        }
        double confidence = (net.ronm19.savannahcat.config.SavannahConfig.PERSONALITY.get() ? cat.getPersonality(Trait.CONFIDENCE) : 50) / 100D;
        double capacity = (1.8 + confidence * .9 + cat.getBondLevel() * .15
                + Math.min(100, cat.getCompanionData().getCourage()) * .006 + Math.min(3, allies) * .45)
                * Math.max(.25, cat.getHealth() / cat.getMaxHealth());
        boolean lowHealth = cat.getHealth() < cat.getMaxHealth() * .28;
        boolean overwhelmed = absoluteDanger || hostileCount > 0 && (lowHealth || pressure > capacity);
        boolean emergency = owner != null && defend != null && owner.getHealth() < owner.getMaxHealth() * .3;
        return new ThreatAssessment(threat, defend, pressure, hostileCount, allies, overwhelmed, emergency);
    }
}
