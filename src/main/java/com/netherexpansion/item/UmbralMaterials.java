package com.netherexpansion.item;

import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import com.netherexpansion.NetherExpansion;

/**
 * Umbral Netherite: Netherite gear upgraded with Umbral Shards. A step above Netherite (more durable, sharper,
 * tougher), with its real strength in its perks against the Underworld (see UmbralPerks).
 */
public final class UmbralMaterials {
	public static final TagKey<Item> REPAIRS_UMBRAL = TagKey.create(Registries.ITEM, NetherExpansion.id("repairs_umbral_netherite"));
	public static final ResourceKey<EquipmentAsset> ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, NetherExpansion.id("umbral_netherite"));

	/** Netherite is 2031 durability, 9.0 speed, +4 damage, 15 enchantability. */
	public static final ToolMaterial TOOLS = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2400, 9.5F, 5.0F, 18, REPAIRS_UMBRAL);

	/** Netherite is 37 durability, the same defense, 15 enchantability, 3 toughness, 0.1 knockback resistance. */
	public static final ArmorMaterial ARMOR = new ArmorMaterial(40,
			Map.of(ArmorType.BOOTS, 3, ArmorType.LEGGINGS, 6, ArmorType.CHESTPLATE, 8, ArmorType.HELMET, 3, ArmorType.BODY, 11),
			18, SoundEvents.ARMOR_EQUIP_NETHERITE, 3.5F, 0.15F, REPAIRS_UMBRAL, ASSET);

	/** Vanilla netherite spear timings: duration, damage multiplier, then the charge and dismount values. */
	public static final float[] SPEAR = {1.15F, 1.2F, 0.4F, 2.5F, 9.0F, 5.5F, 5.1F, 8.75F, 4.6F};

	private UmbralMaterials() {
	}
}
