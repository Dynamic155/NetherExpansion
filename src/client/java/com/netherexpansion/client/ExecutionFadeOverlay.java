package com.netherexpansion.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import com.netherexpansion.entity.UnderworldWatcher;

/**
 * The execution's fade: while a Watcher holds the local player by the neck, the screen darkens to black. It
 * stays black while the player dies, so the death screen comes up out of the dark.
 */
public final class ExecutionFadeOverlay {
	private static boolean executed;

	private ExecutionFadeOverlay() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		Player player = minecraft.player;
		if (player == null || minecraft.level == null) {
			executed = false;
			return;
		}
		if (player.isDeadOrDying()) {
			if (executed) {
				fill(graphics, 1.0F);
			}
			return;
		}
		float fade = 0.0F;
		for (UnderworldWatcher watcher : minecraft.level.getEntitiesOfClass(UnderworldWatcher.class, player.getBoundingBox().inflate(6))) {
			if (watcher.getHeldPlayerId() == player.getId()) {
				float progress = (float) watcher.getExecuteTicks() / UnderworldWatcher.EXECUTE_HOLD_LENGTH;
				fade = Mth.clamp((progress - 0.25F) / 0.7F, 0.0F, 1.0F);
			}
		}
		executed = fade > 0.85F;
		if (fade > 0.0F) {
			fill(graphics, fade * fade * (3.0F - 2.0F * fade));
		}
	}

	private static void fill(GuiGraphicsExtractor graphics, float opacity) {
		int alpha = Mth.clamp((int) (opacity * 255.0F), 0, 255);
		graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24);
	}
}
