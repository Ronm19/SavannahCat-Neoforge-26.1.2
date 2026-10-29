package net.ronm19.savannahcat.companion.utility;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.ronm19.savannahcat.entity.ai.SafetyNavigator;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.EventHooks;
import net.ronm19.savannahcat.companion.CompanionAction;
import net.ronm19.savannahcat.companion.CompanionData;
import net.ronm19.savannahcat.companion.CompanionUtilityData;
import net.ronm19.savannahcat.config.SavannahConfig;
import net.ronm19.savannahcat.entity.SavannahCatEntity;

/** Conservative renewable-only harvesting. Every mutation requires the online owner's protection context. */
public final class HarvestService {
    private HarvestService() {}

    public static boolean isMatureAllowed(BlockState state) {
        if (!SavannahConfig.HARVEST_BLOCKS.get().contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())) return false;
        if (state.is(Blocks.SWEET_BERRY_BUSH)) return state.getValue(SweetBerryBushBlock.AGE) == 3;
        return (state.is(Blocks.WHEAT) || state.is(Blocks.CARROTS) || state.is(Blocks.POTATOES) || state.is(Blocks.BEETROOTS))
                && state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
    }

    public static boolean isVegetationAllowed(BlockState state) {
        if (!SavannahConfig.HARVEST_VEGETATION.get().contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())
                || state.getBlock() instanceof CropBlock || state.getBlock() instanceof PitcherCropBlock
                || state.is(BlockTags.CROPS) || state.is(Blocks.SWEET_BERRY_BUSH)
                || SafetyNavigator.hazardous(state) || !state.getFluidState().isEmpty()) return false;
        return state.getBlock() instanceof TallGrassBlock || state.getBlock() instanceof DoublePlantBlock
                || state.getBlock() instanceof FlowerBlock || state.getBlock() instanceof SaplingBlock;
    }
    public static boolean isHarvestable(BlockState state) { return isVegetationAllowed(state) || isMatureAllowed(state); }
    public static BlockPos canonical(ServerLevel level, BlockPos pos) {
        var state=level.getBlockState(pos);
        return state.getBlock() instanceof DoublePlantBlock && state.getValue(DoublePlantBlock.HALF)==DoubleBlockHalf.UPPER
                ? pos.below() : pos;
    }
    public static String key(BlockPos pos) { return "b:"+pos.asLong(); }

    public static boolean ownerMayCollect(SavannahCatEntity cat, ServerPlayer owner, BlockPos pos) {
        return cat.level() instanceof ServerLevel level && owner.level()==level && cat.isOwnedBy(owner)
                && level.getServer().getPlayerList().getPlayer(owner.getUUID())==owner && !owner.isSpectator()
                && level.hasChunkAt(pos) && level.mayInteract(owner,pos) && SavannahConfig.HARVESTING.get();
    }
    public static boolean ownerMayHarvest(SavannahCatEntity cat, ServerPlayer owner, BlockPos pos) {
        if (!(cat.level() instanceof ServerLevel level) || owner.level() != level || !cat.isOwnedBy(owner)
                || level.getServer().getPlayerList().getPlayer(owner.getUUID()) != owner
                || owner.isSpectator() || !owner.getAbilities().mayBuild || !level.hasChunkAt(pos)
                || !level.mayInteract(owner, pos) || owner.blockActionRestricted(level, pos, owner.gameMode.getGameModeForPlayer())
                || !SavannahConfig.HARVESTING.get() || !level.getGameRules().get(GameRules.MOB_GRIEFING)) return false;
        return EventHooks.canEntityGrief(level, cat);
    }

    public static boolean canFit(SimpleContainer carry, List<ItemStack> incoming) {
        int active=Math.min(carry.getContainerSize(),SavannahConfig.CARRY_SLOTS.get());
        for(int i=active;i<carry.getContainerSize();i++)if(!carry.getItem(i).isEmpty())return false;
        var simulated = CompanionUtilityData.createCarry(active);
        for (int i = 0; i < active; i++) simulated.setItem(i, carry.getItem(i).copy());
        for (var stack : incoming) if (!simulated.addItem(stack.copy()).isEmpty()) return false;
        return true;
    }

    public static boolean harvest(SavannahCatEntity cat, ServerPlayer owner, BlockPos pos) {
        if (!(cat.level() instanceof ServerLevel level) || !cat.isAlive() || cat.isBaby() || cat.isDowned() || cat.isRecovering()
                || !level.hasChunkAt(pos) || !ownerMayHarvest(cat, owner, pos)) return false;
        cat.getCompanionData().getUtilityData().harvest.capacityBlocked=false;
        pos=canonical(level,pos);
        if(!HarvestClaims.reserve(level,key(pos),cat.getUUID()))return false;
        try {
            if(isVegetationAllowed(level.getBlockState(pos)))return clearVegetation(cat,owner,pos);
            return harvestCrop(cat,owner,pos);
        } finally { HarvestClaims.release(level,key(pos),cat.getUUID()); }
    }

    private static boolean harvestCrop(SavannahCatEntity cat, ServerPlayer owner, BlockPos pos) {
        if (!(cat.level() instanceof ServerLevel level) || cat.isDowned() || cat.isRecovering()
                || cat.distanceToSqr(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D) > 9.0D
                || !ownerMayHarvest(cat, owner, pos)) return false;
        var data = cat.getCompanionData().getUtilityData();
        long now = level.getGameTime();
        if (now < data.nextHarvestAt) return false;
        data.nextHarvestAt = now + SavannahConfig.HARVEST_ACTION_INTERVAL.get();
        BlockState state = level.getBlockState(pos);
        if (!isMatureAllowed(state)) return false;
        var fake = FakePlayerFactory.get(level, owner.getGameProfile());
        fake.setPos(cat.position());
        fake.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        fake.setGameMode(owner.gameMode.getGameModeForPlayer());
        if (CommonHooks.fireBlockBreak(level, owner.gameMode.getGameModeForPlayer(), fake, pos, state).isCanceled()) return false;
        if (!level.getBlockState(pos).equals(state)) return false;
        boolean berries = state.is(Blocks.SWEET_BERRY_BUSH);
        var drops = new ArrayList<ItemStack>();
        if (berries) {
            var params = new LootParams.Builder(level).withParameter(LootContextParams.BLOCK_STATE, state)
                    .withOptionalParameter(LootContextParams.INTERACTING_ENTITY, fake).create(LootContextParamSets.BLOCK_INTERACT);
            drops.addAll(level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.HARVEST_SWEET_BERRY_BUSH).getRandomItems(params));
        } else {
            drops.addAll(Block.getDrops(state, level, pos, null, fake, ItemStack.EMPTY));
        }
        BlockState replacement = berries ? state.setValue(SweetBerryBushBlock.AGE, 1) : Blocks.AIR.defaultBlockState();
        ItemStack seedFromCarry=ItemStack.EMPTY;
        if (!berries && SavannahConfig.REPLANT.get()) {
            Item seed = state.is(Blocks.WHEAT) ? Items.WHEAT_SEEDS : state.is(Blocks.CARROTS) ? Items.CARROT
                    : state.is(Blocks.POTATOES) ? Items.POTATO : Items.BEETROOT_SEEDS;
            ItemStack payment = drops.stream().filter(stack -> stack.is(seed) && !stack.isEmpty()).findFirst().orElse(null);
            if (payment == null) {
                seedFromCarry=data.getCarry().getItems().stream().filter(stack->stack.is(seed) && !stack.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
                if(seedFromCarry.isEmpty())return false;
            } else payment.shrink(1);
            replacement = ((CropBlock) state.getBlock()).getStateForAge(0);
        }
        drops.removeIf(ItemStack::isEmpty);
        var carry = data.getCarry();
        if (!canFit(carry, drops)) { data.harvest.capacityBlocked=true; return false; }
        // The place hook sees the actual replacement, as in normal item placement. A veto restores the old mature crop.
        var before = BlockSnapshot.create(level.dimension(), level, pos);
        if (!level.setBlock(pos, replacement, Block.UPDATE_ALL)) return false;
        if (!replacement.isAir() && EventHooks.onBlockPlace(fake, before, Direction.UP)) {
            level.setBlock(pos, state, Block.UPDATE_ALL);
            return false;
        }
        if(!seedFromCarry.isEmpty())seedFromCarry.shrink(1);
        for (var stack : drops) {
            ItemStack overflow = carry.addItem(stack);
            if (!overflow.isEmpty()) Block.popResource(level, pos, overflow); // Mod-altered inventory limits cannot erase items.
        }
        cat.setAction(CompanionAction.PLAY, 12);
        return true;
    }

    private static boolean clearVegetation(SavannahCatEntity cat,ServerPlayer owner,BlockPos bottom) {
        var level=(ServerLevel)cat.level();var data=cat.getCompanionData().getUtilityData();long now=level.getGameTime();
        if(now<data.nextHarvestAt || cat.distanceToSqr(Vec3.atCenterOf(bottom))>9)return false;
        data.nextHarvestAt=now+SavannahConfig.HARVEST_ACTION_INTERVAL.get();
        var state=level.getBlockState(bottom);if(!isVegetationAllowed(state))return false;
        boolean tall=state.getBlock() instanceof DoublePlantBlock;
        BlockPos top=bottom.above();if(tall&&!level.hasChunkAt(top))return false;
        BlockState upper=tall?level.getBlockState(top):Blocks.AIR.defaultBlockState();
        if(tall && (!level.hasChunkAt(top)||!upper.is(state.getBlock())
                || upper.getValue(DoublePlantBlock.HALF)!=DoubleBlockHalf.UPPER || !ownerMayHarvest(cat,owner,top)))return false;
        var fake=FakePlayerFactory.get(level,owner.getGameProfile());fake.setPos(cat.position());
        fake.setGameMode(owner.gameMode.getGameModeForPlayer());fake.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        if(CommonHooks.fireBlockBreak(level,owner.gameMode.getGameModeForPlayer(),fake,bottom,state).isCanceled()
                || tall && CommonHooks.fireBlockBreak(level,owner.gameMode.getGameModeForPlayer(),fake,top,upper).isCanceled())return false;
        if(!level.getBlockState(bottom).equals(state)||tall&&!level.getBlockState(top).equals(upper))return false;
        // Evaluate the canonical lower half's actual loot exactly once. Never let neighbor removal spawn a second copy.
        var drops=Block.getDrops(state,level,bottom,null,fake,ItemStack.EMPTY);
        if(!canFit(data.getCarry(),drops)){data.harvest.capacityBlocked=true;return false;}
        int flags=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS;
        if(tall&&!level.setBlock(top,Blocks.AIR.defaultBlockState(),flags))return false;
        if(!level.setBlock(bottom,Blocks.AIR.defaultBlockState(),flags)) {
            if(tall)level.setBlock(top,upper,flags);return false;
        }
        level.updateNeighborsAt(bottom,state.getBlock());
        if(tall)level.updateNeighborsAt(top,upper.getBlock());
        level.levelEvent(2001,bottom,Block.getId(state));
        for(var stack:drops){var overflow=data.getCarry().addItem(stack);if(!overflow.isEmpty())Block.popResource(level,bottom,overflow);}
        data.harvest.actions++;cat.setAction(CompanionAction.PLAY,6);
        return true;
    }

}
