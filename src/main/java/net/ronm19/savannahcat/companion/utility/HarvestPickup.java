package net.ronm19.savannahcat.companion.utility;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.EventHooks;
import net.ronm19.savannahcat.config.SavannahConfig;
import net.ronm19.savannahcat.entity.SavannahCatEntity;

/** Ownership-filtered, partial pickup into the existing carry inventory. */
public final class HarvestPickup {
    private HarvestPickup(){}
    public static String key(ItemEntity item){return "i:"+item.getUUID();}
    public static boolean eligible(SavannahCatEntity cat,ServerPlayer owner,ItemEntity item) {
        if(!SavannahConfig.HARVEST_PICKUP.get()||cat.isBaby()||!item.isAlive()||item.isRemoved()||item.hasPickUpDelay()
                ||item.level()!=cat.level()||item.getItem().isEmpty()||cat.isDowned()||cat.isRecovering()
                ||!cat.isOwnedBy(owner)||!HarvestService.ownerMayCollect(cat,owner,item.blockPosition()))return false;
        // NeoForge exposes the vanilla pickup target. A thrower is attribution, not pickup ownership.
        if(item.getTarget()!=null&&!item.getTarget().equals(owner.getUUID()))return false;
        var id=BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString();
        if(SavannahConfig.HARVEST_PICKUP_DENYLIST.get().contains(id)
                ||!SavannahConfig.HARVEST_PICKUP_ALLOWLIST.get().isEmpty()&&!SavannahConfig.HARVEST_PICKUP_ALLOWLIST.get().contains(id))return false;
        return capacity(cat,item.getItem())>0;
    }
    public static int capacity(SavannahCatEntity cat,ItemStack stack) {
        var carry=cat.getCompanionData().getUtilityData().getCarry();int space=0;
        for(int i=0;i<Math.min(carry.getContainerSize(),SavannahConfig.CARRY_SLOTS.get());i++) {
            var existing=carry.getItem(i);int limit=Math.min(16,stack.getMaxStackSize());
            if(existing.isEmpty())space+=limit;
            else if(ItemStack.isSameItemSameComponents(existing,stack))space+=Math.max(0,limit-existing.getCount());
        }
        return Math.min(stack.getCount(),space);
    }
    public static boolean pickup(SavannahCatEntity cat,ServerPlayer owner,ItemEntity item) {
        if(!(cat.level() instanceof ServerLevel level)||cat.distanceToSqr(item)>2.25||!eligible(cat,owner,item)
                ||!cat.getSensing().hasLineOfSight(item)||!HarvestClaims.reserve(level,key(item),cat.getUUID()))return false;
        try {
            if(EventHooks.fireItemPickupPre(item,owner).canPickup().isFalse()||!eligible(cat,owner,item))return false;
            var original=item.getItem().copy();int count=capacity(cat,original);if(count<=0)return false;
            var carry=cat.getCompanionData().getUtilityData().getCarry();var moving=original.copyWithCount(count);
            // Add only into configured active slots, so reducing the slot setting cannot expand capacity.
            int remaining=count;
            for(int pass=0;pass<2;pass++)for(int i=0;i<Math.min(carry.getContainerSize(),SavannahConfig.CARRY_SLOTS.get())&&remaining>0;i++){
                var target=carry.getItem(i);int limit=Math.min(16,moving.getMaxStackSize());
                if(pass==0&&!target.isEmpty()&&ItemStack.isSameItemSameComponents(target,moving)){
                    int n=Math.min(remaining,Math.max(0,limit-target.getCount()));target.grow(n);remaining-=n;
                }else if(pass==1&&target.isEmpty()){int n=Math.min(remaining,limit);carry.setItem(i,moving.copyWithCount(n));remaining-=n;}
            }
            int accepted=count-remaining;if(accepted<=0)return false;
            item.getItem().shrink(accepted);
            EventHooks.fireItemPickupPost(item,owner,original);
            cat.take(item,accepted);cat.onItemPickup(item);
            if(item.getItem().isEmpty())item.discard();
            cat.getCompanionData().getUtilityData().harvest.pickups++;
            return true;
        }finally{HarvestClaims.release(level,key(item),cat.getUUID());}
    }
}
