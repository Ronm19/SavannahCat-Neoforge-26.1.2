package net.ronm19.savannahcat.companion;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.ronm19.savannahcat.config.SavannahConfig;
import net.ronm19.savannahcat.entity.SavannahCatEntity;
import net.ronm19.savannahcat.companion.family.BreedingService;

/** Single owner-authoritative interaction path, including offhand precedence over empty-hand commands. */
public final class CompanionInteractions {
    private CompanionInteractions() {}
    public static InteractionResult interact(SavannahCatEntity cat,Player player,InteractionHand hand) {
        if(player.isSpectator() || !cat.isAlive() || cat.isRemoved())return InteractionResult.PASS;
        ItemStack held=player.getItemInHand(hand);
        if(cat.isDowned()) {
            if(cat.isOwnedBy(player) && player.isShiftKeyDown() && SavannahConfig.isFood(held,false)) {
                if(!cat.level().isClientSide() && cat.reviveCompanion()) {
                    consume(player,hand,held);particles(cat,true);
                    player.sendOverlayMessage(Component.translatable("message.savannahcat.revived"));
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }
        // An injured owned companion always heals first, including crouch-feeding.
        if(cat.isOwnedBy(player) && SavannahConfig.HEALING.get() && SavannahConfig.isFood(held,false)
                && cat.getHealth()<cat.getMaxHealth()) {
            if(!cat.level().isClientSide()) {
                consume(player,hand,held);cat.heal(SavannahConfig.HEAL_AMOUNT.get().floatValue());
                cat.recordExperience(CompanionData.Experience.FEED);cat.setAction(CompanionAction.HEAL,20);
                particles(cat,true);cat.getCompanionAudio().eating();
                player.sendOverlayMessage(Component.translatable("message.savannahcat.health",Math.round(cat.getHealth()*10)/10.0,Math.round(cat.getMaxHealth()*10)/10.0));
            }
            return InteractionResult.SUCCESS;
        }
        if(player.isShiftKeyDown() && BreedingService.isBreedingFood(held)) {
            if(!cat.level().isClientSide()) {
                var result=BreedingService.feed(cat,player,held);
                if(result.accepted()){consume(player,hand,held);cat.getCompanionAudio().eating();}
                if(result!=BreedingService.FeedResult.NOT_APPLICABLE)player.sendOverlayMessage(Component.translatable(result.messageKey()));
            }
            return InteractionResult.SUCCESS;
        }
        if(!cat.isTamed() && SavannahConfig.TAMING.get() && SavannahConfig.isFood(held,true)) {
            if(!cat.level().isClientSide()) {
                consume(player,hand,held);
                cat.getCompanionAudio().eating();
                boolean success=cat.getRandom().nextInt(SavannahConfig.TAMING_CHANCE.get())==0;
                if(success){cat.tameFor(player);cat.setAction(CompanionAction.TAME,30);cat.getCompanionAudio().tamed();player.sendOverlayMessage(Component.translatable("message.savannahcat.tamed"));}
                particles(cat,success);
            }
            return InteractionResult.SUCCESS;
        }
        if(!cat.isOwnedBy(player))return InteractionResult.PASS;
        if(SavannahConfig.HEALING.get() && SavannahConfig.isFood(held,false)) {
            if(!cat.level().isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.savannahcat.health",Math.round(cat.getHealth()*10)/10.0,Math.round(cat.getMaxHealth()*10)/10.0));
            }
            return InteractionResult.SUCCESS;
        }
        DyeColor dye=DyeColor.getColor(held);
        if(dye!=null) {
            if(!cat.level().isClientSide() && dye!=cat.getCollarColor()) {cat.setCollarColor(dye);consume(player,hand,held);cat.setAction(CompanionAction.ACKNOWLEDGE,16);}
            return InteractionResult.SUCCESS;
        }
        if(held.is(Items.COMPASS) && SavannahConfig.HOME.get()) {
            if(!cat.level().isClientSide()) {
                if(player.isShiftKeyDown()) {
                    cat.getCompanionData().setHome(GlobalPos.of(cat.level().dimension(),cat.blockPosition()));
                    player.sendOverlayMessage(Component.translatable("message.savannahcat.home_set",cat.getName()));
                } else {
                    boolean returning=cat.getCompanionBrain().returnHome();
                    player.sendOverlayMessage(Component.translatable(returning?"message.savannahcat.home_return":"message.savannahcat.home_missing"));
                }
            }
            return InteractionResult.SUCCESS;
        }
        if(held.is(Items.STRING) && SavannahConfig.AMBIENT.get()) {
            if(!cat.level().isClientSide() && cat.getCompanionBrain().isSafeToRest()) {
                cat.recordExperience(CompanionData.Experience.PLAY);cat.setAction(CompanionAction.PLAY,40);
                cat.getCompanionAudio().playing();
            }
            return InteractionResult.SUCCESS;
        }
        if(held.isEmpty()) {
            ItemStack other=player.getItemInHand(hand==InteractionHand.MAIN_HAND?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND);
            if(!other.isEmpty() && (SavannahConfig.isFood(other,false)||DyeColor.getColor(other)!=null||other.is(Items.COMPASS)||other.is(Items.STRING)))return InteractionResult.PASS;
            if(!cat.level().isClientSide()) {
                cat.setStayCommand(!cat.isStayCommand());cat.recordExperience(CompanionData.Experience.INTERACTION);
                cat.setAction(CompanionAction.ACKNOWLEDGE,16);
                player.sendOverlayMessage(Component.translatable("message.savannahcat."+cat.getCommand().key()));
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
    private static void consume(Player player,InteractionHand hand,ItemStack stack) {
        if(!player.getAbilities().instabuild){stack.shrink(1);player.setItemInHand(hand,stack);}
    }
    private static void particles(SavannahCatEntity cat,boolean heart) {
        if(cat.level() instanceof ServerLevel level)level.sendParticles(heart?ParticleTypes.HEART:ParticleTypes.SMOKE,
                cat.getX(),cat.getY()+cat.getBbHeight()*.7,cat.getZ(),7,.3,.2,.3,.02);
    }
}
