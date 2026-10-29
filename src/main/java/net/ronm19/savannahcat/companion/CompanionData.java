package net.ronm19.savannahcat.companion;

import java.util.EnumMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.ronm19.savannahcat.config.SavannahConfig;
import net.ronm19.savannahcat.companion.CompanionUtilityData;
import org.jspecify.annotations.Nullable;

/** Persistent bounded data only. Sensor targets and live entity references belong to expiring brain memory. */
public final class CompanionData {
 public enum Trait { CONFIDENCE,SOCIABILITY,PLAYFULNESS,CURIOSITY,INDEPENDENCE,PREY_DRIVE }
 public enum Experience {
  FEED(2,1200),TRAVEL(3,1200),PLAY(3,1200),HUNT(8,2400),COMBAT(8,1200),INTERACTION(1,1200),ACCOMPANY(2,2400),REVIVE(3,2400);
  final int points,cooldown; Experience(int p,int c){points=p;cooldown=c;}
 }
 private final int[] traits=new int[Trait.values().length];
 private final EnumMap<Experience,Long> nextExperience=new EnumMap<>(Experience.class);
 private final CompanionUtilityData utility=new CompanionUtilityData();
 private boolean initialized;
 private int bondProgress,courage,fatigue=40;
 private long nextSleep;
 private @Nullable GlobalPos home;
 private @Nullable BlockPos stayAnchor;
 private long lastHunt=-2400,downedUntil,recoveryUntil,protectiveUntil,protectiveCooldown;
 public void initialize(RandomSource random) { if(initialized)return; for(int i=0;i<traits.length;i++)traits[i]=random.nextInt(101);initialized=true; }
 public int getPersonality(Trait t) { return traits[t.ordinal()]; }
 public void initializeInheritedTraits(int[] inherited) { if(inherited.length!=traits.length)throw new IllegalArgumentException("Expected six inherited traits");for(int i=0;i<traits.length;i++)traits[i]=Math.clamp(inherited[i],0,100);initialized=true; }
 public int getBondProgress(){return bondProgress;}
 public int getBondLevel(){ if(!SavannahConfig.BONDING.get())return 1; int level=1, cost=SavannahConfig.BOND_EXPERIENCE.get(); while(level<5 && bondProgress>=cost*level*(level+1)/2)level++;return level; }
 public int getCourage(){return courage;}
 public int getFatigue(){return fatigue;}
 public void setFatigue(int value){fatigue=Math.clamp(value,0,100);}
 public long getNextSleep(){return nextSleep;}
 public void setNextSleep(long value){nextSleep=Math.max(0,value);}
 public boolean experience(Experience reason,long now) {
  if(!SavannahConfig.BONDING.get() || now<nextExperience.getOrDefault(reason,Long.MIN_VALUE))return false;
  nextExperience.put(reason,now+reason.cooldown);bondProgress=Math.min(1000000,bondProgress+reason.points);
  if(reason==Experience.COMBAT || reason==Experience.TRAVEL) courage=Math.clamp(courage+(int)Math.round(SavannahConfig.COURAGE_GAIN.get()),0,100);
  return true;
 }
 public @Nullable GlobalPos getHome(){return home;}
 public void setHome(@Nullable GlobalPos p){home=p;}
 public @Nullable BlockPos getStayAnchor(){return stayAnchor;}
 public void setStayAnchor(@Nullable BlockPos p){stayAnchor=p==null?null:p.immutable();}
 public long getLastHunt(){return lastHunt;}
 public void setLastHunt(long tick){lastHunt=tick;}
 public CompanionUtilityData getUtilityData(){return utility;}
 public long getDownedUntil(){return downedUntil;}
 public void setDownedUntil(long t){downedUntil=t;}
 public long getRecoveryUntil(){return recoveryUntil;}
 public void setRecoveryUntil(long t){recoveryUntil=t;}
 public long getProtectiveUntil(){return protectiveUntil;}
 public long getProtectiveCooldown(){return protectiveCooldown;}
 public void activateProtective(long now){protectiveUntil=now+200;protectiveCooldown=now+1500;}
 public void save(ValueOutput out){
  out.putInt("Version",1);out.putBoolean("Initialized",initialized);out.putIntArray("Traits",traits);
  out.putInt("BondProgress",bondProgress);out.putInt("Courage",courage);
  out.putInt("Fatigue",fatigue);out.putLong("NextSleep",nextSleep);
  out.storeNullable("Home",GlobalPos.CODEC,home);out.storeNullable("StayAnchor",BlockPos.CODEC,stayAnchor);
  out.putLong("LastHunt",lastHunt);out.putLong("DownedUntil",downedUntil);out.putLong("RecoveryUntil",recoveryUntil);
  out.putLong("ProtectiveUntil",protectiveUntil);out.putLong("ProtectiveCooldown",protectiveCooldown);
  var xp=out.child("ExperienceCooldowns");nextExperience.forEach((k,v)->xp.putLong(k.name(),v));
  utility.save(out.child("Utility"));
 }
 public void load(ValueInput in){
  int[] saved=in.getIntArray("Traits").orElse(null);if(saved!=null && saved.length==traits.length){for(int i=0;i<traits.length;i++)traits[i]=Math.clamp(saved[i],0,100);initialized=true;}
  bondProgress=Math.clamp(in.getIntOr("BondProgress",0),0,1000000);courage=Math.clamp(in.getIntOr("Courage",0),0,100);
  fatigue=Math.clamp(in.getIntOr("Fatigue",40),0,100);nextSleep=Math.max(0,in.getLongOr("NextSleep",0));
  home=in.read("Home",GlobalPos.CODEC).orElse(null);stayAnchor=in.read("StayAnchor",BlockPos.CODEC).orElse(null);
  lastHunt=in.getLongOr("LastHunt",-2400);downedUntil=in.getLongOr("DownedUntil",0);recoveryUntil=in.getLongOr("RecoveryUntil",0);
  protectiveUntil=in.getLongOr("ProtectiveUntil",0);protectiveCooldown=in.getLongOr("ProtectiveCooldown",0);
  nextExperience.clear();var xp=in.childOrEmpty("ExperienceCooldowns");for(var reason:Experience.values())xp.getLong(reason.name()).ifPresent(v->nextExperience.put(reason,v));
  utility.load(in.childOrEmpty("Utility"));
 }
}
