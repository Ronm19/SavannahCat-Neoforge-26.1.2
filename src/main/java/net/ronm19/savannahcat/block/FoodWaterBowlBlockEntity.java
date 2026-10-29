package net.ronm19.savannahcat.block;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.ronm19.savannahcat.registry.ModBlockEntities;

/** One sixteen-item food stack plus eight water servings; all mutation is server-owned. */
public final class FoodWaterBowlBlockEntity extends BlockEntity {
    public enum Comfort { NONE,FOOD,WATER }
    public static final int MAX_FOOD=16,MAX_WATER=8;
    private UUID owner;
    private ItemStack food=ItemStack.EMPTY;
    private int water;
    private UUID reservedCat;
    private long reservedUntil;
    public FoodWaterBowlBlockEntity(BlockPos pos,BlockState state){super(ModBlockEntities.FOOD_WATER_BOWL.get(),pos,state);}
    public UUID getOwnerUUID(){return owner;}
    public boolean mayUse(UUID player){return owner==null || owner.equals(player);}
    public boolean claim(UUID player){if(!mayUse(player))return false;if(owner==null){owner=player;changedContents();}return true;}
    public ItemStack getFood(){return food.copy();}
    public int getFoodCount(){return food.getCount();}
    public int getWaterServings(){return water;}
    public boolean hasContents(){return !food.isEmpty() || water>0;}
    public boolean canInsertFood(ItemStack incoming){return !incoming.isEmpty() && food.getCount()<Math.min(MAX_FOOD,incoming.getMaxStackSize()) && (food.isEmpty() || ItemStack.isSameItemSameComponents(food,incoming));}
    public boolean insertFood(ItemStack one){
        if(!canInsertFood(one))return false;
        if(food.isEmpty())food=one.copyWithCount(1);else food.grow(1);
        changedContents();return true;
    }
    public boolean addWater(int servings){if(servings<=0 || water>=MAX_WATER)return false;water=Math.min(MAX_WATER,water+servings);changedContents();return true;}
    public boolean availableFor(UUID cat,long now){return hasContents() && (reservedCat==null || reservedUntil<=now);}
    public Comfort consumeComfort(UUID cat,UUID ownerId,long now,boolean preferWater) {
        if(owner==null || !owner.equals(ownerId) || !availableFor(cat,now))return Comfort.NONE;
        reservedCat=cat;reservedUntil=now+50;
        Comfort result;
        if(water>0 && (preferWater || food.isEmpty())){water--;result=Comfort.WATER;}
        else if(!food.isEmpty()){food.shrink(1);result=Comfort.FOOD;}
        else return Comfort.NONE;
        changedContents();return result;
    }
    private void changedContents(){
        setChanged();
        if(level!=null && !level.isClientSide()) {
            BlockState before=level.getBlockState(worldPosition);
            if(before.getBlock() instanceof FoodWaterBowlBlock)level.setBlock(worldPosition,before.setValue(FoodWaterBowlBlock.HAS_FOOD,!food.isEmpty()).setValue(FoodWaterBowlBlock.HAS_WATER,water>0),Block.UPDATE_ALL);
            level.sendBlockUpdated(worldPosition,before,level.getBlockState(worldPosition),Block.UPDATE_CLIENTS);
        }
    }
    @Override protected void saveAdditional(ValueOutput output){
        super.saveAdditional(output);if(owner!=null)output.store("Owner",UUIDUtil.CODEC,owner);
        if(!food.isEmpty())output.store("Food",ItemStack.CODEC,food);output.putInt("Water",water);
    }
    @Override protected void loadAdditional(ValueInput input){
        super.loadAdditional(input);owner=input.read("Owner",UUIDUtil.CODEC).orElse(null);
        food=input.read("Food",ItemStack.CODEC).orElse(ItemStack.EMPTY);
        if(!food.isEmpty())food.setCount(Math.min(food.getCount(),Math.min(MAX_FOOD,food.getMaxStackSize())));
        water=Math.clamp(input.getIntOr("Water",0),0,MAX_WATER);reservedCat=null;reservedUntil=0;
    }
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){
        super.preRemoveSideEffects(pos,state);
        if(level!=null && !level.isClientSide() && !food.isEmpty()) {
            var dropped=food;food=ItemStack.EMPTY;water=0;Block.popResource(level,pos,dropped);
        }
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return saveCustomOnly(registries);}
}
