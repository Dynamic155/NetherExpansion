package com.netherexpansion.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.netherexpansion.registry.ModEntities;

/**
 * Spawns Underworld Watchers for players exploring a Nether fortress: one at a time, at the far end of what
 * they can see when possible, so the first sighting is a figure standing at the end of a corridor.
 */
public final class WatcherSpawner {
	private static final Identifier FORTRESS = Identifier.withDefaultNamespace("fortress");
	private static final int CHECK_INTERVAL = 200;
	private static final float CHANCE_PER_CHECK = 0.25F;
	private static final int COOLDOWN_AFTER_SPAWN = 20 * 60 * 5;
	private static final double ALREADY_STALKED_RANGE = 96.0;

	/** Game time until which each player gets no new Watcher. */
	private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();

	private WatcherSpawner() {
	}

	public static void tick(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level.NETHER);
		if (level == null || level.getDifficulty() == Difficulty.PEACEFUL || level.getGameTime() % CHECK_INTERVAL != 0) {
			return;
		}
		RandomSource random = level.getRandom();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || player.isCreative() || random.nextFloat() >= CHANCE_PER_CHECK) {
				continue;
			}
			if (COOLDOWNS.getOrDefault(player.getUUID(), 0L) > level.getGameTime() || !inFortress(level, player.blockPosition())) {
				continue;
			}
			boolean alreadyStalked = !level.getEntitiesOfClass(UnderworldWatcher.class,
					player.getBoundingBox().inflate(ALREADY_STALKED_RANGE)).isEmpty();
			if (!alreadyStalked && spawnFor(level, player, random)) {
				COOLDOWNS.put(player.getUUID(), level.getGameTime() + COOLDOWN_AFTER_SPAWN);
			}
		}
	}

	private static boolean inFortress(ServerLevel level, BlockPos pos) {
		return level.structureManager().getStructureWithPieceAt(pos, holder -> holder.is(FORTRESS)).isValid();
	}

	private static boolean spawnFor(ServerLevel level, ServerPlayer player, RandomSource random) {
		UnderworldWatcher watcher = ModEntities.UNDERWORLD_WATCHER.create(level, EntitySpawnReason.EVENT);
		if (watcher == null) {
			return false;
		}
		for (int i = 0; i < 40; i++) {
			// The first tries look ahead of the player, where they will see it
			boolean inView = i < 28;
			float yaw = inView ? player.getYRot() + (random.nextFloat() - 0.5F) * 70.0F : random.nextFloat() * 360.0F;
			double distance = 18 + random.nextDouble() * 14;
			double x = player.getX() - Mth.sin(yaw * Mth.DEG_TO_RAD) * distance;
			double z = player.getZ() + Mth.cos(yaw * Mth.DEG_TO_RAD) * distance;
			Vec3 spot = findFloor(level, watcher, x, player.getY(), z);
			if (spot == null || level.getBrightness(LightLayer.BLOCK, BlockPos.containing(spot)) >= 11) {
				continue;
			}
			if (inView && !canSee(level, player, spot.add(0, 2.9, 0))) {
				continue;
			}
			float facePlayer = (float) Math.toDegrees(Math.atan2(-(player.getX() - spot.x), player.getZ() - spot.z));
			watcher.snapTo(spot.x, spot.y, spot.z, facePlayer, 0.0F);
			watcher.setYHeadRot(facePlayer);
			watcher.setYBodyRot(facePlayer);
			watcher.beginStalking(player);
			return level.addFreshEntity(watcher);
		}
		return false;
	}

	private static Vec3 findFloor(ServerLevel level, UnderworldWatcher watcher, double x, double y, double z) {
		BlockPos column = BlockPos.containing(x, y, z);
		for (int dy = 4; dy >= -6; dy--) {
			BlockPos feet = column.above(dy);
			BlockPos below = feet.below();
			if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) || !level.getFluidState(feet).isEmpty()) {
				continue;
			}
			Vec3 pos = new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
			return level.noCollision(watcher, watcher.getDimensions(watcher.getPose()).makeBoundingBox(pos)) ? pos : null;
		}
		return null;
	}

	private static boolean canSee(ServerLevel level, ServerPlayer player, Vec3 point) {
		ClipContext clip = new ClipContext(player.getEyePosition(), point, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player);
		return level.clip(clip).getType() == HitResult.Type.MISS;
	}
}
