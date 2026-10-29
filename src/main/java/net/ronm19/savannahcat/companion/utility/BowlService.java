package net.ronm19.savannahcat.companion.utility;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;
import net.ronm19.savannahcat.block.FoodWaterBowlBlockEntity;
import net.ronm19.savannahcat.companion.CompanionAction;
import net.ronm19.savannahcat.config.SavannahConfig;
import net.ronm19.savannahcat.entity.SavannahCatEntity;

/** Atomic optional comfort consumption. Bowl availability never affects health, loyalty or survival. */
public final class BowlService {
    public static final int INTERACTION_TICKS=48;
    private BowlService() {}
    public static boolean isFood(ItemStack stack){return SavannahConfig.isFood(stack,false) && stack.has(DataComponents.FOOD);}
    public static boolean isWaterBottle(ItemStack stack){
        var potion=stack.get(DataComponents.POTION_CONTENTS);
        return stack.is(Items.POTION) && potion!=null && potion.is(Potions.WATER);
    }
    public static boolean isRefillItem(ItemStack stack){return isFood(stack) || isWaterBottle(stack) || stack.is(Items.WATER_BUCKET);}
    public static void ownerMessage(ServerPlayer player){player.sendOverlayMessage(Component.translatable("message.savannahcat.bowl_owner"));}
    public static void contentsMessage(ServerPlayer player,FoodWaterBowlBlockEntity bowl){player.sendOverlayMessage(Component.translatable("message.savannahcat.bowl_contents",bowl.getFoodCount(),bowl.getWaterServings()));}
    public static boolean refill(ServerPlayer player,BlockPos pos,InteractionHand hand) {
        var level=player.level();
        if(!SavannahConfig.BOWL_CONTENTS.get() || !SavannahConfig.FURNITURE.get() || !level.hasChunkAt(pos)
                || !player.mayBuild() || player.isSpectator() || !level.mayInteract(player,pos)
                || player.distanceToSqr(Vec3.atCenterOf(pos))>64
                || !(level.getBlockEntity(pos) instanceof FoodWaterBowlBlockEntity bowl))return false;
        if(!bowl.mayUse(player.getUUID())){ownerMessage(player);return false;}
        var held=player.getItemInHand(hand);
        boolean food=isFood(held),bottle=isWaterBottle(held),bucket=held.is(Items.WATER_BUCKET) && SavannahConfig.BOWL_WATER_BUCKETS.get();
        if(!(food && bowl.canInsertFood(held)) && !((bottle || bucket) && bowl.getWaterServings()<FoodWaterBowlBlockEntity.MAX_WATER)) {
            contentsMessage(player,bowl);return false;
        }
        bowl.claim(player.getUUID());
        if(food) {
            if(!bowl.insertFood(held))return false;
            held.consume(1,player);
        } else {
            if(!bowl.addWater(bucket?FoodWaterBowlBlockEntity.MAX_WATER:1))return false;
            // Vanilla helper handles stacked bottles, full inventories and infinite-material players.
            player.setItemInHand(hand,ItemUtils.createFilledResult(held,player,new ItemStack(bucket?Items.BUCKET:Items.GLASS_BOTTLE)));
        }
        level.playSound(null,pos,food?SoundEvents.DECORATED_POT_INSERT:SoundEvents.BOTTLE_EMPTY,SoundSource.BLOCKS,.35F,1.1F);
        contentsMessage(player,bowl);return true;
    }
    /** Selection predicate deliberately omits arrival distance; the coordinator supplies bounded candidates. */
    public static boolean canUse(SavannahCatEntity cat,BlockPos pos) {
        if(!(cat.level() instanceof ServerLevel level) || !SavannahConfig.BOWL_CONTENTS.get() || !SavannahConfig.FURNITURE.get()
                || !cat.isTamed() || !cat.isAlive() || cat.isDowned() || cat.isRecovering() || cat.isOnFire() || cat.isInLiquid()
                || cat.hurtTime>0 || !cat.getCompanionBrain().isSafeToRest() || !level.hasChunkAt(pos)
                || cat.getCompanionData().getUtilityData().nextBowlVisitAt>level.getGameTime()
                || !(level.getBlockEntity(pos) instanceof FoodWaterBowlBlockEntity bowl))return false;
        return cat.getOwnerUUID()!=null && cat.getOwnerUUID().equals(bowl.getOwnerUUID()) && bowl.availableFor(cat.getUUID(),level.getGameTime());
    }
    public static boolean interact(SavannahCatEntity cat,BlockPos pos) {
        if(!canUse(cat,pos) || cat.distanceToSqr(Vec3.atCenterOf(pos))>9)return false;
        var level=(ServerLevel)cat.level();var bowl=(FoodWaterBowlBlockEntity)level.getBlockEntity(pos);
        var result=bowl.consumeComfort(cat.getUUID(),cat.getOwnerUUID(),level.getGameTime(),cat.getRandom().nextBoolean());
        if(result==FoodWaterBowlBlockEntity.Comfort.NONE)return false;
        cat.getCompanionData().getUtilityData().nextBowlVisitAt=level.getGameTime()+SavannahConfig.BOWL_VISIT_COOLDOWN.get();
        boolean drinking=result==FoodWaterBowlBlockEntity.Comfort.WATER;
        cat.getNavigation().stop();cat.getLookControl().setLookAt(pos.getX()+.5D,pos.getY()+.2D,pos.getZ()+.5D);
        cat.setAction(drinking?CompanionAction.DRINK:CompanionAction.EAT,INTERACTION_TICKS);
        if(drinking)cat.getCompanionAudio().drinking();else cat.getCompanionAudio().eating();
        return true;
    }
}
