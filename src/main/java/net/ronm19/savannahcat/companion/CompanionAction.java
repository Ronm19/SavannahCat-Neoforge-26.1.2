package net.ronm19.savannahcat.companion;
public enum CompanionAction {
 IDLE,TAME,HEAL,ALERT,STALK,POUNCE,ATTACK,DODGE,RETREAT,GROOM,STRETCH,SCRATCH,REST,SLEEP,PLAY,DOWNED,REVIVE,ACKNOWLEDGE,INVESTIGATE,SOCIAL,EAT,DRINK;
 public int getId() { return ordinal(); }
 public String animation() { return "animation.savannah_cat."+name().toLowerCase(java.util.Locale.ROOT); }
 public static CompanionAction byId(int id) { return id>=0 && id<values().length ? values()[id] : IDLE; }
 public boolean critical() { return this==DOWNED || this==POUNCE || this==ATTACK || this==DODGE || this==RETREAT; }
 public boolean interaction() { return this==TAME || this==HEAL || this==ACKNOWLEDGE || this==REVIVE; }
 /** These clips supply their own moving or emergency pose; other gestures yield to the walking gait. */
 public boolean overridesWalking() { return critical() || this==STALK || this==REVIVE; }
}
