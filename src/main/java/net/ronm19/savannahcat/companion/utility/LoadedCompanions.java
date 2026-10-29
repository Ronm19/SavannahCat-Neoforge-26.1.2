package net.ronm19.savannahcat.companion.utility;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.ronm19.savannahcat.config.SavannahConfig;
import net.ronm19.savannahcat.entity.SavannahCatEntity;

/** Event-maintained loaded-only owner index. Never scans worlds, loads chunks, or retains entity/world objects strongly. */
public final class LoadedCompanions {
    private static final Map<UUID,LinkedHashMap<UUID,WeakReference<SavannahCatEntity>>> OWNERS = new LinkedHashMap<>();
    private LoadedCompanions() {}
    public static synchronized void track(SavannahCatEntity cat) {
        UUID owner=cat.getOwnerUUID();
        if(owner!=null && !cat.level().isClientSide()) OWNERS.computeIfAbsent(owner,key->new LinkedHashMap<>())
                .put(cat.getUUID(),new WeakReference<>(cat));
    }
    public static synchronized void forget(SavannahCatEntity cat) {
        UUID owner=cat.getOwnerUUID();var cats=OWNERS.get(owner);if(cats==null)return;
        var reference=cats.get(cat.getUUID());
        if(reference!=null && reference.get()==cat)cats.remove(cat.getUUID());
        if(cats.isEmpty())OWNERS.remove(owner);
    }
    public static synchronized void clear() { OWNERS.clear(); }
    public static synchronized List<SavannahCatEntity> forOwner(ServerPlayer owner) {
        var result=new ArrayList<SavannahCatEntity>();var cats=OWNERS.get(owner.getUUID());if(cats==null)return result;
        int inspected=0,cap=SavannahConfig.GROUP_COMMANDS.get()?SavannahConfig.MAX_NEARBY_CATS.get():1;
        var iterator=cats.entrySet().iterator();
        while(iterator.hasNext() && inspected++<SavannahConfig.MAX_SCANNED_ENTITIES.get() && result.size()<cap) {
            var cat=iterator.next().getValue().get();
            if(cat==null || cat.isRemoved() || !cat.isAlive() || !cat.isOwnedBy(owner)
                    || !(cat.level() instanceof net.minecraft.server.level.ServerLevel source)
                    || source.getServer()!=owner.level().getServer()) {
                iterator.remove();continue;
            }
            // Join fires before a newly loaded chunk necessarily becomes entity-accessible. Keep the
            // weak entry through that transition, but never expose it until the level lookup agrees.
            if(source.getEntity(cat.getUUID())!=cat)continue;
            if(cat.level()!=owner.level() || cat.distanceToSqr(owner)<=Math.pow(SavannahConfig.GROUP_RADIUS.get(),2))result.add(cat);
        }
        if(cats.isEmpty())OWNERS.remove(owner.getUUID());return result;
    }
}
