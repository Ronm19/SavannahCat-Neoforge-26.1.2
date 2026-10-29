package net.ronm19.savannahcat.companion.utility;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.ronm19.savannahcat.companion.CompanionAction;
import net.ronm19.savannahcat.companion.CompanionCommand;
import net.ronm19.savannahcat.companion.CompanionData;
import net.ronm19.savannahcat.config.SavannahConfig;
import net.ronm19.savannahcat.entity.SavannahCatEntity;

/** Server-owned command authorization and capped, loaded-entity searches. */
public final class CompanionCommands {
    public static final String[] WHISTLE_MODES = {"follow", "stay", "hunt", "harvest", "recall"};
    private CompanionCommands() {}

    public static List<SavannahCatEntity> ownedNearby(ServerPlayer player, double radius, int limit) {
        var result = new ArrayList<SavannahCatEntity>();
        player.level().getEntities(EntityTypeTest.forClass(SavannahCatEntity.class), player.getBoundingBox().inflate(radius),
                cat -> cat.isAlive() && cat.isOwnedBy(player) && cat.distanceToSqr(player) <= radius * radius,
                result, Math.max(1, Math.min(64, limit)));
        result.sort(Comparator.comparingDouble(cat -> cat.distanceToSqr(player)));
        result.forEach(LoadedCompanions::track);
        return result;
    }

