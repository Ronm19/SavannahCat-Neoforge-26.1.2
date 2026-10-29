package net.ronm19.savannahcat.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.ronm19.savannahcat.companion.utility.CompanionCommands;

/** Resource-built scratching post; activities are coordinated by the companion brain. */
public final class CompanionFurnitureBlock extends Block {
    public static final MapCodec<CompanionFurnitureBlock> CODEC = simpleCodec(CompanionFurnitureBlock::new);
    public CompanionFurnitureBlock(BlockBehaviour.Properties properties) { super(properties); }
    @Override public MapCodec<CompanionFurnitureBlock> codec() { return CODEC; }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.or(Block.box(1,0,1,15,2,15),Block.box(6,2,6,10,15,10),Block.box(5,15,5,11,16,11));
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (player instanceof ServerPlayer owner) CompanionCommands.assignHome(owner, pos);
        return InteractionResult.SUCCESS;
    }
}
