package net.ronm19.savannahcat.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.ronm19.savannahcat.companion.utility.BowlService;
import net.ronm19.savannahcat.companion.utility.CompanionCommands;

/** Optional owner-filled comfort bowl. No ticking block entity or survival requirement. */
public final class FoodWaterBowlBlock extends BaseEntityBlock {
    public static final MapCodec<FoodWaterBowlBlock> CODEC=simpleCodec(FoodWaterBowlBlock::new);
    public static final BooleanProperty HAS_FOOD=BooleanProperty.create("has_food");
    public static final BooleanProperty HAS_WATER=BooleanProperty.create("has_water");
    public FoodWaterBowlBlock(BlockBehaviour.Properties properties) {
        super(properties);registerDefaultState(stateDefinition.any().setValue(HAS_FOOD,false).setValue(HAS_WATER,false));
    }
    @Override public MapCodec<FoodWaterBowlBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(HAS_FOOD,HAS_WATER);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new FoodWaterBowlBlockEntity(pos,state);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return Block.box(2,0,2,14,4,14);}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity by,ItemStack stack) {
        super.setPlacedBy(level,pos,state,by,stack);
        if(by instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof FoodWaterBowlBlockEntity bowl)bowl.claim(player.getUUID());
    }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(!BowlService.isRefillItem(stack))return InteractionResult.TRY_WITH_EMPTY_HAND;
        if(player instanceof ServerPlayer serverPlayer)BowlService.refill(serverPlayer,pos,hand);
        return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if(player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof FoodWaterBowlBlockEntity bowl) {
            if(!bowl.mayUse(serverPlayer.getUUID()))BowlService.ownerMessage(serverPlayer);
            else if(player.isShiftKeyDown())CompanionCommands.assignHome(serverPlayer,pos);
            else BowlService.contentsMessage(serverPlayer,bowl);
        }
        return InteractionResult.SUCCESS;
    }
}
