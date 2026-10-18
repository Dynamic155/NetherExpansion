package com.netherexpansion.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.netherexpansion.entity.UnderworldWatcher;

/**
 * While a Watcher holds the local player for its execution, the player cannot move or look away: each tick the
 * camera is turned to stare into its eye, and the mixins drop mouse turning and movement input.
 */
public final class ExecutionLock {
	private static UnderworldWatcher holder;

	private ExecutionLock() {
	}

	/** True while the local player is being executed. */
	public static boolean isHeld() {
		return holder != null;
	}

	/** The Watcher holding the local player, or null. */
	public static UnderworldWatcher holder() {
		return holder;
	}

	public static void tick(Minecraft minecraft) {
		Player player = minecraft.player;
		holder = null;
		if (player == null || minecraft.level == null || !player.isAlive()) {
			return;
		}
		for (UnderworldWatcher watcher : minecraft.level.getEntitiesOfClass(UnderworldWatcher.class, player.getBoundingBox().inflate(48))) {
			if (watcher.getHeldPlayerId() == player.getId()) {
				holder = watcher;
				break;
			}
		}
		if (holder != null) {
			faceHolder(player, holder);
		}
	}

	private static void faceHolder(Player player, UnderworldWatcher watcher) {
		Vec3 look = watcher.getEyePosition().subtract(player.getEyePosition());
		float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90.0F;
		float pitch = (float) -(Mth.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)) * Mth.RAD_TO_DEG);
		player.setYRot(yaw);
		player.setXRot(pitch);
		player.yRotO = yaw;
		player.xRotO = pitch;
		player.setYHeadRot(yaw);
		player.setYBodyRot(yaw);
	}
}
