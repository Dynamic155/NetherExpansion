package com.netherexpansion.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.netherexpansion.registry.ModEntities;
import com.netherexpansion.registry.ModSounds;

/**
 * The Underworld Watcher: a stalker that reacts to being looked at.
 *
 * <p>It moves through three phases. While <b>observing</b> it stands still and stares. Once the player has seen
 * it and looks away (or it has watched long enough) it starts <b>closing in</b>: it only moves while unobserved,
 * and freezes the moment it is looked at. Its Hunt Pressure builds while it closes in; when it fills (or the
 * player attacks it) it starts <b>hunting</b>. Looking at it while it hunts only stalls it briefly.
 *
 * <p>While hunting it claws anything in reach, and between claws uses one special at a time: Nether Rift (to
 * close distance or get unstuck), Dread Pulse (a ground wave that blinds; cover or a jump avoids it) or Grasp of
 * the Deep (glowing cracks under each player, then tendrils that root whoever stayed put).
 */
public class UnderworldWatcher extends Monster implements GeoEntity {
	public static final int OBSERVING = 0;
	public static final int CLOSING_IN = 1;
	public static final int HUNTING = 2;

	public static final int NO_ACTION = 0;
	public static final int CLAW = 1;
	public static final int RIFT_OUT = 2;
	public static final int RIFT_IN = 3;
	public static final int PULSE_CHARGE = 4;
	public static final int PULSE_FLASH = 5;
	public static final int PULSE_RELEASE = 6;
	public static final int GRASP = 7;
	public static final int UNVEILING = 8;
	public static final int FALSE_SIGHT = 9;
	public static final int WORLD_REND = 10;
	public static final int UNBLINKING = 11;
	public static final int UNBLINKING_FIRE = 12;
	public static final int STAGGERED = 13;

	// The Unveiling. The renderer swings the mask open from MASK_START to MASK_START + 20 and splits the eye at SPLIT.
	public static final int UNVEIL_LENGTH = 80;
	public static final int UNVEIL_MASK_START = 20;
	public static final int UNVEIL_SPLIT_TICK = 40;
	private static final float UNVEIL_HEALTH = 0.35F;
	private static final byte UNVEIL_EVENT = 100;

