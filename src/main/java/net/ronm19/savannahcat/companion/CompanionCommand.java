package net.ronm19.savannahcat.companion;
public enum CompanionCommand {
 FOLLOW(0), STAY(1), HUNT(2), HARVEST(3);
 private final int id;
 CompanionCommand(int id) { this.id=id; }
 public int getId() { return id; }
 public String key() { return name().toLowerCase(java.util.Locale.ROOT); }
 public static CompanionCommand byId(int id) { for(var v:values()) if(v.id==id)return v; return FOLLOW; }
}
