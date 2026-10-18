package com.netherexpansion.entity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import com.netherexpansion.NetherExpansion;

/**
 * Players held by Grasp of the Deep's tendrils. Rooting zeroes movement speed and jump strength with transient
 * attribute modifiers (they sync to the client and are never saved), and a server tick releases them.
 */
public final class GraspRoots {
	private static final Identifier ROOT = NetherExpansion.id("grasp_of_the_deep");
	private static final AttributeModifier HOLD = new AttributeModifier(ROOT, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	private static final DustParticleOptions TENDRIL = new DustParticleOptions(0x1A0B26, 1.2F);

	/** Server tick at which each rooted player is released. */
	private static final Map<UUID, Integer> ROOTED = new HashMap<>();

	private GraspRoots() {
	}

	public static void root(ServerPlayer player, int ticks) {
		apply(player, Attributes.MOVEMENT_SPEED);
		apply(player, Attributes.JUMP_STRENGTH);
		ROOTED.put(player.getUUID(), player.level().getServer().getTickCount() + ticks);
	}

	public static void tick(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Integer>> entries = ROOTED.entrySet().iterator();
		while (entries.hasNext()) {
			Map.Entry<UUID, Integer> entry = entries.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				entries.remove();
			} else if (server.getTickCount() >= entry.getValue() || !player.isAlive()) {
				release(player, Attributes.MOVEMENT_SPEED);
				release(player, Attributes.JUMP_STRENGTH);
				entries.remove();
			} else if (server.getTickCount() % 3 == 0) {
				player.level().sendParticles(TENDRIL, player.getX(), player.getY() + 0.4, player.getZ(), 4, 0.3, 0.4, 0.3, 0.0);
			}
		}
	}

	private static void apply(ServerPlayer player, Holder<Attribute> attribute) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null) {
			instance.addOrUpdateTransientModifier(HOLD);
		}
	}

	private static void release(ServerPlayer player, Holder<Attribute> attribute) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null) {
			instance.removeModifier(ROOT);
		}
	}
}
