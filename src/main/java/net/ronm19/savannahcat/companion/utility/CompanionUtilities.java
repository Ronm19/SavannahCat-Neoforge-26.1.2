package net.ronm19.savannahcat.companion.utility;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.ronm19.savannahcat.companion.CompanionCommand;
import net.ronm19.savannahcat.entity.SavannahCatEntity;

/** One coordinator-controlled utility bridge. No independent tick or competing movement Goal. */
public final class CompanionUtilities {
    private CompanionUtilities(){}
    public static boolean tick(SavannahCatEntity cat){
        if(!(cat.level() instanceof ServerLevel))return false;
        if(cat.isDowned()||cat.isRecovering()||cat.getTarget()!=null||cat.getCompanionBrain().isThreatened()){
            HarvestCoordinator.interrupt(cat);HomeLifeCoordinator.interrupt(cat);return false;
        }
        var owner=cat.getOwnerPlayer();
        if(cat.isTamed()&&!cat.isBaby()&&owner!=null&&cat.getCommand()==CompanionCommand.HARVEST){
            HomeLifeCoordinator.interrupt(cat);return HarvestCoordinator.tick(cat,owner);
        }
        HarvestCoordinator.interrupt(cat);
        // A changed command never schedules another delivery journey. Already-nearby owners may receive carried items.
        if(owner!=null&&!cat.isStayCommand()&&cat.distanceToSqr(owner)<=9&&!cat.getCompanionData().getUtilityData().getCarry().isEmpty())
            deliver(cat,owner);
        return HomeLifeCoordinator.tick(cat);
    }
    public static void deliver(SavannahCatEntity cat,ServerPlayer owner){
        if(!cat.isOwnedBy(owner)||cat.level()!=owner.level()||cat.distanceToSqr(owner)>9)return;
        var data=cat.getCompanionData().getUtilityData();
        var inventory=owner.getInventory();
        int ordinarySlots=Math.min(36,inventory.getNonEquipmentItems().size());
        boolean changed=false;
        for(int slot=0;slot<data.getCarry().getContainerSize();slot++){
            ItemStack stack=data.getCarry().getItem(slot);if(stack.isEmpty())continue;
            // Inventory.add discards overflow for Creative players. Transfer only items that really fit.
            for(int pass=0;pass<2&&!stack.isEmpty();pass++){
                for(int target=0;target<ordinarySlots&&!stack.isEmpty();target++){
                    ItemStack existing=inventory.getItem(target);
                    if(pass==0){
                        if(existing.isEmpty()||!existing.isStackable()||!ItemStack.isSameItemSameComponents(existing,stack))continue;
                        int moved=Math.min(stack.getCount(),Math.max(0,inventory.getMaxStackSize(existing)-existing.getCount()));
                        if(moved==0)continue;
                        existing.grow(moved);existing.setPopTime(5);stack.shrink(moved);changed=true;
                    }else if(existing.isEmpty()){
                        int moved=Math.min(stack.getCount(),inventory.getMaxStackSize(stack));
                        if(moved==0)continue;
                        ItemStack inserted=stack.copyWithCount(moved);inserted.setPopTime(5);
                        inventory.setItem(target,inserted);stack.shrink(moved);changed=true;
                    }
                }
            }
            data.getCarry().setItem(slot,stack);
        }
        if(changed)inventory.setChanged();
        data.lastDeliveryAt=cat.level().getGameTime();
    }
}
