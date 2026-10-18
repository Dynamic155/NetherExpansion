package com.netherexpansion.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;

import com.netherexpansion.NetherExpansion;
import com.netherexpansion.registry.ModItems;

/**
 * Umbral Netherite's perks against the Underworld (the {@code netherexpansion:underworld_mobs} entity tag):
 * <ul>
 * <li>Each armor piece takes 5% off damage from Underworld mobs (20% for the full set).</li>
 * <li>Umbral weapons deal 20% more melee damage to Underworld mobs.</li>
 * <li>The helmet keeps Darkness and Blindness away.</li>
 * </ul>
 * Applied from LivingEntityMixin.
 */
public final class UmbralPerks {
	public static final TagKey<EntityType<?>> UNDERWORLD_MOBS = TagKey.create(Registries.ENTITY_TYPE, NetherExpansion.id("underworld_mobs"));
	public static final TagKey<Item> UMBRAL_ARMOR = TagKey.create(Registries.ITEM, NetherExpansion.id("umbral_armor"));
	public static final TagKey<Item> UMBRAL_WEAPONS = TagKey.create(Registries.ITEM, NetherExpansion.id("umbral_weapons"));

	private static final float ARMOR_REDUCTION_PER_PIECE = 0.05F;
	private static final float WEAPON_BONUS = 0.2F;
	private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private UmbralPerks() {
	}

	public static float modifyDamage(LivingEntity victim, DamageSource source, float amount) {
		Entity attacker = source.getEntity();
		if (attacker != null && attacker.getType().builtInRegistryHolder().is(UNDERWORLD_MOBS)) {
			amount *= 1.0F - ARMOR_REDUCTION_PER_PIECE * umbralPieces(victim);
		}
		if (victim.getType().builtInRegistryHolder().is(UNDERWORLD_MOBS) && attacker instanceof LivingEntity living && source.getDirectEntity() == attacker
				&& living.getMainHandItem().is(UMBRAL_WEAPONS)) {
			amount *= 1.0F + WEAPON_BONUS;
		}
		return amount;
	}

	public static boolean blocksEffect(LivingEntity entity, MobEffectInstance effect) {
		return (effect.is(MobEffects.DARKNESS) || effect.is(MobEffects.BLINDNESS))
				&& entity.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.UMBRAL_NETHERITE_HELMET);
	}

	private static int umbralPieces(LivingEntity entity) {
		int pieces = 0;
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			if (entity.getItemBySlot(slot).is(UMBRAL_ARMOR)) {
				pieces++;
			}
		}
		return pieces;
	}
}
