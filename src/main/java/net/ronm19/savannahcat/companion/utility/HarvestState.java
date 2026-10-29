package net.ronm19.savannahcat.companion.utility;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/** Transient bounded work memory; carried resources alone are persistent. */
public final class HarvestState {
    public final java.util.List<BlockPos> ready = new java.util.ArrayList<>();
    public BlockPos block;
    public UUID item;
    public long until, nextScan, nextPath, nextDelivery;
    public boolean returning, capacityBlocked;
    public int scans, actions, pickups;
    private final Map<String,Long> failed = new LinkedHashMap<>();
    public boolean failed(String key,long now) { failed.entrySet().removeIf(e->e.getValue()<=now); return failed.containsKey(key); }
    public void fail(String key,long now) {
        failed.put(key,now+200);
        while(failed.size()>32)failed.remove(failed.keySet().iterator().next());
    }
    public void clearTarget() { block=null;item=null;until=0;nextPath=0; }
    public void reset() { clearTarget();ready.clear();returning=false;failed.clear();nextScan=0;nextDelivery=0; }
    public int failedCount(){return failed.size();}
}
