package net.ronm19.savannahcat.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.DamageTypeTags;
import net.ronm19.savannahcat.companion.*;
import net.ronm19.savannahcat.companion.family.FamilyData;
import net.ronm19.savannahcat.companion.family.FamilyLifecycle;
import net.ronm19.savannahcat.companion.family.BreedingService;
import net.ronm19.savannahcat.companion.audio.CompanionAudio;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.sounds.SoundEvent;
import net.ronm19.savannahcat.config.SavannahConfig;
import net.ronm19.savannahcat.entity.ai.CompanionBehaviorGoal;
import net.ronm19.savannahcat.entity.ai.CompanionRelations;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;
import net.ronm19.savannahcat.entity.ai.SavannahCatBrain;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.ronm19.savannahcat.util.SusanNameHelper;
import org.jspecify.annotations.Nullable;

/**
 * Savannah Cat companion. Family state and ownership share one persistent entity identity.
 * Explicit breeding is coordinated by the companion Brain rather than vanilla Animal goals.
 */
public class SavannahCatEntity extends PathfinderMob implements GeoEntity {
    private static final String VARIANT_SAVE_KEY = "Variant";
    private static final String SITTING_SAVE_KEY = "Sitting";
    private static final String OWNER_SAVE_KEY = "OwnerUUID";
    private static final String COMMAND_SAVE_KEY = "CompanionCommand";
    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(SavannahCatEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_SITTING =
            SynchedEntityData.defineId(SavannahCatEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_TAMED =
            SynchedEntityData.defineId(SavannahCatEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_COMMAND =
            SynchedEntityData.defineId(SavannahCatEntity.class, EntityDataSerializers.INT);
    /** -1 = tilt left, 0 = no effect, +1 = tilt right. Synced for multiplayer. */
    private static final EntityDataAccessor<Integer> DATA_SUSAN_TILT =
            SynchedEntityData.defineId(SavannahCatEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<String> DATA_OWNER = SynchedEntityData.defineId(SavannahCatEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_COLLAR = SynchedEntityData.defineId(SavannahCatEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BOND = SynchedEntityData.defineId(SavannahCatEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_DOWNED = SynchedEntityData.defineId(SavannahCatEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_RECOVERING = SynchedEntityData.defineId(SavannahCatEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(SavannahCatEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTION_SEQUENCE = SynchedEntityData.defineId(SavannahCatEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_BABY = SynchedEntityData.defineId(SavannahCatEntity.class, EntityDataSerializers.BOOLEAN);
    public static final DataTicket<Boolean> BABY_RENDER_DATA = DataTicket.create("savannahcat:baby", Boolean.class);
    public static final DataTicket<Boolean> BABY_RUN_RENDER_DATA = DataTicket.create("savannahcat:baby_run", Boolean.class);
    public static final DataTicket<Integer> ACTION_RENDER_DATA = DataTicket.create("savannahcat:action", Integer.class);
    public static final DataTicket<Integer> ACTION_SEQUENCE_RENDER_DATA = DataTicket.create("savannahcat:action_sequence", Integer.class);
    private final CompanionData companionData = new CompanionData();
    private final CompanionLifecycle lifecycle = new CompanionLifecycle(this);
    private final FamilyData familyData = new FamilyData();
    private final FamilyLifecycle familyLifecycle = new FamilyLifecycle(this);
    private final CompanionAudio companionAudio = new CompanionAudio(this);
    private int actionTicks;
    private long peacefulWakeUntil;
    private int renderedActionSequence=-1;
    private boolean renderedBaby;

    /** Renderer snapshot used by the controller instead of reading a mutable entity during rendering. */
    public static final DataTicket<Boolean> SITTING_RENDER_DATA = DataTicket.create("savannahcat:sitting", Boolean.class);
    public static final DataTicket<Integer> SUSAN_TILT_RENDER_DATA = DataTicket.create("savannahcat:susan_tilt", Integer.class);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.savannah_cat.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.savannah_cat.walk");
    private static final RawAnimation BABY_RUN = RawAnimation.begin().thenLoop("animation.savannah_cat.baby_run");
    private static final RawAnimation BABY_CURIOUS = RawAnimation.begin().thenLoop("animation.savannah_cat.baby_curious");
    private static final RawAnimation SIT = RawAnimation.begin().thenPlayAndHold("animation.savannah_cat.sit");
    private static final RawAnimation SUSAN_LEFT = RawAnimation.begin().thenPlay("animation.savannah_cat.susan_tilt_left");
    private static final RawAnimation SUSAN_RIGHT = RawAnimation.begin().thenPlay("animation.savannah_cat.susan_tilt_right");
    private static final int SUSAN_TILT_DURATION_TICKS = 36; // 1.8 seconds, matching GeckoLib keyframes
    private static final int SUSAN_MIN_COOLDOWN_TICKS = 400; // 20 seconds
    private static final int SUSAN_EXTRA_COOLDOWN_TICKS = 401; // up to 40 seconds
    private static final double SUSAN_GREETING_DISTANCE = 8.0D;
    private static final RawAnimation SIT_EXIT_TO_IDLE = RawAnimation.begin()
            .thenPlay("animation.savannah_cat.sit_exit").thenLoop("animation.savannah_cat.idle");
    private static final RawAnimation SLEEP_EXIT_TO_IDLE = RawAnimation.begin()
            .thenPlay("animation.savannah_cat.wake").thenLoop("animation.savannah_cat.idle");
    private static final java.util.EnumMap<CompanionAction,RawAnimation> ACTION_ANIMATIONS = new java.util.EnumMap<>(CompanionAction.class);
    static {
        for(var action:CompanionAction.values()) {
            boolean loop=switch(action){case STALK,RETREAT,GROOM,SCRATCH,PLAY,INVESTIGATE,SOCIAL->true;default->false;};
            ACTION_ANIMATIONS.put(action,action==CompanionAction.SLEEP
                    ? RawAnimation.begin().thenPlay("animation.savannah_cat.sleep_enter").thenLoop(action.animation())
                    : loop?RawAnimation.begin().thenLoop(action.animation()):RawAnimation.begin().thenPlayAndHold(action.animation()));
        }
    }
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private int susanTiltTicksRemaining;
    private int susanCooldownTicks;
    private boolean nextSusanTiltRight;
    private UUID ownerUuid;
    private final SavannahCatBrain companionBrain = new SavannahCatBrain(this);

    public SavannahCatEntity(EntityType<? extends SavannahCatEntity> type, Level level) {
        super(type, level);
        setPathfindingMalus(PathType.FIRE, -1.0F);
        setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, 16.0F);
        setPathfindingMalus(PathType.LAVA, -1.0F);
        setPathfindingMalus(PathType.DAMAGING, -1.0F);
        setPathfindingMalus(PathType.DAMAGING_IN_NEIGHBOR, 8.0F);
        setPathfindingMalus(PathType.POWDER_SNOW, -1.0F);
        companionData.initialize(getRandom());
        susanCooldownTicks = nextSusanCooldown();
        nextSusanTiltRight = getRandom().nextBoolean();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.36D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this) {
            @Override public boolean canUse() { return !SavannahCatEntity.this.isDowned() && super.canUse(); }
        });
        goalSelector.addGoal(1, new CompanionBehaviorGoal(this));
        goalSelector.addGoal(2, new net.ronm19.savannahcat.entity.ai.HomeLifeLookGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT, SavannahCatVariant.BROWN_SPOTTED.getId());
        builder.define(DATA_SITTING, false);
        builder.define(DATA_TAMED, false);
        builder.define(DATA_COMMAND, CompanionCommand.FOLLOW.getId());
        builder.define(DATA_OWNER, "");
        builder.define(DATA_COLLAR, DyeColor.RED.getId());
        builder.define(DATA_BOND, 1);
        builder.define(DATA_DOWNED, false);
        builder.define(DATA_RECOVERING, false);
        builder.define(DATA_ACTION, 0);
        builder.define(DATA_ACTION_SEQUENCE, 0);
        builder.define(DATA_SUSAN_TILT, 0);
        builder.define(DATA_BABY, false);
    }

    public SavannahCatVariant getVariant() {
        return SavannahCatVariant.byId(entityData.get(DATA_VARIANT));
    }

    public void setVariant(SavannahCatVariant variant) {
        entityData.set(DATA_VARIANT, variant.getId());
    }

    public boolean isTamed() {
        return entityData.get(DATA_TAMED);
    }

    /** Server-side persistent ownership; this UUID cannot be replaced by other players feeding her. */
    public UUID getOwnerUUID() {
        if (ownerUuid != null) return ownerUuid;
        try { return UUID.fromString(entityData.get(DATA_OWNER)); } catch (IllegalArgumentException ignored) { return null; }
    }

    public boolean isOwnedBy(Player player) {
        return isTamed() && player.getUUID().equals(getOwnerUUID());
    }

    /** Returns the owner only when online and in the same dimension; no forced chunk loads. */
    public ServerPlayer getOwnerPlayer() {
        if (ownerUuid == null || !(level() instanceof ServerLevel serverLevel)) return null;
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(ownerUuid);
        return player != null && player.level() == level() && player.isAlive() && !player.isSpectator()
                ? player : null;
    }

    public CompanionData getCompanionData() { return companionData; }
    public FamilyData getFamilyData() { return familyData; }
    public FamilyLifecycle getFamilyLifecycle() { return familyLifecycle; }
    public CompanionAudio getCompanionAudio() { return companionAudio; }
    public EntityDimensions getFamilyDimensions(int stage) {
        return stage<4 ? EntityDimensions.scalable(.5F,1.15F).withEyeHeight(.78F)
                : getType().getDimensions();
    }
    @Override protected EntityDimensions getDefaultDimensions(Pose pose) {
        return getFamilyDimensions(isBaby()?0:4);
    }
    public void syncFamilyState() {
        if(level().isClientSide())return;
        boolean changed=entityData.get(DATA_BABY)!=familyData.isBaby();
        entityData.set(DATA_BABY,familyData.isBaby());
        if(changed){refreshDimensions();lifecycle.applyAttributes();}
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if(DATA_BABY.equals(key))refreshDimensions();
    }
    /** Birth-only UUID inheritance: an online Player is never needed and ownership cannot be replaced. */
    public void inheritOwner(UUID owner,DyeColor collar) {
        if(level().isClientSide() || owner==null || isTamed())return;
        ownerUuid=owner;entityData.set(DATA_OWNER,owner.toString());entityData.set(DATA_TAMED,true);
        setCollarColor(collar);setPersistenceRequired();lifecycle.applyAttributes();
        net.ronm19.savannahcat.companion.utility.LoadedCompanions.track(this);
    }
    public CompanionCommand getCommand() { return CompanionCommand.byId(entityData.get(DATA_COMMAND)); }
    public boolean isStayCommand() { return isTamed() && getCommand()==CompanionCommand.STAY; }
    public String getCompanionCommandName() { return getCommand().key(); }
    public void setStayCommand(boolean stay) { setCommand(stay?CompanionCommand.STAY:CompanionCommand.FOLLOW); }
    public void setCommand(CompanionCommand command) {
        if(!isTamed() || level().isClientSide() || isDowned()
                || isBaby() && (command==CompanionCommand.HUNT || command==CompanionCommand.HARVEST))return;
        BreedingService.cancelLove(this);
        companionBrain.cancelHomeReturn();
        boolean changed=getCommand()!=command;
        if(isCompanionSleeping())peacefulWakeUntil=level().getGameTime()+30;
        companionBrain.wakeFromCommand();
        // Repeated whistle orders still wake/cancel a home session, but keep a useful path and Stay anchor.
        if(!changed) {
            if(command==CompanionCommand.HUNT)companionBrain.requestIdleHuntSearch();
            if(command==CompanionCommand.STAY)setSitting(companionBrain.isSafeToRest());
            return;
        }
        entityData.set(DATA_COMMAND,command.getId());
        companionBrain.onCommandChanged();
        if(command==CompanionCommand.STAY)companionData.setStayAnchor(blockPosition());
        getNavigation().stop();setTarget(null);
        setSitting(command==CompanionCommand.STAY && companionBrain.isSafeToRest());
    }
    public int getBondLevel() { return level().isClientSide()?entityData.get(DATA_BOND):companionData.getBondLevel(); }
    public int getPersonality(CompanionData.Trait trait) { return SavannahConfig.PERSONALITY.get()?companionData.getPersonality(trait):50; }
    public boolean isDowned() { return level().isClientSide()?entityData.get(DATA_DOWNED):companionData.getDownedUntil()>0; }
    public boolean isRecovering() { return level().isClientSide()?entityData.get(DATA_RECOVERING):companionData.getRecoveryUntil()>level().getGameTime(); }
    public DyeColor getCollarColor() { return DyeColor.byId(entityData.get(DATA_COLLAR)); }
    public void setCollarColor(DyeColor color) { if(!level().isClientSide())entityData.set(DATA_COLLAR,color.getId()); }
    public float getPounceDamage() { return SavannahConfig.pounceDamage(getBondLevel()); }
    public void recordExperience(CompanionData.Experience reason) {
        if(isTamed() && !level().isClientSide() && companionData.experience(reason,level().getGameTime())) {
            lifecycle.applyAttributes();syncCompanionState();
        }
    }
    public boolean isProtectiveInstinctActive() { return companionData.getProtectiveUntil()>level().getGameTime(); }
    public boolean tryProtectiveInstinct(LivingEntity threat) { return lifecycle.protective(threat); }
    public CompanionAction getAction() { return CompanionAction.byId(entityData.get(DATA_ACTION)); }
    public boolean isCompanionSleeping() { return getAction()==CompanionAction.SLEEP && !isDowned(); }
    public int getActionSequence() { return entityData.get(DATA_ACTION_SEQUENCE); }
    public void setAction(CompanionAction action,int ticks) {
        if(level().isClientSide())return;
        if(isDowned() && action!=CompanionAction.DOWNED)return;
        if(getAction()==CompanionAction.SLEEP && action.interaction()) {
            companionBrain.wakeFromCommand();
            peacefulWakeUntil=level().getGameTime()+30;
        }
        if(action.interaction() && !action.critical())ticks+=(int)Math.max(0,peacefulWakeUntil-level().getGameTime());
        if(getAction().critical() && !action.critical() && actionTicks>0)return;
        if(getAction().interaction() && actionTicks>0 && !action.critical() && !action.interaction())return;
        if(getAction()!=action){entityData.set(DATA_ACTION,action.getId());entityData.set(DATA_ACTION_SEQUENCE,getActionSequence()+1);}
        actionTicks=Math.max(1,ticks);
        if(action!=CompanionAction.IDLE)stopSusanTilt();
    }
    public void clearAction() { entityData.set(DATA_ACTION,0);actionTicks=0; }
    public void syncCompanionState() {
        entityData.set(DATA_BOND,companionData.getBondLevel());entityData.set(DATA_DOWNED,isDowned());entityData.set(DATA_RECOVERING,isRecovering());
    }
    public boolean reviveCompanion() { return !level().isClientSide() && lifecycle.revive(); }
    public void finishDownedDeath(DamageSource source) { lifecycle.onFinalDeath();setHealth(0);super.die(source); }
    @Override public void die(DamageSource source) {
        if(!level().isClientSide()) {
            if(lifecycle.interceptDeath(source))return;
            lifecycle.onFinalDeath();
        }
        super.die(source);
    }
    public void tameFor(Player player) {
        if(level().isClientSide() || isTamed())return;
        ownerUuid=player.getUUID();entityData.set(DATA_OWNER,ownerUuid.toString());entityData.set(DATA_TAMED,true);
        setPersistenceRequired();setCommand(CompanionCommand.FOLLOW);setCollarColor(DyeColor.RED);lifecycle.applyAttributes();
        net.ronm19.savannahcat.companion.utility.LoadedCompanions.track(this);
    }
    @Override protected InteractionResult mobInteract(Player player,InteractionHand hand) {
        var result=CompanionInteractions.interact(this,player,hand);
        return result==InteractionResult.PASS?super.mobInteract(player,hand):result;
    }
    @Override public InteractionResult interact(Player player,InteractionHand hand,net.minecraft.world.phys.Vec3 location) {
        var item=player.getItemInHand(hand);
        // Mob handles eggs before mobInteract; protect juvenile and incapacitated parents at that boundary.
        if(item.getItem() instanceof net.minecraft.world.item.SpawnEggItem
                && net.minecraft.world.item.SpawnEggItem.spawnsEntity(item,getType())
                && (isBaby() || isDowned() || isRecovering() || player.isSpectator()))return InteractionResult.FAIL;
        return super.interact(player,hand,location);
    }
    /** Vanilla's deliberate egg-on-parent shortcut shares the same permanent family facts. */
    @Override protected void onOffspringSpawnedFromEgg(Player player,Mob offspring) {
        super.onOffspringSpawnedFromEgg(player,offspring);
        if(!(offspring instanceof SavannahCatEntity kitten) || level().isClientSide())return;
        kitten.getFamilyData().setParents(getUUID(),null,getUUID());
        kitten.setVariant(getVariant());
        kitten.getCompanionData().setHome(companionData.getHome());
        if(isTamed())kitten.inheritOwner(getOwnerUUID(),getCollarColor());
        kitten.syncFamilyState();
    }

    public boolean isSitting() {
        return entityData.get(DATA_SITTING);
    }

    /** Synced pose hook. Owner Stay is a separate command and can survive a safety interruption. */
    public void setSitting(boolean sitting) {
        entityData.set(DATA_SITTING, sitting);
        if (sitting) {
            stopSusanTilt();
            holdSittingPosition();
            // Clear a pending movement-control target as well as the navigation path.
            getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0.0D);
        }
    }

    /** Preserve downward gravity/contact while stopping the horizontal locomotion of a sitting cat. */
    public void holdSittingPosition() {
        getNavigation().stop();
        setXxa(0.0F);
        setYya(0.0F);
        setSpeed(0.0F);
        setJumping(false);
        double verticalSpeed = getDeltaMovement().y;
        // A zero-Y move can clear onGround in 26.1; keep a minimal downward contact probe.
        if (onGround() && verticalSpeed == 0.0D) {
            verticalSpeed = -0.001D;
        }
        setDeltaMovement(0.0D, verticalSpeed, 0.0D);
    }

    public boolean canRemainSitting() {
        return isAlive() && onGround() && !companionBrain.isThreatened()
                && hurtTime == 0 && getTarget() == null;
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            EntitySpawnReason reason, @Nullable SpawnGroupData groupData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, groupData);
        // Vanilla Mob adds a random follow-range bonus; this foundation promises exactly 32.
        var followRange = getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            followRange.removeModifier(RANDOM_SPAWN_BONUS_ID);
        }
        setVariant(SavannahCatVariant.random(getRandom()));
        return result;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString(VARIANT_SAVE_KEY, getVariant().getSerializedName());
        output.putBoolean(SITTING_SAVE_KEY, isSitting());
        output.putInt("CollarColor",getCollarColor().getId());
        companionData.save(output.child("CompanionData"));
        familyData.save(output.child("Family"));
        if (isTamed() && ownerUuid != null) {
            output.putString(OWNER_SAVE_KEY, ownerUuid.toString());
            output.putInt(COMMAND_SAVE_KEY, entityData.get(DATA_COMMAND));
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setVariant(SavannahCatVariant.byName(input.getStringOr(VARIANT_SAVE_KEY, "brown")));
        String savedOwner = input.getStringOr(OWNER_SAVE_KEY, "");
        ownerUuid = null;
        try {
            if (!savedOwner.isEmpty()) ownerUuid = UUID.fromString(savedOwner);
        } catch (IllegalArgumentException ignored) {
            // Corrupt or manually edited owner data must never crash a world load.
        }
        entityData.set(DATA_TAMED, ownerUuid != null);
        entityData.set(DATA_OWNER,ownerUuid==null?"":ownerUuid.toString());
        entityData.set(DATA_COMMAND, CompanionCommand.byId(input.getIntOr(COMMAND_SAVE_KEY,0)).getId());
        entityData.set(DATA_COLLAR,Math.clamp(input.getIntOr("CollarColor",DyeColor.RED.getId()),0,15));
        companionData.load(input.childOrEmpty("CompanionData"));
        companionData.initialize(getRandom());
        familyData.load(input.childOrEmpty("Family"));syncFamilyState();
        if(isBaby() && (getCommand()==CompanionCommand.HUNT || getCommand()==CompanionCommand.HARVEST))entityData.set(DATA_COMMAND,CompanionCommand.FOLLOW.getId());
        if(isStayCommand() && companionData.getStayAnchor()==null)companionData.setStayAnchor(blockPosition());
        clearAction();syncCompanionState();
        if(isDowned()){setHealth(Math.max(1,getHealth()));setAction(CompanionAction.DOWNED,40);setGlowingTag(true);}
        setSitting(input.getBooleanOr(SITTING_SAVE_KEY, false));
        if (isTamed()) setPersistenceRequired();
        // This is a cosmetic one-shot, not persistent state. Reloads restart the idle timer.
        stopSusanTilt();
        susanCooldownTicks = nextSusanCooldown();
    }

    @Override
    public void tick() {
        if (!level().isClientSide()) {
            familyLifecycle.tick();
            lifecycle.tick();
            if(actionTicks>0 && --actionTicks==0 && !isDowned())clearAction();
            if(isAlive() && !isNoAi())companionBrain.tick();
            if(isSitting() && !isDowned() && (!canRemainSitting() || !getNavigation().isDone()
                    || getDeltaMovement().horizontalDistanceSqr()>.0001))setSitting(false);
            if(isSitting())holdSittingPosition();
        }
        super.tick();
        if (!level().isClientSide()) {tickSusanEasterEgg();companionAudio.tick();}
    }

    private int nextSusanCooldown() {
        return SUSAN_MIN_COOLDOWN_TICKS + getRandom().nextInt(SUSAN_EXTRA_COOLDOWN_TICKS);
    }

    /** Server-authoritative event: clients use the synced direction to select the GeckoLib clip. */
    private void tickSusanEasterEgg() {
        if (!isSusan() || !isAlive()) {
            stopSusanTilt();
            return;
        }
        if (getSusanTiltSide() != 0) {
            // Any movement, sitting, injury, or danger should immediately interrupt the cosmetic idle.
            if (!canPlaySpecialIdleAnimation()) {
                stopSusanTilt();
                susanCooldownTicks = nextSusanCooldown();
                return;
            }
            if (--susanTiltTicksRemaining <= 0) {
                stopSusanTilt();
                susanCooldownTicks = nextSusanCooldown();
            }
            return;
        }
        if (susanCooldownTicks > 0) {
            susanCooldownTicks--;
            return;
        }
        // Owned Susan greets her owner; wild Susan may greet a nearby visitor.
        Player greetingPlayer = isTamed() ? getOwnerPlayer() : level().getNearestPlayer(this, SUSAN_GREETING_DISTANCE);
        if (canPlaySpecialIdleAnimation() && greetingPlayer != null
                && distanceToSqr(greetingPlayer) <= SUSAN_GREETING_DISTANCE * SUSAN_GREETING_DISTANCE) {
            int direction = nextSusanTiltRight ? 1 : -1;
            nextSusanTiltRight = !nextSusanTiltRight;
            susanTiltTicksRemaining = SUSAN_TILT_DURATION_TICKS;
            entityData.set(DATA_SUSAN_TILT, direction);
            // Preserve Susan's established recording and adult pitch; kittens add only a restrained lift.
            float susanPitch=(1.2F + getRandom().nextFloat() * 0.12F)*(isBaby()?1.10F:1.0F);
            if(SavannahConfig.SOUNDS.get() && SavannahConfig.SOUND_VOLUME.get()>0)
                playSound(SoundEvents.CAT_PURREOW_BABY.value(), 0.38F*SavannahConfig.SOUND_VOLUME.get().floatValue(), susanPitch);
        }
    }

    private void stopSusanTilt() {
        susanTiltTicksRemaining = 0;
        if (entityData.get(DATA_SUSAN_TILT) != 0) {
            entityData.set(DATA_SUSAN_TILT, 0);
        }
    }

    public int getSusanTiltSide() {
        return entityData.get(DATA_SUSAN_TILT);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if(SavannahConfig.FRIENDLY_FIRE_PROTECTION.get() && source.getEntity() instanceof Player p && isOwnedBy(p))return false;
        if(isDowned() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))return false;
        boolean damaged = super.hurtServer(level, source, damage);
        if (damaged) {
            setSitting(false);
            if (source.getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker) {
                companionBrain.rememberThreat(attacker);
            }
        }
        return damaged;
    }

    @Override public boolean canTrample(ServerLevel level,BlockState state,BlockPos pos,double fallDistance) {
        return !state.is(Blocks.FARMLAND) && super.canTrample(level,state,pos,fallDistance);
    }
    @Override public boolean canAttack(LivingEntity target) {
        return isAlive() && !isBaby() && !isDowned() && !isRecovering()
                && target.level() == level() && !CompanionRelations.isFriendly(this, target) && super.canAttack(target);
    }
    @Override public boolean doHurtTarget(ServerLevel level,net.minecraft.world.entity.Entity target) {
        // Native Mob attacks damage directly, so they must share the companion safety predicate.
        return level() == level && target instanceof LivingEntity living && canAttack(living)
                && super.doHurtTarget(level, target);
    }
    @Override protected void playHurtSound(DamageSource source) { companionAudio.hurt(); }
    @Override protected @Nullable SoundEvent getDeathSound() { return null; }
    @Override protected void playStepSound(BlockPos pos,BlockState state) { companionAudio.step(pos,state); }
    @Override protected void playCombinationStepSounds(BlockState primary,BlockState secondary,BlockPos primaryPos,BlockPos secondaryPos) {
        companionAudio.step(primaryPos,primary);
    }
    @Override protected void playMuffledStepSound(BlockState state,BlockPos pos) {
        // The primary material already emitted one quiet server-owned paw contact.
    }
    @Override protected net.minecraft.world.entity.Entity.MovementEmission getMovementEmission() {
        return level().isClientSide() || !SavannahConfig.SOUNDS.get() || SavannahConfig.SOUND_VOLUME.get()<=0
                || isSitting() || isDowned() || isCompanionSleeping()
                ? net.minecraft.world.entity.Entity.MovementEmission.EVENTS
                : net.minecraft.world.entity.Entity.MovementEmission.ALL;
    }
    @Override public boolean causeFallDamage(double distance,float multiplier,DamageSource source) {
        if(distance<=4 && companionBrain.isControlledLeap())return false;
        return super.causeFallDamage(distance,multiplier,source);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<SavannahCatEntity>("movement", 5, test -> {
            boolean baby=test.getDataOrDefault(BABY_RENDER_DATA,false);
            if(baby!=renderedBaby){test.controller().reset();renderedBaby=baby;renderedActionSequence=-1;}
            RawAnimation gait=baby && test.getDataOrDefault(BABY_RUN_RENDER_DATA,false)?BABY_RUN:WALK;
            var action=CompanionAction.byId(test.getDataOrDefault(ACTION_RENDER_DATA,0));
            int sequence=test.getDataOrDefault(ACTION_SEQUENCE_RENDER_DATA,0);
            boolean peacefulWake=action!=CompanionAction.SLEEP && !action.critical() && !test.isMoving();
            if(peacefulWake && test.isCurrentAnimation(ACTION_ANIMATIONS.get(CompanionAction.SLEEP)))return test.setAndContinue(SLEEP_EXIT_TO_IDLE);
            if(peacefulWake && test.isCurrentAnimationStage("animation.savannah_cat.wake"))return PlayState.CONTINUE;
            if(test.isMoving() && !action.overridesWalking())return test.setAndContinue(gait);
            if(action!=CompanionAction.IDLE) {
                if(sequence!=renderedActionSequence){test.controller().reset();renderedActionSequence=sequence;}
                return test.setAndContinue(baby && action==CompanionAction.INVESTIGATE?BABY_CURIOUS:ACTION_ANIMATIONS.get(action));
            }
            if (test.getDataOrDefault(SITTING_RENDER_DATA, false)) {
                return test.setAndContinue(SIT);
            }
            // Actual movement interrupts the posture immediately instead of waiting for a seated exit.
            if (test.isMoving()) {
                return test.setAndContinue(gait);
            }
            if(test.isCurrentAnimation(ACTION_ANIMATIONS.get(CompanionAction.REST)))return test.setAndContinue(SIT_EXIT_TO_IDLE);
            if (test.isCurrentAnimation(SIT)) {
                return test.setAndContinue(SIT_EXIT_TO_IDLE);
            }
            int susanTilt = test.getDataOrDefault(SUSAN_TILT_RENDER_DATA, 0);
            if (susanTilt < 0) {
                return test.setAndContinue(SUSAN_LEFT);
            }
            if (susanTilt > 0) {
                return test.setAndContinue(SUSAN_RIGHT);
            }
            if (test.isCurrentAnimation(SIT_EXIT_TO_IDLE)) {
                return PlayState.CONTINUE;
            }
            return test.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    @Override
    public final boolean isBaby() {
        return entityData.get(DATA_BABY);
    }

    @Override
    public final void setBaby(boolean baby) {
        if(level().isClientSide())return;
        if(baby){if(!familyData.isBaby())familyData.startBaby(SavannahConfig.GROWTH_TICKS.get());syncFamilyState();}
        else familyLifecycle.tryMature();
    }

    public boolean isSusan() {
        return hasCustomName() && SusanNameHelper.isSusan(getCustomName().getString());
    }

    /** Only relaxed, stationary Susan cats are eligible for this cosmetic animation. */
    public boolean canPlaySpecialIdleAnimation() {
        return isAlive() && !isDowned() && getAction()==CompanionAction.IDLE && !isSitting() && onGround() && !isInLiquid() && !isOnFire()
                && hurtTime == 0 && getTarget() == null && !companionBrain.isThreatened()
                && getNavigation().isDone() && getDeltaMovement().horizontalDistanceSqr() < 0.0001D;
    }

    public SavannahCatBrain getCompanionBrain() {
        return companionBrain;
    }

    public static boolean checkSavannahCatSpawnRules(EntityType<SavannahCatEntity> type,
            ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        return (reason!=EntitySpawnReason.NATURAL || SavannahConfig.SPAWNING.get()) && level.getBlockState(pos.below()).is(BlockTags.ANIMALS_SPAWNABLE_ON)
                && (EntitySpawnReason.ignoresLightRequirements(reason) || level.getRawBrightness(pos, 0) > 8)
                && Mob.checkMobSpawnRules(type, level, reason, pos, random);
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return Math.max(SavannahConfig.SPAWN_MIN.get(),SavannahConfig.SPAWN_MAX.get());
    }
}
