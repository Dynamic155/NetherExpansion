package com.netherexpansion.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.netherexpansion.entity.Execution;
import com.netherexpansion.registry.ModParticles;
import com.netherexpansion.registry.ModSounds;

/**
 * Veilstep Charm: blinks the player up to eight blocks in the direction they look, stopping short of walls,
 * with a three second cooldown. No fall damage carries over from before the blink.
 */
public class VeilstepCharmItem extends Item {
	private static final double RANGE = 8.0;
	private static final int COOLDOWN = 60;

	public VeilstepCharmItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		if (Execution.isHeld(serverPlayer)) {
			return InteractionResult.FAIL;
		}
		Vec3 destination = findDestination(serverLevel, player);
		if (destination == null) {
			return InteractionResult.FAIL;
		}
		Vec3 from = player.position();
		serverLevel.sendParticles(ModParticles.VOID_MOTE, from.x, from.y + 1.0, from.z, 16, 0.3, 0.6, 0.3, 0.02);
		serverLevel.sendParticles(ModParticles.WATCHER_ASH, from.x, from.y + 1.0, from.z, 8, 0.3, 0.6, 0.3, 0.01);
		serverPlayer.connection.teleport(destination.x, destination.y, destination.z, player.getYRot(), player.getXRot());
		player.resetFallDistance();
		serverLevel.sendParticles(ModParticles.EYE_LIGHT, destination.x, destination.y + 1.0, destination.z, 12, 0.3, 0.6, 0.3, 0.05);
		serverLevel.playSound(null, from.x, from.y, from.z, ModSounds.WATCHER_RIFT_OUT, SoundSource.PLAYERS, 0.8F, 1.7F);
		serverLevel.playSound(null, destination.x, destination.y, destination.z, ModSounds.WATCHER_RIFT_IN, SoundSource.PLAYERS, 0.8F, 1.8F);
		player.getCooldowns().addCooldown(stack, COOLDOWN);
		return InteractionResult.SUCCESS;
	}

	/** The farthest point along the look direction (up to RANGE) where the player fits, or null if under a block. */
	private static Vec3 findDestination(ServerLevel level, Player player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		HitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		double reach = hit.getType() == HitResult.Type.MISS ? RANGE : hit.getLocation().distanceTo(eye) - 0.6;
		Vec3 eyeOffset = new Vec3(0, player.getEyeHeight(), 0);
		for (double d = reach; d >= 1.0; d -= 0.5) {
			Vec3 feet = eye.add(look.scale(d)).subtract(eyeOffset);
			if (level.noCollision(player, player.getBoundingBox().move(feet.subtract(player.position())))) {
				return feet;
			}
		}
		return null;
	}
}
