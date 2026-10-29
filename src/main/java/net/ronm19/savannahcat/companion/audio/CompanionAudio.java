package net.ronm19.savannahcat.companion.audio;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.ronm19.savannahcat.companion.CompanionAction;
import net.ronm19.savannahcat.companion.CompanionData.Trait;
import net.ronm19.savannahcat.companion.utility.HomeLifeState;
import net.ronm19.savannahcat.config.SavannahConfig;
import net.ronm19.savannahcat.entity.SavannahCatEntity;
import net.ronm19.savannahcat.registry.ModSounds;
import net.ronm19.savannahcat.registry.ModSounds.Cue;

/** Server-only event audio with per-cat gates and bounded spatial group budgets; contains no client controller hook. */
public final class CompanionAudio {
    private static final Map<ServerLevel,Map<Long,Budget>> BUDGETS=new WeakHashMap<>();
    private static final class Budget {long voiceStart,stepStart;int voices,steps;}
    private final SavannahCatEntity cat;
    private final EnumMap<Cue,Long> ready=new EnumMap<>(Cue.class);
    private long nextAmbient,nextBehavior,sleepSince;
    private boolean sleeping,deathPlayed;
    private CompanionAction previousAction=CompanionAction.IDLE;
    public CompanionAudio(SavannahCatEntity cat){this.cat=cat;}

