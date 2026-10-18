package com.netherexpansion.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import com.netherexpansion.registry.ModParticles;
import com.netherexpansion.registry.ModSounds;

/**
 * Eye of the Deep: the Watcher's core, its guaranteed drop, and the key to the Underworld. Until the way down
 * exists, using it shows that it pulls toward something beneath the Nether.
 */
public class EyeOfTheDeepItem extends Item {
	public EyeOfTheDeepItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level instanceof ServerLevel serverLevel) {
			boolean nether = level.dimension() == Level.NETHER;
			// Motes sink from the eye toward the ground: it pulls downward
			serverLevel.sendParticles(ModParticles.VOID_MOTE, player.getX(), player.getY() + 1.2, player.getZ(), 0, 0, -1, 0, 0.3);
			for (int i = 0; i < 12; i++) {
				serverLevel.sendParticles(ModParticles.VOID_MOTE, player.getRandomX(0.8), player.getY() + 1.4, player.getRandomZ(0.8),
						0, 0, -1, 0, 0.25);
			}
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.WATCHER_HEARTBEAT, SoundSource.PLAYERS, 0.8F, 1.2F);
			String key = nether ? "item.netherexpansion.eye_of_the_deep.pull_nether" : "item.netherexpansion.eye_of_the_deep.pull";
			player.sendOverlayMessage(Component.translatable(key).withStyle(ChatFormatting.DARK_PURPLE));
			player.getCooldowns().addCooldown(player.getItemInHand(hand), 40);
		}
		return InteractionResult.SUCCESS;
	}
}
