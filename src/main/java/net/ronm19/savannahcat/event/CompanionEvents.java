package net.ronm19.savannahcat.event;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.projectile.FishingHook;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.ronm19.savannahcat.SavannahCatMod;
import net.ronm19.savannahcat.companion.CompanionAction;
import net.ronm19.savannahcat.companion.utility.CompanionCommands;
import net.ronm19.savannahcat.companion.utility.FishingAssistance;
import net.ronm19.savannahcat.companion.utility.PhantomDeterrenceGoal;
import net.ronm19.savannahcat.companion.utility.LoadedCompanions;
import net.ronm19.savannahcat.config.SavannahConfig;
import net.ronm19.savannahcat.entity.SavannahCatEntity;

@EventBusSubscriber(modid = SavannahCatMod.MOD_ID)
public final class CompanionEvents {
    // Runtime identity only: a persisted marker would incorrectly skip installation after entity reload.
    private static final Map<Mob, Boolean> INSTALLED = Collections.synchronizedMap(new WeakHashMap<>());
    private CompanionEvents() {}

    @SubscribeEvent public static void entityJoined(EntityJoinLevelEvent event) {
        if(!event.getLevel().isClientSide() && event.getEntity() instanceof SavannahCatEntity cat)LoadedCompanions.track(cat);
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)
                || (!(mob instanceof Creeper) && !(mob instanceof Phantom)) || INSTALLED.putIfAbsent(mob, true) != null) return;
        // Installing goals here performs no world/chunk access during entity loading.
        if (mob instanceof Creeper creeper) {
            creeper.goalSelector.addGoal(3, new AvoidEntityGoal<>(creeper, SavannahCatEntity.class, 6.0F, 1.0D, 1.2D,
                    cat -> SavannahConfig.DETERRENCE.get() && cat.isAlive() && !((SavannahCatEntity) cat).isDowned()));
        } else if (mob instanceof Phantom phantom) {
            phantom.goalSelector.addGoal(0, new PhantomDeterrenceGoal(phantom));
        }
    }
    @SubscribeEvent public static void entityLeft(EntityLeaveLevelEvent event) {
        if(!event.getLevel().isClientSide() && event.getEntity() instanceof SavannahCatEntity cat) {
            var sleep = cat.getCompanionBrain().getSleep();
            // Tracking can end before chunk serialization; preserve unrelated persistent manual Sitting.
            if (sleep.hasActivity() || cat.getAction() == CompanionAction.SLEEP) sleep.wake(event.getLevel().getGameTime());
            net.ronm19.savannahcat.companion.utility.HarvestCoordinator.interrupt(cat);
            LoadedCompanions.forget(cat);
        }
    }
    @SubscribeEvent public static void serverStopped(ServerStoppedEvent event) { LoadedCompanions.clear();INSTALLED.clear();net.ronm19.savannahcat.companion.bedtime.BedtimeReservations.clear();net.ronm19.savannahcat.companion.utility.HarvestClaims.clear(); }

    @SubscribeEvent public static void itemFished(ItemFishedEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer owner) || !SavannahConfig.FISHING.get()) return;
        var cats = CompanionCommands.ownedNearby(owner, SavannahConfig.FISHING_RADIUS.get(), SavannahConfig.MAX_SCANNED_ENTITIES.get());
        double bonus = FishingAssistance.strongestBonus(owner, cats);
        if (bonus <= 0.0D) return;
        event.damageRodBy(FishingAssistance.rodDamage(event.getRodDamage(), bonus, owner.getRandom().nextDouble()));
        // A quiet attention response is occasional and uses the same single strongest benefit window.
        if (owner.getRandom().nextInt(8) == 0 && !cats.isEmpty()) cats.getFirst().setAction(CompanionAction.INVESTIGATE, 20);
    }

    @SubscribeEvent public static void fishingWait(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof FishingHook hook) || hook.level().isClientSide()
                || !(hook.getPlayerOwner() instanceof ServerPlayer owner) || !SavannahConfig.FISHING.get()) return;
        var state = hook.getPersistentData();
        if (hook.timeUntilLured <= 0) {
            state.putBoolean("SavannahWaitApplied", false);
            return;
        }
        if (state.getBooleanOr("SavannahWaitApplied", false)) return;
        // At most one bounded cat scan every 20 hook ticks; the timer itself is changed only once per wait cycle.
        if (!state.contains("SavannahFishingCacheTick") || hook.tickCount - state.getIntOr("SavannahFishingCacheTick", 0) >= 20) {
            var cats = CompanionCommands.ownedNearby(owner, SavannahConfig.FISHING_RADIUS.get(), SavannahConfig.MAX_SCANNED_ENTITIES.get());
            state.putDouble("SavannahFishingBonus", FishingAssistance.strongestBonus(owner, cats));
            state.putInt("SavannahFishingCacheTick", hook.tickCount);
        }
        double bonus = state.getDoubleOr("SavannahFishingBonus", 0.0D);
        hook.timeUntilLured = FishingAssistance.biteWait(hook.timeUntilLured, bonus);
        state.putBoolean("SavannahWaitApplied", true);
    }
}