    /** A visible cat under the crosshair receives an individual order; otherwise the capped nearby group does. */
    public static List<SavannahCatEntity> whistleTargets(ServerPlayer player) {
        double radius = SavannahConfig.GROUP_RADIUS.get();
        int limit = SavannahConfig.GROUP_COMMANDS.get() ? SavannahConfig.MAX_NEARBY_CATS.get() : 1;
        var group = ownedNearby(player, radius, SavannahConfig.MAX_SCANNED_ENTITIES.get());
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(Math.min(32.0D, radius)));
        SavannahCatEntity sighted = null;
        double nearest = Double.MAX_VALUE;
        // Use the same bounded candidate set; blocks between owner and cat reject individual selection.
        for (var cat : group) {
            var hit = cat.getBoundingBox().inflate(0.3D).clip(eye, end);
            if (hit.isPresent() && player.hasLineOfSight(cat)) {
                double distance = hit.get().distanceToSqr(eye);
                if (distance < nearest) { nearest = distance; sighted = cat; }
            }
        }
        return sighted == null ? group.subList(0, Math.min(group.size(), Math.max(1, limit))) : List.of(sighted);
    }

    public static boolean apply(ServerPlayer owner, SavannahCatEntity cat, int whistleMode) {
        if (whistleMode<0 || whistleMode>=WHISTLE_MODES.length || !owner.isAlive() || owner.isSpectator()
                || !cat.isAlive() || !cat.isOwnedBy(owner) || cat.isDowned() || cat.isRecovering()) return false;
        if(cat.isBaby() && (whistleMode==2 || whistleMode==3))return false;
        if (whistleMode == 4) {
            if(cat.level()==owner.level() && cat.distanceToSqr(owner)>Math.pow(SavannahConfig.GROUP_RADIUS.get(),2))return false;
            cat.getCompanionBrain().wakeFromCommand();
            return cat.getCompanionBrain().recallToOwner(owner);
        }
        if (cat.level() != owner.level() || cat.distanceToSqr(owner) > Math.pow(SavannahConfig.GROUP_RADIUS.get(), 2)) return false;
        if (whistleMode == 2 && !SavannahConfig.HUNTING.get()) return false;
        if (whistleMode == 3 && !SavannahConfig.HARVESTING.get()) return false;
        // Rejected orders must not discard the food-paid breeding state.
        net.ronm19.savannahcat.companion.family.BreedingService.cancelLove(cat);
        cat.setCommand(CompanionCommand.byId(Math.floorMod(whistleMode, 4)));
        cat.setAction(CompanionAction.ACKNOWLEDGE, 12);
        cat.recordExperience(CompanionData.Experience.INTERACTION);
        return true;
    }

    public static int issue(ServerPlayer owner, int mode) {
        mode=Math.floorMod(mode,WHISTLE_MODES.length);
        int successes = 0;
        var accepted = new ArrayList<SavannahCatEntity>();
        var targets=whistleTargets(owner);
        if(mode==4) {
            // Keep an explicitly sighted single cat selected; otherwise use only the event-maintained loaded index.
            var eye=owner.getEyePosition();var end=eye.add(owner.getLookAngle().scale(32));
            boolean singleSighted=targets.size()==1 && owner.hasLineOfSight(targets.getFirst())
                    && targets.getFirst().getBoundingBox().inflate(.3).clip(eye,end).isPresent();
            if(!singleSighted)targets=LoadedCompanions.forOwner(owner);
        }
        for (var cat : targets) if (apply(owner, cat, mode)) { successes++; accepted.add(cat); }
        int rejected=targets.size()-successes;
        Component feedback = targets.isEmpty()
                ? Component.translatable(mode==4?"message.savannahcat.recall_none":"message.savannahcat.no_owned_cat")
                : Component.translatable("message.savannahcat.whistle_result",
                    Component.translatable("command.savannahcat." + WHISTLE_MODES[mode]), successes,rejected);
        if (mode == 2 && !accepted.isEmpty()) feedback = Component.empty().append(feedback).append(" · ").append(huntFeedback(accepted));
        owner.sendOverlayMessage(feedback);
        if (successes > 0) owner.level().playSound(null, owner.blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(),
                SoundSource.PLAYERS, 0.5F, 1.25F);
        return successes;
    }

    /** Feedback reuses the actual bounded search state; it performs no second entity or block scan. */
    public static Component huntFeedback(List<SavannahCatEntity> cats) {
        if (cats.size() == 1) {
            var brain = cats.getFirst().getCompanionBrain();
            var status = brain.getHuntStatus();
            return status == net.ronm19.savannahcat.entity.ai.SavannahCatBrain.HuntStatus.COOLDOWN
                    ? Component.translatable(status.translationKey(), (brain.getHuntCooldownTicks() + 19) / 20)
                    : Component.translatable(status.translationKey());
        }
        int pursuing = 0, searching = 0, waiting = 0;
        for (var cat : cats) switch (cat.getCompanionBrain().getHuntStatus()) {
            case PURSUING -> pursuing++;
            case SEARCHING, NO_PREY -> searching++;
            default -> waiting++;
        }
        return Component.translatable("message.savannahcat.hunt.group_result", pursuing, searching, waiting);
    }

    /** Furniture home assignment is explicit and affects only the nearest cat actually owned by this player. */
    public static boolean assignHome(ServerPlayer owner, BlockPos pos) {
        if (!SavannahConfig.HOME.get() || !SavannahConfig.FURNITURE.get() || !owner.level().hasChunkAt(pos) || !owner.level().mayInteract(owner, pos)) return false;
        var bowl=owner.level().getBlockEntity(pos) instanceof net.ronm19.savannahcat.block.FoodWaterBowlBlockEntity value?value:null;
        if(bowl!=null && !bowl.mayUse(owner.getUUID())){BowlService.ownerMessage(owner);return false;}
        var candidates = ownedNearby(owner, 8.0D, SavannahConfig.MAX_NEARBY_CATS.get());
        if (candidates.isEmpty()) {
            owner.sendOverlayMessage(Component.translatable("message.savannahcat.no_owned_cat"));
            return false;
        }
        var cat = candidates.getFirst();
        if(bowl!=null)bowl.claim(owner.getUUID());
        cat.getCompanionData().setHome(GlobalPos.of(owner.level().dimension(), pos.immutable()));
        cat.recordExperience(CompanionData.Experience.INTERACTION);
        owner.sendOverlayMessage(Component.translatable("message.savannahcat.home_set", cat.getName()));
        return true;
    }
}
