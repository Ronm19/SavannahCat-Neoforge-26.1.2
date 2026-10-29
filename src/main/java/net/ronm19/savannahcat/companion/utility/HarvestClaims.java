package net.ronm19.savannahcat.companion.utility;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;

/** Loaded-level work leases, shared by block and item targets, with no retained entity references. */
public final class HarvestClaims {
    private record Lease(UUID cat,long until) {}
    private static final Map<ServerLevel,Map<String,Lease>> LEVELS=new WeakHashMap<>();
    private HarvestClaims(){}
    private static Map<String,Lease> entries(ServerLevel level,long now) {
        var entries=LEVELS.computeIfAbsent(level,l->new LinkedHashMap<>());
        entries.entrySet().removeIf(e->e.getValue().until()<=now);return entries;
    }
    public static boolean available(ServerLevel level,String target,UUID cat) {
        var lease=entries(level,level.getGameTime()).get(target);return lease==null||lease.cat().equals(cat);
    }
    public static boolean reserve(ServerLevel level,String target,UUID cat) {
        var entries=entries(level,level.getGameTime());var lease=entries.get(target);
        if(lease!=null&&!lease.cat().equals(cat)||lease==null&&entries.size()>=512)return false;
        entries.put(target,new Lease(cat,level.getGameTime()+80));return true;
    }
    public static void release(ServerLevel level,String target,UUID cat) {
        var entries=LEVELS.get(level);if(entries==null)return;var lease=entries.get(target);
        if(lease!=null&&lease.cat().equals(cat))entries.remove(target);
    }
    public static void releaseAll(ServerLevel level,UUID cat) {
        var entries=LEVELS.get(level);if(entries!=null)entries.entrySet().removeIf(e->e.getValue().cat().equals(cat));
    }
    public static void clear(){LEVELS.clear();}
}