    public void tick() {
        if(!(cat.level() instanceof ServerLevel level) || !enabled() || !cat.isAlive() || cat.isDowned() || cat.isNoAi())return;
        long now=level.getGameTime();var action=cat.getAction();
        if(action==CompanionAction.SLEEP) {
            if(!sleeping){sleeping=true;sleepSince=now;nextBehavior=Math.max(nextBehavior,now+120);}
            if(now-sleepSince>=120 && now>=nextBehavior){sleeping();nextBehavior=now+Math.max(240,SavannahConfig.BEHAVIOR_SOUND_INTERVAL.get()*3);}
            previousAction=action;return;
        }
        sleeping=false;
        if(cat.isRecovering() || cat.hurtTime>0 || cat.isInLiquid() || cat.isOnFire() || cat.getSusanTiltSide()!=0){previousAction=action;return;}
        if(cat.getCompanionBrain().isThreatened()) {
            if(now>=nextBehavior){
                var threat=cat.getTarget();
                if(threat!=null && cat.distanceToSqr(threat)<=16)hiss();else growl();
                nextBehavior=now+SavannahConfig.BEHAVIOR_SOUND_INTERVAL.get();
            }
            previousAction=action;return;
        }
        if(action==CompanionAction.PLAY && previousAction!=action)playing();
        previousAction=action;
        if(!relaxed(action))return;
        if(nextAmbient==0){nextAmbient=now+ambientInterval();return;}
        if(now<nextAmbient)return;
        nextAmbient=now+ambientInterval();
        var owner=cat.getOwnerPlayer();
        if(owner!=null && cat.distanceToSqr(owner)<=16 && cat.getDeltaMovement().horizontalDistanceSqr()<.0001 && cat.getRandom().nextBoolean())purr();
        else emit(Cue.AMBIENT,.28F,1,80,true);
    }
    private boolean relaxed(CompanionAction action){return action==CompanionAction.IDLE || action==CompanionAction.REST || action==CompanionAction.GROOM || action==CompanionAction.INVESTIGATE || action==CompanionAction.SOCIAL;}
    private int ambientInterval(){
        double personality=1+(cat.getPersonality(Trait.INDEPENDENCE)-cat.getPersonality(Trait.SOCIABILITY))/250D;
        double variation=SavannahConfig.SOUND_VARIATION.get()?.8+cat.getRandom().nextDouble()*.4:1;
        return Math.max(60,(int)Math.round(SavannahConfig.AMBIENT_SOUND_INTERVAL.get()*personality*variation*(cat.isBaby()?.9:1)));
    }
    private boolean enabled(){return SavannahConfig.SOUNDS.get() && !cat.isSilent() && SavannahConfig.SOUND_VOLUME.get()>0;}
    public boolean hurt(){return !cat.isDowned() && emit(Cue.HURT,.45F,1,10,false);}
    public boolean downed(){return cat.isDowned() && emit(Cue.HURT,.30F,.95F,10,false);}
    public boolean death(){if(deathPlayed || cat.isDowned())return false;deathPlayed=true;return emit(Cue.DEATH,.45F,.94F,200,false);}
    public boolean tamed(){return cat.isTamed() && emit(Cue.TAMING,.35F,1.04F,30,true);}
    public boolean revived(){return !cat.isDowned() && cat.isRecovering() && emit(Cue.REVIVAL,.30F,1,40,true);}
    /** Call only after a real item/serving was consumed. The short gate avoids duplicate interaction hooks. */
    public boolean eating(){return !cat.isDowned() && emit(Cue.EATING,.22F,1,8,true);}
    public boolean drinking(){return !cat.isDowned() && emit(Cue.DRINKING,.18F,1,8,true);}
    public boolean playing(){return cat.getAction()==CompanionAction.PLAY && !cat.isDowned() && emit(Cue.PLAYING,.20F,1.04F,80,true);}
    public boolean purr(){return cat.getCompanionBrain().isSafeToRest() && !cat.isDowned() && emit(Cue.PURR,.17F,1,120,true);}
    public boolean hiss(){return cat.getCompanionBrain().isThreatened() && !cat.isDowned() && emit(Cue.HISS,.30F,1,80,true);}
    public boolean growl(){return cat.getCompanionBrain().isThreatened() && !cat.isDowned() && emit(Cue.GROWL,.22F,1,100,true);}
    public boolean sleeping(){return cat.getAction()==CompanionAction.SLEEP && !cat.isDowned() && emit(Cue.SLEEPING,.075F,1,240,true);}
    public boolean scratch(){
        var session=cat.getCompanionData().getUtilityData().homeLife;
        return cat.getAction()==CompanionAction.SCRATCH && session.active() && session.kind==HomeLifeState.Kind.POST && session.phase==HomeLifeState.Phase.USE
                && !cat.isDowned() && emit(Cue.SCRATCHING,.14F,1,8,true);
    }
    /** Called by Entity's native step cadence, preserving its surface choice and speed-based timing. */
    public boolean step(BlockPos pos,BlockState state) {
        if(!cat.onGround() || cat.isSitting() || cat.isDowned() || cat.getAction()==CompanionAction.SLEEP || cat.getDeltaMovement().horizontalDistanceSqr()<.0001)return false;
        var material=state.getSoundType(cat.level(),pos,cat);
        SoundEvent sound=material.getStepSound();
        // Canonical grass padding has our subtitle; other surfaces retain their native material sound.
        if(sound==SoundEvents.GRASS_STEP)sound=ModSounds.sound(Cue.STEP);
        return emitSound(Cue.STEP,sound,SavannahConfig.FOOTSTEP_VOLUME.get().floatValue(),material.getPitch(),2,true);
    }
    private boolean emit(Cue cue,float volume,float pitch,int cooldown,boolean optional){return emitSound(cue,ModSounds.sound(cue),volume,pitch,cooldown,optional);}
    private boolean emitSound(Cue cue,SoundEvent sound,float volume,float pitch,int cooldown,boolean optional) {
        if(!(cat.level() instanceof ServerLevel level) || !enabled() || cat.isRemoved())return false;
        if(cue!=Cue.DEATH && !cat.isAlive())return false;
        long now=level.getGameTime();if(now<ready.getOrDefault(cue,Long.MIN_VALUE))return false;
        if(optional && !takeBudget(level,cat.blockPosition(),now,cue==Cue.STEP))return false;
        ready.put(cue,now+cooldown);
        float variation=SavannahConfig.SOUND_VARIATION.get()?(cat.getRandom().nextFloat()-.5F)*.10F:0;
        float finalPitch=Math.clamp(pitch+(cat.isBaby()?.18F:0)+variation,.65F,1.45F);
        float finalVolume=Math.clamp(volume*SavannahConfig.SOUND_VOLUME.get().floatValue()*(cat.isBaby()?.72F:1),0,1);
        if(finalVolume<=0)return false;
        level.playSound(null,cat.getX(),cat.getY(),cat.getZ(),sound,SoundSource.NEUTRAL,finalVolume,finalPitch);
        return true;
    }
    private static boolean takeBudget(ServerLevel level,BlockPos pos,long now,boolean step) {
        var cells=BUDGETS.computeIfAbsent(level,key->new LinkedHashMap<>());
        cells.entrySet().removeIf(entry->now-Math.max(entry.getValue().voiceStart,entry.getValue().stepStart)>200);
        long key=BlockPos.asLong(Math.floorDiv(pos.getX(),16),Math.floorDiv(pos.getY(),16),Math.floorDiv(pos.getZ(),16));
        var budget=cells.get(key);
        if(budget==null){if(cells.size()>=512)return false;budget=new Budget();budget.voiceStart=now;budget.stepStart=now;cells.put(key,budget);}
        if(step){if(now-budget.stepStart>=10){budget.stepStart=now;budget.steps=0;}if(budget.steps>=6)return false;budget.steps++;}
        else{if(now-budget.voiceStart>=40){budget.voiceStart=now;budget.voices=0;}if(budget.voices>=4)return false;budget.voices++;}
        return true;
    }
}
