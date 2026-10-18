package com.netherexpansion.item;

import java.util.Comparator;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

import com.netherexpansion.NetherExpansion;
import com.netherexpansion.registry.ModItems;
import com.netherexpansion.registry.ModSounds;

/**
 * The Watcher Sigil: carried anywhere in the inventory, it warns its holder when a dangerous mob (the
 * {@code netherexpansion:sigil_threats} entity tag) closes in on them, naming it and its direction. It then goes on
 * a 30 second cooldown, shown on the item, so the warnings never become constant.
 */
public final class SigilWarnings {
	public static final TagKey<EntityType<?>> THREATS = TagKey.create(Registries.ENTITY_TYPE, NetherExpansion.id("sigil_threats"));

	private static final int CHECK_INTERVAL = 10;
	private static final double SCAN_RANGE = 24.0;
	/** A threat counts as approaching when it targets the player, or once it is this close. */
	private static final double NEAR = 16.0;
	private static final int COOLDOWN = 600;
	private static final String[] DIRECTIONS = {"south", "southwest", "west", "northwest", "north", "northeast", "east", "southeast"};

	private SigilWarnings() {
	}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_INTERVAL != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			ItemStack sigil = findSigil(player);
			if (sigil.isEmpty() || player.getCooldowns().isOnCooldown(sigil)) {
				continue;
			}
			Mob threat = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(SCAN_RANGE),
							mob -> mob.isAlive() && mob.getType().builtInRegistryHolder().is(THREATS) && (mob.getTarget() == player || mob.distanceTo(player) <= NEAR))
					.stream()
					.min(Comparator.comparingDouble(mob -> mob.distanceToSqr(player)))
					.orElse(null);
			if (threat != null) {
				warn(player, threat);
				player.getCooldowns().addCooldown(sigil, COOLDOWN);
			}
		}
	}

	private static ItemStack findSigil(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(ModItems.WATCHER_SIGIL)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	private static void warn(ServerPlayer player, Mob threat) {
		double dx = threat.getX() - player.getX();
		double dz = threat.getZ() - player.getZ();
		// Minecraft yaw: 0 is south, 90 is west
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		String direction = DIRECTIONS[Math.floorMod(Math.round(yaw / 45.0F), 8)];
		int distance = Mth.floor(Math.sqrt(dx * dx + dz * dz));
		player.sendOverlayMessage(Component.translatable("message.netherexpansion.sigil_warning", threat.getDisplayName(),
				Component.translatable("direction.netherexpansion." + direction), distance).withStyle(ChatFormatting.GOLD));
		// Only the holder hears the sigil
		player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSounds.SIGIL_WARNING),
				SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), 1.0F, 1.0F, player.getRandom().nextLong()));
	}
}