	private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(UnderworldWatcher.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(UnderworldWatcher.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_WATCHED = SynchedEntityData.defineId(UnderworldWatcher.class, EntityDataSerializers.BOOLEAN);
	/** Ticks into the Unveiling (0 = still veiled, UNVEIL_LENGTH = fully unveiled). */
	private static final EntityDataAccessor<Integer> DATA_UNVEIL = SynchedEntityData.defineId(UnderworldWatcher.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_ILLUSION = SynchedEntityData.defineId(UnderworldWatcher.class, EntityDataSerializers.BOOLEAN);

	// Watching: the player counts as looking at it within about 21 degrees of their view direction
	private static final double WATCH_CONE = 0.93;
	private static final int BRIGHT_LIGHT = 12;

	// Hunt Pressure
	private static final float PRESSURE_MAX = 100.0F;
	private static final float START_CLOSING_IN = 25.0F;

	// Speeds (navigation multipliers on a base speed of 0.3)
	private static final double CLOSING_IN_SPEED = 1.4;
	private static final double HUNT_SPEED = 1.05;
	private static final double LIT_HUNT_SPEED = 0.7;

	// Action timings, in ticks
	private static final int CLAW_HIT_TICK = 12;
	private static final int CLAW_LENGTH = 20;
	private static final int RIFT_HIDE_TICK = 15;
	private static final int RIFT_EMERGE_TICK = 30;
	private static final int RIFT_LENGTH = 45;
	private static final int PULSE_FLASH_TICK = 20;
	private static final int PULSE_RELEASE_TICK = 26;
	private static final int PULSE_LENGTH = 45;
	private static final int GRASP_SLAM_TICK = 15;
	private static final int GRASP_ERUPT_TICK = 30;
	private static final int GRASP_LENGTH = 34;
	private static final int DEATH_TICKS = 50;

	// Dread Pulse: a ground wave that grows 0.75 blocks per tick out to 12 blocks
	private static final double PULSE_RING_SPEED = 0.75;
	private static final double PULSE_MAX_RADIUS = 12.0;
	private static final DustParticleOptions PULSE_WAVE = new DustParticleOptions(0x2A0F3D, 1.6F);

	// Grasp of the Deep: cracks under each player glow for 0.75s, then tendrils root whoever is still there
	private static final double GRASP_RANGE = 16.0;
	private static final double GRASP_ZONE_RADIUS = 1.4;
	private static final int ROOT_TICKS = 40;
	private static final DustParticleOptions GLOWING_CRACK = new DustParticleOptions(0xFF5A1E, 1.0F);
	private static final DustParticleOptions TENDRIL = new DustParticleOptions(0x1A0B26, 1.4F);

	// False Sight: two copies that mirror its movement around the player, 120 degrees apart
	private static final int FALSE_SIGHT_SPAWN_TICK = 15;
	private static final int FALSE_SIGHT_LENGTH = 30;
	private static final int ILLUSION_LIFETIME = 300;

	// World Rend: a crack 14 blocks long and 3 wide glows for 1.5s, then erupts
	private static final int REND_CRACK_TICK = 14;
	private static final int REND_ERUPT_TICK = 44;
	private static final int REND_LENGTH = 50;
	private static final double REND_REACH = 14.0;
	private static final double REND_HALF_WIDTH = 1.5;
	private static final DustParticleOptions REND_GLOW = new DustParticleOptions(0xFF6A20, 1.3F);

	// Unblinking: a 3s stare. Breaking line of sight for 4 ticks interrupts it and staggers the Watcher.
	private static final int UNBLINKING_CHARGE = 60;
	private static final int UNBLINKING_FIRE_TICK = 6;
	private static final int UNBLINKING_FIRE_LENGTH = 12;
	private static final int STAGGER_LENGTH = 30;
	private static final int LOS_GRACE = 4;
	private static final DustParticleOptions BEAM = new DustParticleOptions(0xE8D8FF, 1.5F);

	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	private static final RawAnimation WATCHED = RawAnimation.begin().thenPlay("tilt").thenLoop("watched");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	private static final RawAnimation SPRINT = RawAnimation.begin().thenLoop("sprint");
	private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");
	private static final RawAnimation CLAW_ANIM = RawAnimation.begin().thenPlay("claw");
	private static final RawAnimation RIFT_OUT_ANIM = RawAnimation.begin().thenPlayAndHold("rift_out");
	private static final RawAnimation RIFT_IN_ANIM = RawAnimation.begin().thenPlay("rift_in");
	private static final RawAnimation DREAD_PULSE_ANIM = RawAnimation.begin().thenPlay("dread_pulse");
	private static final RawAnimation GRASP_ANIM = RawAnimation.begin().thenPlay("grasp");
	private static final RawAnimation UNVEIL_ANIM = RawAnimation.begin().thenPlay("unveil");
	private static final RawAnimation FALSE_SIGHT_ANIM = RawAnimation.begin().thenPlay("false_sight");
	private static final RawAnimation WORLD_REND_ANIM = RawAnimation.begin().thenPlay("world_rend");
	private static final RawAnimation UNBLINKING_ANIM = RawAnimation.begin().thenLoop("unblinking");
	private static final RawAnimation UNBLINKING_FIRE_ANIM = RawAnimation.begin().thenPlay("unblinking_fire");

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private float huntPressure;
	private boolean hasBeenSeen;
	private int actionTicks;
	private int clawCooldown;
	private int riftCooldown;
	private int pulseCooldown;
	private int graspCooldown;
	/** Shared gap between Nether Rift, Dread Pulse and Grasp, so it uses one at a time between claws. */
	private int specialCooldown;
	private int creepCooldown;
	private int heartbeatCooldown;
	private int freezeTicks;
	private int freezeCooldown;
	private int unwatchedTicks;
	private int lostTicks;
	private boolean pathFound = true;
	private Vec3 riftDestination = Vec3.ZERO;
	private final Set<UUID> pulseHits = new HashSet<>();
	private final List<Vec3> graspZones = new ArrayList<>();

	// Boss phase
	private final ServerBossEvent bossEvent = (ServerBossEvent) new ServerBossEvent(UUID.randomUUID(),
			Component.translatable("boss.netherexpansion.underworld_watcher"), BossEvent.BossBarColor.PURPLE,
			BossEvent.BossBarOverlay.NOTCHED_10).setDarkenScreen(true);
	private int falseSightCooldown;
	private int rendCooldown;
	private int unblinkingCooldown;
	private int losBrokenTicks;
	private Vec3 rendStart = Vec3.ZERO;
	private Vec3 rendEnd = Vec3.ZERO;
	private final List<Integer> illusionIds = new ArrayList<>();

	// Only set on illusions: the real Watcher they copy, and where around the player they stand
	private int ownerId = -1;
	private float illusionAngle;
	private int illusionLife;

	public UnderworldWatcher(EntityType<? extends UnderworldWatcher> type, Level level) {
		super(type, level);
		// Boss bars start visible; this one stays hidden until the Unveiling (or a load of an unveiled Watcher)
		bossEvent.setVisible(false);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 100.0)
				.add(Attributes.ATTACK_DAMAGE, 10.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FOLLOW_RANGE, 64.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.ARMOR, 6.0)
				.add(Attributes.STEP_HEIGHT, 1.5);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		// Wanders only when it has nobody to watch; otherwise customServerAiStep drives it.
		goalSelector.addGoal(8, new RandomStrollGoal(this, 0.6) {
			@Override
			public boolean canUse() {
				return getTarget() == null && super.canUse();
			}
		});
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_PHASE, OBSERVING);
		builder.define(DATA_ACTION, NO_ACTION);
		builder.define(DATA_WATCHED, false);
		builder.define(DATA_UNVEIL, 0);
		builder.define(DATA_ILLUSION, false);
	}

	/** Ticks into the Unveiling: 0 while veiled, UNVEIL_LENGTH once the boss phase has begun. */
	public int getUnveilTicks() {
		return entityData.get(DATA_UNVEIL);
	}

	private void setUnveilTicks(int ticks) {
		entityData.set(DATA_UNVEIL, ticks);
	}

	public boolean isUnveiled() {
		return getUnveilTicks() > 0;
	}

	/** A False Sight copy: harmless, silent, and gone in one hit. */
	public boolean isIllusion() {
		return entityData.get(DATA_ILLUSION);
	}

	public int getPhase() {
		return entityData.get(DATA_PHASE);
	}

	private void setPhase(int phase) {
		entityData.set(DATA_PHASE, phase);
	}

	public int getAction() {
		return entityData.get(DATA_ACTION);
	}

	private void setAction(int action) {
		entityData.set(DATA_ACTION, action);
	}

	public boolean isWatched() {
		return entityData.get(DATA_WATCHED);
	}

	private void setWatched(boolean watched) {
		entityData.set(DATA_WATCHED, watched);
	}

	/**
	 * Texture suffix for the eye: dim orange while watching (and while staggered), red while hunting, pale violet
	 * while charging Dread Pulse or Unblinking, a white flash just before they release, and dark when dead.
	 */
	public String eyeVariant() {
		if (isDeadOrDying()) {
			return "_dark";
		}
		return switch (getAction()) {
			case PULSE_CHARGE, UNBLINKING -> "_charging";
			case PULSE_FLASH, UNBLINKING_FIRE -> "_flash";
			case STAGGERED -> "";
			default -> getAction() != NO_ACTION || getPhase() == HUNTING ? "_hunting" : "";
		};
	}

	/** Used by the spawner: lock onto a player straight away. */
	public void beginStalking(Player player) {
		setTarget(player);
		getLookControl().setLookAt(player);
	}

	// ---------------------------------------------------------------------------------------------
	// The Watch
	// ---------------------------------------------------------------------------------------------

	/** True when the player is looking at its head or chest and can actually see it. */
	public boolean isLookedAtBy(Player player) {
		if (player.isSpectator() || !player.isAlive()) {
			return false;
		}
		Vec3 view = player.getViewVector(1.0F).normalize();
		Vec3 eye = player.getEyePosition();
		for (double height : new double[] {0.92, 0.55}) {
			Vec3 toPoint = position().add(0, getBbHeight() * height, 0).subtract(eye).normalize();
			if (view.dot(toPoint) > WATCH_CONE) {
				return player.hasLineOfSight(this);
			}
		}
		return false;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (isIllusion()) {
			tickIllusion(level);
			return;
		}
		tickCooldowns();
		bossEvent.setProgress(getHealth() / getMaxHealth());

		LivingEntity target = getTarget();
		if (getAction() != NO_ACTION) {
			tickAction(level, target);
			return;
		}
		if (target == null || !target.isAlive()) {
			setWatched(false);
			setSprinting(false);
			if (getPhase() == HUNTING && ++lostTicks > 200) {
				calmDown();
			}
			return;
		}

		double distance = distanceTo(target);
		boolean watched = target instanceof Player player && isLookedAtBy(player);
		setWatched(watched);
		unwatchedTicks = watched ? 0 : unwatchedTicks + 1;
		tickHeartbeat(level, distance);

		switch (getPhase()) {
			case OBSERVING -> tickObserving(target, watched, distance);
			case CLOSING_IN -> tickClosingIn(level, target, watched, distance);
			default -> tickHunting(level, target, watched, distance);
		}
	}

	private void tickCooldowns() {
		clawCooldown = Math.max(0, clawCooldown - 1);
		riftCooldown = Math.max(0, riftCooldown - 1);
		pulseCooldown = Math.max(0, pulseCooldown - 1);
		graspCooldown = Math.max(0, graspCooldown - 1);
		specialCooldown = Math.max(0, specialCooldown - 1);
		falseSightCooldown = Math.max(0, falseSightCooldown - 1);
		rendCooldown = Math.max(0, rendCooldown - 1);
		unblinkingCooldown = Math.max(0, unblinkingCooldown - 1);
		creepCooldown = Math.max(0, creepCooldown - 1);
		freezeCooldown = Math.max(0, freezeCooldown - 1);
	}

	/** Phase 1: stand still and stare. Turns its head slowly, unless looked at, when it snaps. */
	private void tickObserving(LivingEntity target, boolean watched, double distance) {
		freeze();
		lookAtTarget(target, watched);
		if (watched) {
			hasBeenSeen = true;
		} else if (hasBeenSeen) {
			// The player saw it and looked away
			setPhase(CLOSING_IN);
			return;
		}
		if (distance < 32) {
			addPressure(0.04F);
		}
		if (huntPressure >= START_CLOSING_IN) {
			setPhase(CLOSING_IN);
		}
	}

	/** Phase 2: move only while unobserved. Waits just outside bright light. */
	private void tickClosingIn(ServerLevel level, LivingEntity target, boolean watched, double distance) {
		if (watched) {
			freeze();
			lookAtTarget(target, true);
			addPressure(0.03F);
		} else {
			lookAtTarget(target, false);
			boolean targetInLight = level.getBrightness(LightLayer.BLOCK, target.blockPosition()) >= BRIGHT_LIGHT;
			if (targetInLight && distance < 9) {
				freeze();
				addPressure(0.02F);
			} else {
				navigateTo(target, CLOSING_IN_SPEED);
				addPressure(distance < 6 ? 1.5F : 0.15F);
			}
			if (unwatchedTicks > 40 && distance > 16 && creepCooldown == 0) {
				creepCloser(level, target);
			}
		}
		if (huntPressure >= PRESSURE_MAX) {
			startHunt();
		}
	}

	/** Phase 3: chase, claw and rift. Being looked at only stalls it for half a second. */
	private void tickHunting(ServerLevel level, LivingEntity target, boolean watched, double distance) {
		lostTicks = distance > 48 ? lostTicks + 1 : 0;
		if (lostTicks > 200) {
			calmDown();
			return;
		}
		lookAtTarget(target, true);
		if (watched && freezeCooldown == 0) {
			freezeTicks = isUnveiled() ? 6 : 10;
			freezeCooldown = 70;
		}
		if (freezeTicks > 0) {
			freezeTicks--;
			freeze();
			setSprinting(false);
			return;
		}
		if (distance <= 3.5 && clawCooldown == 0 && hasLineOfSight(target)) {
			startClaw();
			return;
		}
		boolean inLight = level.getBrightness(LightLayer.BLOCK, blockPosition()) >= BRIGHT_LIGHT;
		double speed = inLight ? LIT_HUNT_SPEED : HUNT_SPEED;
		navigateTo(target, isUnveiled() ? speed * 1.15 : speed);
		setSprinting(!inLight);
		boolean stuck = !pathFound || (distance > 5 && getNavigation().isStuck());
		if (specialCooldown == 0) {
			startSpecial(level, target, distance, stuck);
		}
	}

	/**
	 * Picks one special. Nether Rift comes first when the target is far or out of reach; otherwise it chooses at
	 * random among the ones in range: Dread Pulse (mid range) and Grasp of the Deep (longer range), plus False
	 * Sight, World Rend and Unblinking once unveiled. All but False Sight need to see the target.
	 */
	private void startSpecial(ServerLevel level, LivingEntity target, double distance, boolean stuck) {
		if (riftCooldown == 0 && (distance > 14 || stuck) && startRift(level, target)) {
			return;
		}
		boolean sees = hasLineOfSight(target);
		List<Runnable> options = new ArrayList<>();
		if (pulseCooldown == 0 && sees && distance >= 3 && distance <= 10) {
			options.add(this::startDreadPulse);
		}
		if (graspCooldown == 0 && sees && distance >= 6 && distance <= GRASP_RANGE) {
			options.add(this::startGrasp);
		}
		if (isUnveiled()) {
			illusionIds.removeIf(id -> !(level.getEntity(id) instanceof UnderworldWatcher copy) || !copy.isAlive());
			if (falseSightCooldown == 0 && illusionIds.isEmpty() && distance <= 20) {
				options.add(this::startFalseSight);
			}
			if (rendCooldown == 0 && sees && distance >= 4 && distance <= REND_REACH) {
				options.add(this::startWorldRend);
			}
			if (unblinkingCooldown == 0 && sees && distance >= 6 && distance <= 24) {
				options.add(this::startUnblinking);
			}
		}
		if (!options.isEmpty()) {
			options.get(random.nextInt(options.size())).run();
		}
	}

	private void beginSpecial(int action, int length) {
		setAction(action);
		actionTicks = 0;
		freeze();
		setSprinting(false);
		// The boss phase leaves shorter gaps between specials
		specialCooldown = length + (isUnveiled() ? 40 + random.nextInt(40) : 80 + random.nextInt(60));
	}

	/** Plays an action animation on the Watcher and on its False Sight copies, so they mimic it. */
	private void animate(String animation) {
		triggerAnim("action", animation);
		forEachIllusion(copy -> copy.triggerAnim("action", animation));
	}

	private void stopAnimating(String animation) {
		stopTriggeredAnim("action", animation);
		forEachIllusion(copy -> copy.stopTriggeredAnim("action", animation));
	}

	private void forEachIllusion(java.util.function.Consumer<UnderworldWatcher> action) {
		for (int id : illusionIds) {
			if (level().getEntity(id) instanceof UnderworldWatcher copy && copy.isAlive()) {
				action.accept(copy);
			}
		}
	}

	private void startHunt() {
		setPhase(HUNTING);
		huntPressure = PRESSURE_MAX;
		clawCooldown = 10;
		riftCooldown = 60;
		pulseCooldown = 100;
		graspCooldown = 60;
		specialCooldown = 40;
		playSound(ModSounds.WATCHER_HUNT, 3.0F, 1.0F);
	}

	/** Lost its target for a while: go back to watching from a distance. */
	private void calmDown() {
		setPhase(OBSERVING);
		huntPressure = 30.0F;
		hasBeenSeen = false;
		lostTicks = 0;
		setSprinting(false);
		getNavigation().stop();
	}

	private void addPressure(float amount) {
		huntPressure = Math.min(PRESSURE_MAX, huntPressure + amount);
	}

	private void freeze() {
		getNavigation().stop();
		Vec3 motion = getDeltaMovement();
		setDeltaMovement(0, motion.y, 0);
	}

	private void lookAtTarget(LivingEntity target, boolean snap) {
		getLookControl().setLookAt(target, snap ? 60.0F : 3.0F, 30.0F);
	}

	/** Re-paths every half second, or right away once the old path is finished. */
	private void navigateTo(LivingEntity target, double speed) {
		if (tickCount % 10 == 0 || getNavigation().isDone()) {
			pathFound = getNavigation().moveTo(target, speed);
		}
	}

	private void tickHeartbeat(ServerLevel level, double distance) {
		if (distance > 40 || --heartbeatCooldown > 0) {
			return;
		}
		float closeness = 1.0F - (float) (distance / 40.0);
		level.playSound(null, getX(), getY() + 1.0, getZ(), ModSounds.WATCHER_HEARTBEAT, SoundSource.HOSTILE,
				3.0F, 0.85F + 0.25F * closeness);
		heartbeatCooldown = (int) Mth.lerp(closeness, 50, 12);
	}

	/** While unobserved, reappear somewhere closer that the player is not looking at. */
	private void creepCloser(ServerLevel level, LivingEntity target) {
		Vec3 view = target.getViewVector(1.0F);
		Vec3 spot = findStandingSpot(level, target.position(), 8, 14, 24, pos -> {
			Vec3 toSpot = pos.subtract(target.getEyePosition()).normalize();
			return view.dot(toSpot) < 0.2 && level.getBrightness(LightLayer.BLOCK, BlockPos.containing(pos)) < BRIGHT_LIGHT;
		});
		creepCooldown = 80 + random.nextInt(60);
		if (spot != null) {
			level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.5, getZ(), 12, 0.3, 1.0, 0.3, 0.01);
			teleportTo(spot.x, spot.y, spot.z);
			getNavigation().stop();
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Attacks
	// ---------------------------------------------------------------------------------------------

	private void startClaw() {
		setAction(CLAW);
		actionTicks = 0;
		freeze();
		setSprinting(false);
		animate("claw");
		playSound(ModSounds.WATCHER_CLAW_WINDUP, 1.5F, 1.0F);
	}

	/** Returns false (and waits a bit before retrying) when there is nowhere to emerge. */
	private boolean startRift(ServerLevel level, LivingEntity target) {
		float behind = target.getYRot() + 180.0F;
		int[] tries = {0};
		Vec3 exit = findStandingSpot(level, target.position(), 4, 7, 30, pos -> {
			if (pos.distanceTo(target.position()) < 3.0) {
				return false; // never on top of the player
			}
			// Prefer the dark, and behind the player, for the first tries
			boolean early = tries[0]++ < 18;
			if (early && level.getBrightness(LightLayer.BLOCK, BlockPos.containing(pos)) >= BRIGHT_LIGHT) {
				return false;
			}
			if (early) {
				float angle = (float) Math.toDegrees(Math.atan2(-(pos.x - target.getX()), pos.z - target.getZ()));
				return Math.abs(Mth.wrapDegrees(angle - behind)) < 80;
			}
			return true;
		});
		if (exit == null) {
			riftCooldown = 40;
			return false;
		}
		riftDestination = exit;
		beginSpecial(RIFT_OUT, RIFT_LENGTH);
		animate("rift_out");
		playSound(ModSounds.WATCHER_RIFT_OUT, 2.0F, 1.0F);
		return true;
	}

	private void startDreadPulse() {
		beginSpecial(PULSE_CHARGE, PULSE_LENGTH);
		pulseHits.clear();
		animate("dread_pulse");
		playSound(ModSounds.WATCHER_PULSE_CHARGE, 2.5F, 1.0F);
	}

	private void startGrasp() {
		beginSpecial(GRASP, GRASP_LENGTH);
		graspZones.clear();
		animate("grasp");
	}

	private void startFalseSight() {
		beginSpecial(FALSE_SIGHT, FALSE_SIGHT_LENGTH);
		animate("false_sight");
		playSound(ModSounds.WATCHER_FALSE_SIGHT, 2.5F, 1.0F);
	}

	private void startWorldRend() {
		beginSpecial(WORLD_REND, REND_LENGTH);
		animate("world_rend");
		playSound(ModSounds.WATCHER_CLAW_WINDUP, 2.0F, 0.8F);
	}

	private void startUnblinking() {
		beginSpecial(UNBLINKING, UNBLINKING_CHARGE + UNBLINKING_FIRE_LENGTH);
		losBrokenTicks = 0;
		animate("unblinking");
	}

	// ---------------------------------------------------------------------------------------------
	// The Unveiling (boss phase)
	// ---------------------------------------------------------------------------------------------

	/**
	 * At 35% health it stops fighting, stands upright and stares. Its mask cracks open and the eye splits in three.
	 * The world darkens, particles freeze in the air, and the boss bar appears.
	 */
	private void startUnveiling(ServerLevel level) {
		setAction(UNVEILING);
		actionTicks = 0;
		freeze();
		setSprinting(false);
		setPhase(HUNTING);
		setUnveilTicks(1);
		setPersistenceRequired();
		animate("unveil");
		playSound(ModSounds.WATCHER_UNVEIL, 4.0F, 1.0F);
		level.broadcastEntityEvent(this, UNVEIL_EVENT);
		bossEvent.setVisible(true);
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(48))) {
			player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120), this);
			player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
			player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("boss.netherexpansion.underworld_watcher.unveiled")));
			player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("boss.netherexpansion.underworld_watcher")));
		}
	}

	private void tickUnveiling(ServerLevel level, LivingEntity target) {
		if (target != null) {
			lookAtTarget(target, true);
		}
		setUnveilTicks(Math.min(actionTicks, UNVEIL_LENGTH));
		if (actionTicks == UNVEIL_SPLIT_TICK) {
			playSound(ModSounds.WATCHER_SHATTER, 3.0F, 0.7F);
			Vec3 eye = getEyePosition();
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OBSIDIAN.defaultBlockState()),
					eye.x, eye.y, eye.z, 25, 0.3, 0.3, 0.3, 0.1);
		}
		if (actionTicks >= UNVEIL_LENGTH) {
			setAction(NO_ACTION);
			clawCooldown = 10;
			specialCooldown = 20;
			falseSightCooldown = 0;
			rendCooldown = 40;
			unblinkingCooldown = 100;
		}
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == UNVEIL_EVENT) {
			WatcherEffects.particleFreezeTicks = 40;
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		if (!isIllusion()) {
			bossEvent.addPlayer(player);
		}
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		bossEvent.removePlayer(player);
	}

	// ---------------------------------------------------------------------------------------------
	// False Sight copies
	// ---------------------------------------------------------------------------------------------

	/** Two copies split off and take up mirrored positions around the player. */
	private void spawnIllusions(ServerLevel level) {
		for (float angle : new float[] {120.0F, -120.0F}) {
			UnderworldWatcher copy = ModEntities.UNDERWORLD_WATCHER.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (copy == null) {
				continue;
			}
			copy.entityData.set(DATA_ILLUSION, true);
			copy.ownerId = getId();
			copy.illusionAngle = angle;
			copy.setUnveilTicks(UNVEIL_LENGTH);
			copy.setPhase(HUNTING);
			copy.snapTo(getX(), getY(), getZ(), getYRot(), 0.0F);
			copy.setYHeadRot(getYHeadRot());
			copy.setYBodyRot(yBodyRot);
			copy.setHealth(1.0F);
			if (level.addFreshEntity(copy)) {
				illusionIds.add(copy.getId());
			}
		}
		level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.6, getZ(), 30, 0.6, 1.2, 0.6, 0.02);
	}

	/**
	 * A copy stands where the real Watcher would be if the scene were rotated around the player, copies its stance
	 * and actions, and never does anything itself.
	 */
	private void tickIllusion(ServerLevel level) {
		Entity entity = ownerId >= 0 ? level.getEntity(ownerId) : null;
		if (!(entity instanceof UnderworldWatcher owner) || !owner.isAlive() || ++illusionLife > ILLUSION_LIFETIME
				|| owner.getTarget() == null) {
			vanish(level);
			return;
		}
		LivingEntity target = owner.getTarget();
		setAction(owner.getAction());
		setWatched(owner.isWatched());
		setSprinting(owner.isSprinting());

		Vec3 offset = owner.position().subtract(target.position());
		float cos = Mth.cos(illusionAngle * Mth.DEG_TO_RAD);
		float sin = Mth.sin(illusionAngle * Mth.DEG_TO_RAD);
		Vec3 wanted = target.position().add(offset.x * cos - offset.z * sin, offset.y, offset.x * sin + offset.z * cos);

		if (distanceToSqr(wanted) > 100) {
			Vec3 spot = findStandingSpot(level, wanted, 0, 2, 8, pos -> true);
			if (spot != null) {
				teleportTo(spot.x, spot.y, spot.z);
			}
		} else if (owner.getAction() == NO_ACTION && (tickCount % 5 == 0 || getNavigation().isDone())) {
			getNavigation().moveTo(wanted.x, wanted.y, wanted.z, owner.isSprinting() ? 1.6 : 1.3);
		}
		if (owner.getAction() != NO_ACTION) {
			freeze();
		}
		getLookControl().setLookAt(target, 60.0F, 30.0F);
	}

	private void vanish(ServerLevel level) {
		level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.6, getZ(), 20, 0.4, 1.0, 0.4, 0.02);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1.6, getZ(), 15, 0.4, 1.0, 0.4, 0.05);
		playSound(ModSounds.WATCHER_ILLUSION_BREAK, 2.0F, 1.0F);
		discard();
	}

	@Override
	public boolean shouldBeSaved() {
		return !isIllusion() && super.shouldBeSaved();
	}

	@Override
	protected boolean shouldDropLoot(ServerLevel level) {
		return !isIllusion() && super.shouldDropLoot(level);
	}

	@Override
	public boolean shouldDropExperience() {
		return !isIllusion() && super.shouldDropExperience();
	}

	// ---------------------------------------------------------------------------------------------
	// World Rend and Unblinking
	// ---------------------------------------------------------------------------------------------

	/** The claw rakes the ground: a crack opens from its feet toward where the target stands now. */
	private void rendOpen(ServerLevel level, LivingEntity target) {
		Vec3 aim = target != null ? target.position() : position().add(Vec3.directionFromRotation(0, getYRot()));
		Vec3 direction = aim.subtract(position()).multiply(1, 0, 1).normalize();
		rendStart = position().add(direction.scale(1.5));
		rendEnd = rendStart.add(direction.scale(REND_REACH));
		level.playSound(null, rendStart.x, rendStart.y, rendStart.z, ModSounds.WATCHER_GRASP_CRACK, SoundSource.HOSTILE, 2.5F, 0.8F);
	}

	/** The warning: the crack glows along its whole length, with smoke at its edges. */
	private void rendGlow(ServerLevel level) {
		Vec3 along = rendEnd.subtract(rendStart);
		Vec3 side = new Vec3(-along.z, 0, along.x).normalize();
		for (int i = 0; i < 12; i++) {
			Vec3 point = rendStart.add(along.scale(random.nextDouble()));
			level.sendParticles(REND_GLOW, point.x, point.y + 0.1, point.z, 1, 0.15, 0, 0.15, 0);
			if (i % 3 == 0) {
				Vec3 edge = point.add(side.scale((random.nextBoolean() ? 1 : -1) * REND_HALF_WIDTH));
				level.sendParticles(ParticleTypes.SMOKE, edge.x, edge.y + 0.1, edge.z, 1, 0, 0.03, 0, 0.01);
			}
		}
	}

	/** The crack erupts: damage and a launch for anyone still standing on it. */
	private void rendErupt(ServerLevel level) {
		level.playSound(null, rendStart.x, rendStart.y, rendStart.z, ModSounds.WATCHER_REND_ERUPT, SoundSource.HOSTILE, 4.0F, 1.0F);
		Vec3 along = rendEnd.subtract(rendStart);
		for (double d = 0; d <= REND_REACH; d += 0.7) {
			Vec3 point = rendStart.add(along.normalize().scale(d));
			level.sendParticles(ParticleTypes.LAVA, point.x, point.y + 0.2, point.z, 2, 0.5, 0.1, 0.5, 0);
			level.sendParticles(ParticleTypes.FLAME, point.x, point.y + 0.4, point.z, 4, 0.5, 0.6, 0.5, 0.05);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, point.x, point.y + 0.8, point.z, 2, 0.4, 0.6, 0.4, 0.03);
			if (d % 2.8 < 0.7) {
				level.sendParticles(ParticleTypes.EXPLOSION, point.x, point.y + 0.5, point.z, 1, 0, 0, 0, 0);
			}
		}
		for (Player player : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(REND_REACH + 3, 4, REND_REACH + 3))) {
			if (player.isSpectator() || player.isCreative() || Math.abs(player.getY() - rendStart.y) > 2.5) {
				continue;
			}
			if (distanceToSegment(player.position(), rendStart, rendEnd) <= REND_HALF_WIDTH + 0.3) {
				player.hurtServer(level, damageSources().mobAttack(this), 12.0F);
				Vec3 motion = player.getDeltaMovement();
				player.setDeltaMovement(motion.x, 0.8, motion.z);
				player.needsSync = true;
			}
		}
	}

	/** Horizontal distance from a point to the segment a-b. */
	private static double distanceToSegment(Vec3 point, Vec3 a, Vec3 b) {
		double abx = b.x - a.x;
		double abz = b.z - a.z;
		double lengthSqr = abx * abx + abz * abz;
		double t = lengthSqr == 0 ? 0 : Mth.clamp(((point.x - a.x) * abx + (point.z - a.z) * abz) / lengthSqr, 0, 1);
		return Math.sqrt(Mth.square(point.x - (a.x + abx * t)) + Mth.square(point.z - (a.z + abz * t)));
	}

	/** The charge: light gathers in its three eyes with a rising tone. */
	private void tickUnblinkingCharge(ServerLevel level, LivingEntity target) {
		if (target == null || !target.isAlive()) {
			setAction(NO_ACTION);
			stopAnimating("unblinking");
			return;
		}
		lookAtTarget(target, true);
		losBrokenTicks = hasLineOfSight(target) ? 0 : losBrokenTicks + 1;
		if (losBrokenTicks >= LOS_GRACE) {
			// Broke line of sight or reached cover: the charge collapses and it staggers
			stopAnimating("unblinking");
			setAction(STAGGERED);
			actionTicks = 0;
			unblinkingCooldown = 300;
			playSound(ModSounds.WATCHER_UNBLINKING_FAIL, 2.5F, 1.0F);
			return;
		}
		Vec3 eye = getEyePosition();
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, eye.x, eye.y, eye.z, 4, 0.8, 0.8, 0.8, 0.08);
		if (actionTicks % 15 == 1) {
			playSound(ModSounds.WATCHER_UNBLINKING_CHARGE, 2.5F, 0.6F + 0.8F * actionTicks / UNBLINKING_CHARGE);
		}
		if (actionTicks >= UNBLINKING_CHARGE) {
			stopAnimating("unblinking");
			animate("unblinking_fire");
			setAction(UNBLINKING_FIRE);
			actionTicks = 0;
		}
	}

	/** The beam: from its eyes to the target's, stopped by the first solid block. */
	private void fireBeam(ServerLevel level, LivingEntity target) {
		playSound(ModSounds.WATCHER_UNBLINKING_FIRE, 4.0F, 1.0F);
		Vec3 from = getEyePosition();
		Vec3 to = target != null ? target.getEyePosition() : from.add(getViewVector(1.0F).scale(24));
		HitResult blocked = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		Vec3 end = blocked.getType() == HitResult.Type.MISS ? to : blocked.getLocation();
		Vec3 step = end.subtract(from);
		double length = step.length();
		step = step.normalize();
		for (double d = 0.5; d < length; d += 0.25) {
			Vec3 point = from.add(step.scale(d));
			level.sendParticles(BEAM, point.x, point.y, point.z, 1, 0.03, 0.03, 0.03, 0);
			if (d % 2.0 < 0.25) {
				level.sendParticles(ParticleTypes.SONIC_BOOM, point.x, point.y, point.z, 1, 0, 0, 0, 0);
			}
		}
		if (target != null && blocked.getType() == HitResult.Type.MISS) {
			target.hurtServer(level, damageSources().sonicBoom(this), 14.0F);
			target.push(step.x * 1.2, 0.3, step.z * 1.2);
			target.needsSync = true;
		}
	}

	private void tickAction(ServerLevel level, LivingEntity target) {
		actionTicks++;
		freeze();
		switch (getAction()) {
			case CLAW -> {
				if (target != null) {
					lookAtTarget(target, true);
				}
				if (actionTicks == CLAW_HIT_TICK) {
					clawStrike(level, target);
				}
				if (actionTicks >= CLAW_LENGTH) {
					setAction(NO_ACTION);
					clawCooldown = isUnveiled() ? 18 : 25;
				}
			}
			case RIFT_OUT -> {
				if (actionTicks < RIFT_HIDE_TICK) {
					level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.6, getZ(), 3, 0.3, 1.0, 0.3, 0.01);
					level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1.6, getZ(), 2, 0.3, 1.0, 0.3, 0.02);
				} else if (actionTicks == RIFT_HIDE_TICK) {
					teleportTo(riftDestination.x, riftDestination.y, riftDestination.z);
				} else {
					// The exit telegraph: smoke and embers rising where it will emerge
					level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.2, getZ(), 2, 0.25, 0.1, 0.25, 0.03);
					level.sendParticles(new DustParticleOptions(0xFF4A12, 0.8F), getX(), getY() + 0.3, getZ(), 2, 0.3, 0.2, 0.3, 0.0);
				}
				if (actionTicks >= RIFT_EMERGE_TICK) {
					setAction(RIFT_IN);
					animate("rift_in");
					playSound(ModSounds.WATCHER_RIFT_IN, 2.0F, 1.0F);
				}
			}
			case RIFT_IN -> {
				if (target != null) {
					lookAtTarget(target, true);
				}
				if (actionTicks >= RIFT_LENGTH) {
					setAction(NO_ACTION);
					riftCooldown = 120 + random.nextInt(80);
					clawCooldown = 0;
				}
			}
			case PULSE_CHARGE -> {
				// Arches back while light gathers in the eye (pale violet)
				if (target != null) {
					lookAtTarget(target, true);
				}
				if (actionTicks % 3 == 0) {
					Vec3 eye = getEyePosition();
					level.sendParticles(ParticleTypes.REVERSE_PORTAL, eye.x, eye.y, eye.z, 3, 0.4, 0.4, 0.4, 0.05);
				}
				if (actionTicks >= PULSE_FLASH_TICK) {
					setAction(PULSE_FLASH);
				}
			}
			case PULSE_FLASH -> {
				// The eye flashes white: the last warning before the wave
				if (actionTicks >= PULSE_RELEASE_TICK) {
					setAction(PULSE_RELEASE);
					playSound(ModSounds.WATCHER_PULSE_SCREAM, 4.0F, 1.0F);
				}
			}
			case PULSE_RELEASE -> {
				int ringTick = actionTicks - PULSE_RELEASE_TICK - 1;
				double radius = 1.0 + ringTick * PULSE_RING_SPEED;
				if (radius <= PULSE_MAX_RADIUS) {
					pulseWave(level, radius, ringTick == 0 ? 0.0 : radius - PULSE_RING_SPEED);
				}
				if (actionTicks >= PULSE_LENGTH) {
					setAction(NO_ACTION);
					pulseCooldown = 260 + random.nextInt(80);
				}
			}
			case GRASP -> {
				if (target != null) {
					lookAtTarget(target, true);
				}
				if (actionTicks == GRASP_SLAM_TICK) {
					graspSlam(level);
				} else if (actionTicks > GRASP_SLAM_TICK && actionTicks < GRASP_ERUPT_TICK) {
					graspCracksGlow(level);
				} else if (actionTicks == GRASP_ERUPT_TICK) {
					graspErupt(level);
				}
				if (actionTicks >= GRASP_LENGTH) {
					setAction(NO_ACTION);
					graspCooldown = 220 + random.nextInt(80);
				}
			}
			case UNVEILING -> tickUnveiling(level, target);
			case FALSE_SIGHT -> {
				if (target != null) {
					lookAtTarget(target, true);
				}
				if (actionTicks == FALSE_SIGHT_SPAWN_TICK) {
					spawnIllusions(level);
				}
				if (actionTicks >= FALSE_SIGHT_LENGTH) {
					setAction(NO_ACTION);
					falseSightCooldown = 420;
				}
			}
			case WORLD_REND -> {
				if (actionTicks == REND_CRACK_TICK) {
					rendOpen(level, target);
				} else if (actionTicks > REND_CRACK_TICK && actionTicks < REND_ERUPT_TICK) {
					rendGlow(level);
				} else if (actionTicks == REND_ERUPT_TICK) {
					rendErupt(level);
				} else if (actionTicks < REND_CRACK_TICK && target != null) {
					lookAtTarget(target, true);
				}
				if (actionTicks >= REND_LENGTH) {
					setAction(NO_ACTION);
					rendCooldown = 220 + random.nextInt(60);
				}
			}
			case UNBLINKING -> tickUnblinkingCharge(level, target);
			case UNBLINKING_FIRE -> {
				if (actionTicks == UNBLINKING_FIRE_TICK) {
					fireBeam(level, target);
				}
				if (actionTicks >= UNBLINKING_FIRE_LENGTH) {
					setAction(NO_ACTION);
					unblinkingCooldown = 320;
				}
			}
			case STAGGERED -> {
				if (actionTicks >= STAGGER_LENGTH) {
					setAction(NO_ACTION);
				}
			}
			default -> setAction(NO_ACTION);
		}
	}

	/** The hit lands only if the target is still in reach and in front: dodging back or strafing works. */
	private void clawStrike(ServerLevel level, LivingEntity target) {
		boolean inReach = target != null && target.isAlive() && distanceTo(target) <= 4.2 && hasLineOfSight(target);
		if (inReach) {
			Vec3 facing = Vec3.directionFromRotation(0, getYRot());
			Vec3 toTarget = target.position().subtract(position()).multiply(1, 0, 1).normalize();
			inReach = facing.dot(toTarget) > 0.3;
		}
		if (inReach && doHurtTarget(level, target)) {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1), this);
			playSound(ModSounds.WATCHER_CLAW_HIT, 1.5F, 1.0F);
		} else {
			playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.5F);
		}
	}

	/**
	 * One tick of the Dread Pulse ground wave: draws the ring and hits players its front passed this tick. Players
	 * in the air (jumping over it), behind solid cover, or on another level escape it. Each is hit at most once.
	 */
	private void pulseWave(ServerLevel level, double radius, double previousRadius) {
		int points = (int) (radius * 10);
		for (int i = 0; i < points; i++) {
			double angle = Math.PI * 2 * i / points;
			double x = getX() + Math.cos(angle) * radius;
			double z = getZ() + Math.sin(angle) * radius;
			level.sendParticles(PULSE_WAVE, x, getY() + 0.15, z, 1, 0, 0, 0, 0);
			if (i % 4 == 0) {
				level.sendParticles(ParticleTypes.SMOKE, x, getY() + 0.2, z, 1, 0, 0.05, 0, 0.0);
			}
		}
		for (Player player : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(PULSE_MAX_RADIUS + 1, 3, PULSE_MAX_RADIUS + 1))) {
			if (player.isSpectator() || player.isCreative() || pulseHits.contains(player.getUUID())) {
				continue;
			}
			double horizontal = Math.sqrt(Mth.square(player.getX() - getX()) + Mth.square(player.getZ() - getZ()));
			if (horizontal > radius || horizontal <= previousRadius) {
				continue;
			}
			pulseHits.add(player.getUUID());
			if (!player.onGround() || Math.abs(player.getY() - getY()) > 2.5 || behindCover(level, player)) {
				continue;
			}
			player.hurtServer(level, damageSources().indirectMagic(this, this), 4.0F);
			player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80), this);
			player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 100), this);
			if (horizontal < 5) {
				player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 25), this);
			}
		}
	}

	/** True when solid blocks stand between it and the player at ground-wave height. */
	private boolean behindCover(ServerLevel level, Player player) {
		Vec3 from = position().add(0, 0.6, 0);
		Vec3 to = player.position().add(0, 0.6, 0);
		return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS;
	}

	/** Hands hit the ground: marks a crack zone under every player it can see in range. */
	private void graspSlam(ServerLevel level) {
		playSound(ModSounds.WATCHER_GRASP_SLAM, 2.5F, 1.0F);
		Vec3 hands = position().add(Vec3.directionFromRotation(0, getYRot()).scale(1.8));
		BlockState ground = level.getBlockState(BlockPos.containing(hands).below());
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), hands.x, hands.y + 0.1, hands.z, 30, 0.6, 0.1, 0.6, 0.15);
		}
		for (Player player : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(GRASP_RANGE, 6, GRASP_RANGE))) {
			if (!player.isSpectator() && !player.isCreative() && distanceTo(player) <= GRASP_RANGE && hasLineOfSight(player)) {
				graspZones.add(player.position());
				level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.WATCHER_GRASP_CRACK, SoundSource.HOSTILE, 1.5F, 1.0F);
			}
		}
	}

	/** The warning: the ground under each zone cracks and glows before the tendrils come. */
	private void graspCracksGlow(ServerLevel level) {
		for (Vec3 zone : graspZones) {
			for (int i = 0; i < 6; i++) {
				double angle = random.nextDouble() * Math.PI * 2;
				double r = random.nextDouble() * GRASP_ZONE_RADIUS;
				level.sendParticles(GLOWING_CRACK, zone.x + Math.cos(angle) * r, zone.y + 0.08, zone.z + Math.sin(angle) * r, 1, 0, 0, 0, 0);
			}
		}
	}

	/** Tendrils erupt from every zone and root whoever is still standing in one. */
	private void graspErupt(ServerLevel level) {
		for (Vec3 zone : graspZones) {
			level.playSound(null, zone.x, zone.y, zone.z, ModSounds.WATCHER_GRASP_ERUPT, SoundSource.HOSTILE, 2.0F, 1.0F);
			for (double h = 0; h < 2.4; h += 0.2) {
				for (int arm = 0; arm < 3; arm++) {
					double angle = h * 2.5 + arm * Math.PI * 2 / 3;
					double r = 0.7 - h * 0.2;
					level.sendParticles(TENDRIL, zone.x + Math.cos(angle) * r, zone.y + h, zone.z + Math.sin(angle) * r, 1, 0, 0, 0, 0);
				}
			}
			level.sendParticles(ParticleTypes.SQUID_INK, zone.x, zone.y + 0.3, zone.z, 8, 0.4, 0.2, 0.4, 0.05);
		}
		for (Player player : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(GRASP_RANGE + 2, 8, GRASP_RANGE + 2))) {
			if (player.isSpectator() || player.isCreative()) {
				continue;
			}
			for (Vec3 zone : graspZones) {
				double horizontal = Math.sqrt(Mth.square(player.getX() - zone.x) + Mth.square(player.getZ() - zone.z));
				if (horizontal <= GRASP_ZONE_RADIUS && Math.abs(player.getY() - zone.y) < 2.0) {
					player.hurtServer(level, damageSources().indirectMagic(this, this), 3.0F);
					if (player instanceof ServerPlayer serverPlayer) {
						GraspRoots.root(serverPlayer, ROOT_TICKS);
					}
					break;
				}
			}
		}
	}

	/**
	 * Finds a floor with room to stand (and no lava) at a random spot between minRadius and maxRadius around
	 * center that the filter accepts.
	 */
	private Vec3 findStandingSpot(ServerLevel level, Vec3 center, double minRadius, double maxRadius, int tries, Predicate<Vec3> filter) {
		for (int i = 0; i < tries; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double radius = minRadius + random.nextDouble() * (maxRadius - minRadius);
			int x = Mth.floor(center.x + Math.cos(angle) * radius);
			int z = Mth.floor(center.z + Math.sin(angle) * radius);
			for (int dy = 3; dy >= -4; dy--) {
				BlockPos feet = BlockPos.containing(x, center.y + dy, z);
				BlockPos below = feet.below();
				BlockState floor = level.getBlockState(below);
				if (!floor.isFaceSturdy(level, below, Direction.UP) || !level.getFluidState(feet).isEmpty()
						|| !level.getFluidState(below).isEmpty()) {
					continue;
				}
				Vec3 pos = new Vec3(x + 0.5, feet.getY(), z + 0.5);
				if (level.noCollision(this, getDimensions(getPose()).makeBoundingBox(pos)) && filter.test(pos)) {
					return pos;
				}
				break;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------------------------------------
	// Damage, sounds, particles, death
	// ---------------------------------------------------------------------------------------------

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (isIllusion()) {
			// A copy shatters at the first touch
			if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.getEntity() != null) {
				vanish(level);
			}
			return false;
		}
		boolean bypasses = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
		if (!isUnveiled() && !bypasses) {
			// It can't be killed before it unveils
			amount = Math.min(amount, getHealth() - 1.0F);
		}
		if (getAction() == STAGGERED) {
			amount *= 1.5F;
		}
		boolean hurt = super.hurtServer(level, source, amount);
		// Attacking it ends the game of watching
		if (hurt && getPhase() != HUNTING && source.getEntity() instanceof Player player && !player.isCreative()) {
			setTarget(player);
			startHunt();
		}
		if (hurt && !isUnveiled() && isAlive() && getHealth() <= getMaxHealth() * UNVEIL_HEALTH) {
			startUnveiling(level);
		}
		return hurt;
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		boolean hidden = getAction() == RIFT_OUT && actionTicks >= RIFT_HIDE_TICK;
		if ((hidden || getAction() == UNVEILING) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		return super.isInvulnerableTo(level, source);
	}

	@Override
	public boolean isPickable() {
		return getAction() != RIFT_OUT && super.isPickable();
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSqr) {
		// Stalkers keep their distance, so only despawn when really far away
		return distanceSqr > 64 * 64;
	}

	@Override
	protected int getBaseExperienceReward(ServerLevel level) {
		return 40;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		// Copies are silent: no rumble, no footsteps, no heartbeat
		return isIllusion() ? null : ModSounds.WATCHER_AMBIENT;
	}

	@Override
	public void playAmbientSound() {
		if (isIllusion()) {
			return;
		}
		super.playAmbientSound();
		// The rumble is layered with whispers
		playSound(ModSounds.WATCHER_WHISPER, 1.2F, 0.9F + random.nextFloat() * 0.2F);
	}

	@Override
	public int getAmbientSoundInterval() {
		return 200;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.WATCHER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.WATCHER_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 2.0F;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		if (isIllusion()) {
			return;
		}
		playSound(ModSounds.WATCHER_STEP, 1.5F, 0.9F + random.nextFloat() * 0.2F);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (level().isClientSide() && !isDeadOrDying() && getAction() != RIFT_OUT) {
			spawnAmbientParticles();
		}
	}

	/** Sparse black ash, red embers, and tiny purple motes that vanish before they reach the ground. */
	private void spawnAmbientParticles() {
		double x = getRandomX(0.6);
		double y = getY() + random.nextDouble() * getBbHeight();
		double z = getRandomZ(0.6);
		if (random.nextFloat() < 0.25F) {
			level().addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0.01, 0);
		}
		// Copies give off smoke but no embers
		if (random.nextFloat() < 0.12F && !isIllusion()) {
			level().addParticle(new DustParticleOptions(0xFF4A12, 0.5F), x, y, z, 0, 0.02, 0);
		}
		if (random.nextFloat() < 0.08F) {
			level().addParticle(ParticleTypes.REVERSE_PORTAL, x, y, z, 0, -0.02, 0);
		}
	}

	/** Freezes, its eye goes dark, then it breaks into obsidian shards that sink into the ground. */
	@Override
	protected void tickDeath() {
		deathTime++;
		if (level().isClientSide()) {
			if (deathTime > 16) {
				BlockParticleOption shard = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OBSIDIAN.defaultBlockState());
				for (int i = 0; i < 3; i++) {
					level().addParticle(shard, getRandomX(0.6), getY() + random.nextDouble() * getBbHeight() * 0.8, getRandomZ(0.6), 0, -0.05, 0);
				}
				if (random.nextFloat() < 0.4F) {
					level().addParticle(ParticleTypes.REVERSE_PORTAL, getRandomX(0.6), getY() + 1.0, getRandomZ(0.6), 0, 0, 0);
				}
			}
		} else {
			if (deathTime == 16) {
				playSound(ModSounds.WATCHER_SHATTER, 2.0F, 0.8F);
			}
			if (deathTime >= DEATH_TICKS && !isRemoved()) {
				level().broadcastEntityEvent(this, (byte) 60);
				remove(RemovalReason.KILLED);
			}
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Saving and animation
	// ---------------------------------------------------------------------------------------------

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("Phase", getPhase());
		output.putFloat("HuntPressure", huntPressure);
		output.putBoolean("Unveiled", isUnveiled());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		setPhase(input.getIntOr("Phase", OBSERVING));
		huntPressure = input.getFloatOr("HuntPressure", 0.0F);
		if (input.getBooleanOr("Unveiled", false)) {
			setUnveilTicks(UNVEIL_LENGTH);
			bossEvent.setVisible(true);
		}
	}

	@Override
	public void setCustomName(Component name) {
		super.setCustomName(name);
		if (name != null) {
			bossEvent.setName(name);
		}
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<UnderworldWatcher>("main", 5, test -> {
			UnderworldWatcher watcher = test.animatable();
			if (watcher.isDeadOrDying()) {
				return test.setAndContinue(DEATH);
			}
			if (test.isMoving()) {
				return test.setAndContinue(watcher.isSprinting() ? SPRINT : WALK);
			}
			if (watcher.isWatched() && watcher.getPhase() != HUNTING) {
				return test.setAndContinue(WATCHED);
			}
			return test.setAndContinue(IDLE);
		}));
		controllers.add(new AnimationController<UnderworldWatcher>("action", 0, test -> PlayState.STOP)
				.triggerableAnim("claw", CLAW_ANIM)
				.triggerableAnim("rift_out", RIFT_OUT_ANIM)
				.triggerableAnim("rift_in", RIFT_IN_ANIM)
				.triggerableAnim("dread_pulse", DREAD_PULSE_ANIM)
				.triggerableAnim("grasp", GRASP_ANIM)
				.triggerableAnim("unveil", UNVEIL_ANIM)
				.triggerableAnim("false_sight", FALSE_SIGHT_ANIM)
				.triggerableAnim("world_rend", WORLD_REND_ANIM)
				.triggerableAnim("unblinking", UNBLINKING_ANIM)
				.triggerableAnim("unblinking_fire", UNBLINKING_FIRE_ANIM));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
