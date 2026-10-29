package net.ronm19.savannahcat.companion.utility;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.ronm19.savannahcat.companion.CompanionCommand;
import net.ronm19.savannahcat.config.SavannahConfig;
import net.ronm19.savannahcat.entity.SavannahCatEntity;
import net.ronm19.savannahcat.entity.ai.SafetyNavigator;

/** One HARVEST-mode work selection; scans, actions, paths, leases and failure memories have independent bounds. */
public final class HarvestCoordinator {
    private HarvestCoordinator(){}
    public static boolean tick(SavannahCatEntity cat,ServerPlayer owner) {
        if(!(cat.level() instanceof ServerLevel level)||cat.getCommand()!=CompanionCommand.HARVEST||cat.isBaby()
                ||cat.isDowned()||cat.isRecovering()||cat.getCompanionBrain().isThreatened()||cat.getTarget()!=null
                ||!cat.isOwnedBy(owner)||owner.level()!=level||!SavannahConfig.HARVESTING.get()){
            interrupt(cat);return false;
        }
        var data=cat.getCompanionData().getUtilityData();var state=data.harvest;long now=level.getGameTime();
        int range=SavannahConfig.HARVEST_RANGE.get();
        if(cat.distanceToSqr(owner)>(range+4D)*(range+4D)){interrupt(cat);return false;}
        if(state.returning||full(cat)){
            clearTarget(cat);
            if(data.getCarry().isEmpty()){state.returning=false;return false;}
            if(cat.distanceToSqr(owner)>9){
                state.returning=true;cat.setSitting(false);cat.getCompanionBrain().getNavigator().navigate(owner.position(),1);
                return true;
            }
            if(now>=state.nextDelivery){
                CompanionUtilities.deliver(cat,owner);state.nextDelivery=now+100;
            }
            state.returning=false;
            if(full(cat))return true;
        }
        if(state.item!=null){
            var entity=level.getEntity(state.item);
            if(!(entity instanceof ItemEntity item)||!HarvestPickup.eligible(cat,owner,item)
                    ||item.distanceToSqr(owner)>range*range||now>=state.until){fail(cat);return false;}
            if(!HarvestClaims.reserve(level,HarvestPickup.key(item),cat.getUUID())){clearTarget(cat);return false;}
            if(cat.distanceToSqr(item)<=2.25){
                if(HarvestPickup.pickup(cat,owner,item)){clearTarget(cat);data.nextUtilityScanAt=0;}
                else fail(cat);
                return true;
            }
            return approach(cat,item.position(),now);
        }
        if(state.block!=null){
            var target=state.block;
            if(!level.hasChunkAt(target)||!HarvestService.isHarvestable(level.getBlockState(target))
                    ||owner.distanceToSqr(Vec3.atCenterOf(target))>range*range||now>=state.until){fail(cat);return false;}
            if(!HarvestClaims.reserve(level,HarvestService.key(target),cat.getUUID())){clearTarget(cat);return false;}
            if(cat.distanceToSqr(Vec3.atCenterOf(target))<=7 && clearRay(cat,Vec3.atCenterOf(target))){
                if(now<data.nextHarvestAt)return true;
                if(HarvestService.harvest(cat,owner,target)){clearTarget(cat);data.failedPaths=0;}
                else if(state.capacityBlocked){
                    clearTarget(cat);state.ready.clear();state.returning=true;state.nextScan=now+100;
                }else fail(cat);
                return true;
            }
            return approach(cat,Vec3.atBottomCenterOf(target),now);
        }
        while(!state.ready.isEmpty()){
            var queued=state.ready.removeFirst();
            if(level.hasChunkAt(queued)&&HarvestService.isHarvestable(level.getBlockState(queued))
                    &&owner.distanceToSqr(Vec3.atCenterOf(queued))<=range*range&&!state.failed(HarvestService.key(queued),now)
                    &&HarvestClaims.reserve(level,HarvestService.key(queued),cat.getUUID())){
                state.block=queued;data.harvestTarget=queued;state.until=now+160;return true;
            }
        }
        if(now<state.nextScan)return false;
        state.nextScan=now+SavannahConfig.HARVEST_SCAN_INTERVAL.get();state.scans++;
        BlockPos center=owner.blockPosition();var blocks=new ArrayList<BlockPos>();var seen=new HashSet<Long>();
        for(int x=-range;x<=range;x++)for(int z=-range;z<=range;z++)for(int y=-1;y<=1;y++){
            var pos=center.offset(x,y,z);
            if(!level.hasChunkAt(pos)||owner.distanceToSqr(Vec3.atCenterOf(pos))>range*range)continue;
            var block=level.getBlockState(pos);if(!HarvestService.isHarvestable(block))continue;
            var target=HarvestService.canonical(level,pos);
            if(!seen.add(target.asLong())||state.failed(HarvestService.key(target),now)
                    ||!HarvestClaims.available(level,HarvestService.key(target),cat.getUUID())
                    ||!HarvestService.ownerMayHarvest(cat,owner,target))continue;
            blocks.add(target.immutable());
        }
        blocks.sort(Comparator.comparingDouble(pos->cat.distanceToSqr(Vec3.atCenterOf(pos))));
        var items=new ArrayList<ItemEntity>(16);
        if(SavannahConfig.HARVEST_PICKUP.get())level.getEntities(EntityTypeTest.forClass(ItemEntity.class),
                owner.getBoundingBox().inflate(range,2,range),
                item->HarvestPickup.eligible(cat,owner,item)&&item.distanceToSqr(owner)<=range*range
                        &&!state.failed(HarvestPickup.key(item),now)
                        &&HarvestClaims.available(level,HarvestPickup.key(item),cat.getUUID()),items,16);
        items.sort(Comparator.comparingDouble(cat::distanceToSqr));
        var item=items.isEmpty()?null:items.getFirst();var block=blocks.isEmpty()?null:blocks.getFirst();
        state.ready.clear();
        for(int i=1;i<Math.min(33,blocks.size());i++)state.ready.add(blocks.get(i));
        if(item!=null&&(block==null||cat.distanceToSqr(item)<cat.distanceToSqr(Vec3.atCenterOf(block)))){
            if(HarvestClaims.reserve(level,HarvestPickup.key(item),cat.getUUID())){state.item=item.getUUID();state.until=now+160;}
        }else if(block!=null&&HarvestClaims.reserve(level,HarvestService.key(block),cat.getUUID())){
            state.block=block;data.harvestTarget=block;state.until=now+160;
        }else if(!data.getCarry().isEmpty()){
            state.returning=true;
            if(cat.distanceToSqr(owner)<=9){CompanionUtilities.deliver(cat,owner);state.returning=false;}
        }
        return state.block!=null||state.item!=null||state.returning;
    }
    private static boolean full(SavannahCatEntity cat){
        var carry=cat.getCompanionData().getUtilityData().getCarry();
        for(int i=0;i<Math.min(carry.getContainerSize(),SavannahConfig.CARRY_SLOTS.get());i++){
            var stack=carry.getItem(i);if(stack.isEmpty()||stack.getCount()<Math.min(16,stack.getMaxStackSize()))return false;
        }
        return true;
    }
    private static boolean approach(SavannahCatEntity cat,Vec3 target,long now){
        var state=cat.getCompanionData().getUtilityData().harvest;cat.setSitting(false);
        if(now<state.nextPath)return true;
        state.nextPath=now+SavannahConfig.PATH_INTERVAL.get();
        var level=(ServerLevel)cat.level();Vec3 point=null;
        for(Direction side:Direction.Plane.HORIZONTAL){
            var landing=SafetyNavigator.landingAt(level,cat,BlockPos.containing(target).relative(side));
            if(landing.isPresent()&&(point==null||cat.distanceToSqr(landing.get())<cat.distanceToSqr(point)))point=landing.get();
        }
        if(point==null||!cat.getCompanionBrain().getNavigator().navigateExact(point,.95)){
            if(++cat.getCompanionData().getUtilityData().failedPaths>=3){fail(cat);return false;}
        }
        return true;
    }
    private static boolean clearRay(SavannahCatEntity cat,Vec3 point){
        return cat.level().clip(new ClipContext(cat.getEyePosition(),point,ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,cat)).getType()==HitResult.Type.MISS;
    }
    private static void fail(SavannahCatEntity cat){
        var state=cat.getCompanionData().getUtilityData().harvest;
        if(state.block!=null)state.fail(HarvestService.key(state.block),cat.level().getGameTime());
        if(state.item!=null)state.fail("i:"+state.item,cat.level().getGameTime());
        clearTarget(cat);
    }
    private static void clearTarget(SavannahCatEntity cat){
        if(cat.level() instanceof ServerLevel level)HarvestClaims.releaseAll(level,cat.getUUID());
        var data=cat.getCompanionData().getUtilityData();
        if(data.harvest.block!=null||data.harvest.item!=null){cat.getNavigation().stop();cat.getMoveControl().setWait();}
        data.harvest.clearTarget();data.harvestTarget=null;data.failedPaths=0;
    }
    public static void interrupt(SavannahCatEntity cat){
        var state=cat.getCompanionData().getUtilityData().harvest;
        boolean wasReturning=state.returning;
        clearTarget(cat);state.returning=false;state.ready.clear();
        if(wasReturning){cat.getNavigation().stop();cat.getMoveControl().setWait();}
    }
}
