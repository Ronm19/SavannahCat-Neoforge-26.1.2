package net.ronm19.savannahcat.companion;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.ronm19.savannahcat.config.SavannahConfig;
import net.ronm19.savannahcat.entity.SavannahCatEntity;

/** Server lifecycle separated from sensing and presentation. Absolute game-time deadlines survive unloading. */
public final class CompanionLifecycle {
    private final SavannahCatEntity cat;
    private Vec3 travelOrigin;
    private Vec3 ownerTravelOrigin;
    private boolean finalDeath;
    private boolean finalDeathSound;
    public CompanionLifecycle(SavannahCatEntity cat) { this.cat=cat; }
    public void tick() {
        if(!cat.isAlive() || cat.isRemoved())return;
        long now=cat.level().getGameTime();
        var data=cat.getCompanionData();
        if (cat.isDowned()) {
            cat.getNavigation().stop(); cat.setTarget(null); cat.setSitting(false);
            cat.holdSittingPosition();cat.setGlowingTag(true);
            cat.setAction(CompanionAction.DOWNED,40);
            if(now>=data.getDownedUntil() || !SavannahConfig.DOWNED.get()) {
                finalDeath=true;cat.finishDownedDeath(cat.damageSources().generic());
            }
            return;
        }
        if(cat.tickCount%20==0) {
            applyAttributes();
            cat.syncCompanionState();
            if(cat.isRecovering()) cat.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,40,0,false,false));
            if(cat.isTamed() && !cat.isRecovering() && cat.getOwnerPlayer()!=null
                    && cat.distanceToSqr(cat.getOwnerPlayer())<256 && cat.getDeltaMovement().horizontalDistanceSqr()>.0001) {
                if(travelOrigin==null){travelOrigin=cat.position();ownerTravelOrigin=cat.getOwnerPlayer().position();}
                if(travelOrigin.distanceToSqr(cat.position())>=256
                        && ownerTravelOrigin.distanceToSqr(cat.getOwnerPlayer().position())>=256) {
                    cat.recordExperience(CompanionData.Experience.TRAVEL);
                    cat.recordExperience(CompanionData.Experience.ACCOMPANY);
                    travelOrigin=cat.position();
                    ownerTravelOrigin=cat.getOwnerPlayer().position();
                }
            }
        }
    }
    public void applyAttributes() {
        double oldMax=cat.getMaxHealth(), hp=cat.getHealth();
        var health=cat.getAttribute(Attributes.MAX_HEALTH);
        var attack=cat.getAttribute(Attributes.ATTACK_DAMAGE);
        double next=cat.isBaby()?16*SavannahConfig.HEALTH_SCALE.get():SavannahConfig.maxHealth(cat.getBondLevel());
        if(health!=null && Math.abs(health.getBaseValue()-next)>.001) {
            health.setBaseValue(next);
            cat.setHealth((float)Math.min(next,oldMax>0?hp/oldMax*next:hp));
        }
        if(attack!=null)attack.setBaseValue(cat.isBaby()?0:SavannahConfig.meleeDamage(cat.getBondLevel())*(cat.isRecovering()?.5:1));
    }
    public boolean interceptDeath(DamageSource source) {
        if(finalDeath || !SavannahConfig.DOWNED.get() || !cat.isTamed() || cat.isDowned()
                || cat.isRecovering() || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))return false;
        cat.getCompanionData().setDownedUntil(cat.level().getGameTime()+SavannahConfig.downedTicks(cat.getBondLevel()));
        cat.setHealth(1);cat.setTarget(null);cat.setSitting(false);cat.getNavigation().stop();
        cat.setGlowingTag(true);cat.setAction(CompanionAction.DOWNED,40);cat.syncCompanionState();
        net.ronm19.savannahcat.companion.family.BreedingService.cancelLove(cat);
        cat.getCompanionAudio().downed();
        if(cat.getOwnerPlayer()!=null)cat.getOwnerPlayer().sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.savannahcat.downed"));
        return true;
    }
    public boolean revive() {
        if(!cat.isDowned() || !cat.isAlive() || cat.isRemoved() || finalDeath)return false;
        long now=cat.level().getGameTime();
        if(now>=cat.getCompanionData().getDownedUntil())return false;
        cat.getCompanionData().setDownedUntil(0);
        cat.getCompanionData().setRecoveryUntil(now+SavannahConfig.DOWNED_RECOVERY_TICKS.get());
        cat.setHealth(Math.max(1,cat.getMaxHealth()*.25F));cat.setGlowingTag(false);
        cat.clearAction();cat.setAction(CompanionAction.REVIVE,30);cat.syncCompanionState();
        cat.recordExperience(CompanionData.Experience.REVIVE);applyAttributes();
        cat.getCompanionAudio().revived();
        return true;
    }
    public void onFinalDeath() {
        finalDeath=true;
        cat.getCompanionData().setDownedUntil(0);
        cat.getCompanionData().setRecoveryUntil(0);
        cat.setGlowingTag(false);cat.clearAction();cat.syncCompanionState();
        if(!finalDeathSound){cat.getCompanionAudio().death();finalDeathSound=true;}
    }
    public boolean protective(LivingEntity threat) {
        var owner=cat.getOwnerPlayer();long now=cat.level().getGameTime();var data=cat.getCompanionData();
        if(cat.isBaby() || !SavannahConfig.COMBAT.get() || cat.getBondLevel()<5 || cat.isDowned() || cat.isRecovering()
                || owner==null || owner.getHealth()/owner.getMaxHealth()>=.3F || threat==null || !threat.isAlive()
                || cat.distanceToSqr(threat)>256 || now<data.getProtectiveCooldown()
                || net.ronm19.savannahcat.entity.ai.CompanionRelations.isExplosiveOrBoss(threat)
                || !cat.getCompanionBrain().isThreatened())return false;
        data.activateProtective(now);
        cat.addEffect(new MobEffectInstance(MobEffects.STRENGTH,200,0,false,true));
        cat.addEffect(new MobEffectInstance(MobEffects.SPEED,200,0,false,true));
        if(cat.level() instanceof ServerLevel level)level.sendParticles(ParticleTypes.ENCHANT,
                cat.getX(),cat.getY()+.6,cat.getZ(),12,.4,.3,.4,.1);
        return true;
    }
}
