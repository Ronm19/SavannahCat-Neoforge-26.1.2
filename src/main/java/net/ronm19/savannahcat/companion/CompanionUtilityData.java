package net.ronm19.savannahcat.companion;

import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Four small carried stacks; target positions are short-lived coordinator caches, never entity references. */
public final class CompanionUtilityData {
    public final net.ronm19.savannahcat.companion.utility.HarvestState harvest = new net.ronm19.savannahcat.companion.utility.HarvestState();
    public final net.ronm19.savannahcat.companion.utility.HomeLifeState homeLife = new net.ronm19.savannahcat.companion.utility.HomeLifeState();
    private final SimpleContainer carry = createCarry(4);
    public static SimpleContainer createCarry(int slots) {
        return new SimpleContainer(slots) {
            @Override public int getMaxStackSize() { return 16; }
            @Override public ItemStack addItem(ItemStack incoming) {
                ItemStack remainder = incoming.copy();
                for (int pass=0;pass<2;pass++) for(int slot=0;slot<getContainerSize() && !remainder.isEmpty();slot++) {
                    var target=getItem(slot);
                    if(pass==0 && !target.isEmpty() && ItemStack.isSameItemSameComponents(target,remainder)) {
                        int moved=Math.min(remainder.getCount(),Math.min(16,target.getMaxStackSize())-target.getCount());
                        if(moved>0){target.grow(moved);remainder.shrink(moved);}
                    } else if(pass==1 && target.isEmpty()) {
                        int moved=Math.min(remainder.getCount(),Math.min(16,remainder.getMaxStackSize()));
                        setItem(slot,remainder.copyWithCount(moved));remainder.shrink(moved);
                    }
                }
                return remainder;
            }
        };
    }
    public long nextHarvestAt;
    public long nextUtilityScanAt;
    public long nextFurnitureAt;
    public long nextScratchAt;
    public long nextBowlVisitAt;
    public long nextHomeScanAt;
    public long lastDeliveryAt;
    public long furnitureActiveUntil;
    public BlockPos harvestTarget;
    public BlockPos furnitureTarget;
    public BlockPos windowTarget;
    public boolean carpetRestTarget;
    public int failedPaths;

    public SimpleContainer getCarry() { return carry; }

    public void save(ValueOutput output) {
        carry.storeAsItemList(output.list("Carried", ItemStack.CODEC));
        output.putLong("NextHarvest", nextHarvestAt);
        output.putLong("NextFurniture", nextFurnitureAt);
        output.putLong("NextScratch", nextScratchAt);
        output.putLong("NextBowlVisitAt", nextBowlVisitAt);
        output.putLong("LastDelivery", lastDeliveryAt);
    }

    public void load(ValueInput input) {
        carry.fromItemList(input.listOrEmpty("Carried", ItemStack.CODEC));
        nextHarvestAt = Math.max(0L, input.getLongOr("NextHarvest", 0L));
        nextFurnitureAt = Math.max(0L, input.getLongOr("NextFurniture", 0L));
        nextScratchAt = Math.max(0L, input.getLongOr("NextScratch", 0L));
        nextBowlVisitAt = Math.max(0L, input.getLongOr("NextBowlVisitAt", 0L));
        lastDeliveryAt = Math.max(0L, input.getLongOr("LastDelivery", 0L));
        harvestTarget = null;
        furnitureTarget = null;
        windowTarget=null;carpetRestTarget=false;
        nextUtilityScanAt = 0L;
        furnitureActiveUntil=0L;
        failedPaths = 0;
        nextHomeScanAt = 0;
        homeLife.reset();
        harvest.reset();
    }
}
