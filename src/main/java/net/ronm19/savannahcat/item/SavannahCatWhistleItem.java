package net.ronm19.savannahcat.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.ronm19.savannahcat.registry.ModBlocks;
import net.ronm19.savannahcat.companion.utility.CompanionCommands;
import net.ronm19.savannahcat.config.SavannahConfig;

/** Selection is a standard saved/synchronized item component, never a client-authored command packet. */
public final class SavannahCatWhistleItem extends Item {
    public SavannahCatWhistleItem(Properties properties) { super(properties.stacksTo(1)); }

    public static int selectedMode(ItemStack stack) {
        return Math.floorMod(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getIntOr("SavannahWhistleMode", 0), CompanionCommands.WHISTLE_MODES.length);
    }

    public static void cycle(ItemStack stack) {
        int mode = (selectedMode(stack) + 1) % CompanionCommands.WHISTLE_MODES.length;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt("SavannahWhistleMode", mode));
    }

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player.isSpectator()) return InteractionResult.PASS;
        if (player instanceof ServerPlayer owner) {
            var held = player.getItemInHand(hand);
            if (player.isShiftKeyDown()) {
                cycle(held);
                owner.sendOverlayMessage(Component.translatable("message.savannahcat.whistle_mode",
                        Component.translatable("command.savannahcat." + CompanionCommands.WHISTLE_MODES[selectedMode(held)])));
            } else if (!owner.getCooldowns().isOnCooldown(held)) {
                CompanionCommands.issue(owner, selectedMode(held));
                owner.getCooldowns().addCooldown(held, SavannahConfig.COMMAND_COOLDOWN.get());
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer owner && owner.isShiftKeyDown()
                && (context.getLevel().getBlockState(context.getClickedPos()).is(ModBlocks.SCRATCHING_POST.get())
                    || context.getLevel().getBlockState(context.getClickedPos()).is(ModBlocks.FOOD_WATER_BOWL.get()))) {
            CompanionCommands.assignHome(owner, context.getClickedPos());
            return InteractionResult.SUCCESS;
        }
        return context.getPlayer() == null ? InteractionResult.PASS
                : use(context.getLevel(), context.getPlayer(), context.getHand());
    }
}
