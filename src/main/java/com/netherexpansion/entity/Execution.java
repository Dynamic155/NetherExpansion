package com.netherexpansion.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;

import com.netherexpansion.NetherExpansion;

/**
 * The Underworld Watcher's execution. When any of its attacks would kill a player, the death is cancelled: the
 * player is kept at half a heart, pinned in place (no walking, jumping or falling) and protected from all other
 * damage while the Watcher walks up, lifts them by the neck and finishes them with its own damage type.
 *
 * <p>A totem of undying is respected: a player holding one dies (and is saved) the normal way.
 */
public final class Execution {
	public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE, NetherExpansion.id("execution"));

	private static final Identifier HOLD_ID = NetherExpansion.id("execution_hold");
	private static final AttributeModifier HOLD = new AttributeModifier(HOLD_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

	/** Players currently held: they cannot take damage or die until released or executed. */
	private static final Set<UUID> HELD = new HashSet<>();
	/** Players receiving the final blow, which must go through. */
	private static final Set<UUID> FINISHING = new HashSet<>();

	private Execution() {
	}

	public static void init() {
		ServerLivingEntityEvents.ALLOW_DEATH.register(Execution::allowDeath);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Execution::allowDamage);
	}

	private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || FINISHING.contains(player.getUUID())) {
			return true;
		}
		if (HELD.contains(player.getUUID())) {
			player.setHealth(1.0F);
			return false;
		}
		if (source.getEntity() instanceof UnderworldWatcher watcher && watcher.canExecute() && !holdsTotem(player)) {
			player.setHealth(1.0F);
			hold(player);
			watcher.beginExecution(player);
			return false;
		}
		return true;
	}

	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		return !(entity instanceof ServerPlayer player) || !HELD.contains(player.getUUID()) || FINISHING.contains(player.getUUID());
	}

	private static boolean holdsTotem(ServerPlayer player) {
		return player.getMainHandItem().is(Items.TOTEM_OF_UNDYING) || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
	}

	/** Pins the player: no walking, no jumping, no falling. */
	public static void hold(ServerPlayer player) {
		HELD.add(player.getUUID());
		modify(player, Attributes.MOVEMENT_SPEED, true);
		modify(player, Attributes.JUMP_STRENGTH, true);
		modify(player, Attributes.GRAVITY, true);
	}

	/** Lets the player go (they survive, at whatever health they have). */
	public static void release(ServerPlayer player) {
		HELD.remove(player.getUUID());
		modify(player, Attributes.MOVEMENT_SPEED, false);
		modify(player, Attributes.JUMP_STRENGTH, false);
		modify(player, Attributes.GRAVITY, false);
	}

	/** True while a Watcher holds this player. */
	public static boolean isHeld(ServerPlayer player) {
		return HELD.contains(player.getUUID());
	}

	/** The final blow: releases the player and kills them with the execution damage type. */
	public static void kill(ServerPlayer player, UnderworldWatcher watcher) {
		release(player);
		Holder<DamageType> type = player.level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE_TYPE);
		FINISHING.add(player.getUUID());
		try {
			player.hurtServer(player.level(), new DamageSource(type, watcher), 1000.0F);
		} finally {
			FINISHING.remove(player.getUUID());
		}
	}

	private static void modify(ServerPlayer player, Holder<Attribute> attribute, boolean apply) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (apply) {
			instance.addOrUpdateTransientModifier(HOLD);
		} else {
			instance.removeModifier(HOLD_ID);
		}
	}
}
