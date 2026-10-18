package com.netherexpansion.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.ArmorType;

import com.netherexpansion.NetherExpansion;
import com.netherexpansion.item.EyeOfTheDeepItem;
import com.netherexpansion.item.UmbralMaterials;
import com.netherexpansion.item.VeilstepCharmItem;

public final class ModItems {
	/** Everything this mod adds, in creative tab order. */
	public static final List<Item> ALL = new ArrayList<>();

	public static final Item UNDERWORLD_WATCHER_SPAWN_EGG = register("underworld_watcher_spawn_egg", SpawnEggItem::new,
			p -> p.spawnEgg(ModEntities.UNDERWORLD_WATCHER));

	// The Watcher's drops
	public static final Item EYE_OF_THE_DEEP = register("eye_of_the_deep", EyeOfTheDeepItem::new,
			p -> p.stacksTo(1).rarity(Rarity.EPIC).fireResistant().component(DataComponents.LORE, lore("eye_of_the_deep", 2)));
	public static final Item UMBRAL_SHARD = register("umbral_shard", Item::new,
			p -> p.rarity(Rarity.UNCOMMON).fireResistant().component(DataComponents.LORE, lore("umbral_shard", 1)));
	public static final Item WATCHER_SIGIL = register("watcher_sigil", Item::new,
			p -> p.stacksTo(1).rarity(Rarity.RARE).fireResistant().component(DataComponents.LORE, lore("watcher_sigil", 2)));

	// Made from Umbral Shards
	public static final Item VEILSTEP_CHARM = register("veilstep_charm", VeilstepCharmItem::new,
			p -> p.stacksTo(1).rarity(Rarity.RARE).fireResistant().component(DataComponents.LORE, lore("veilstep_charm", 1)));
	public static final Item UMBRAL_UPGRADE_SMITHING_TEMPLATE = register("umbral_upgrade_smithing_template",
			ModItems::umbralTemplate, p -> p.rarity(Rarity.UNCOMMON).fireResistant());

	// Umbral Netherite gear: tool damage and speed baselines match netherite; the material adds its bonus
	public static final Item UMBRAL_NETHERITE_SWORD = gear("sword", p -> p.sword(UmbralMaterials.TOOLS, 3.0F, -2.4F));
	public static final Item UMBRAL_NETHERITE_PICKAXE = gear("pickaxe", p -> p.pickaxe(UmbralMaterials.TOOLS, 1.0F, -2.8F));
	public static final Item UMBRAL_NETHERITE_AXE = gear("axe", p -> p.axe(UmbralMaterials.TOOLS, 5.0F, -3.0F));
	public static final Item UMBRAL_NETHERITE_SHOVEL = gear("shovel", p -> p.shovel(UmbralMaterials.TOOLS, 1.5F, -3.0F));
	public static final Item UMBRAL_NETHERITE_HOE = gear("hoe", p -> p.hoe(UmbralMaterials.TOOLS, -4.0F, 0.0F));
	public static final Item UMBRAL_NETHERITE_SPEAR = gear("spear", p -> {
		float[] s = UmbralMaterials.SPEAR;
		return p.spear(UmbralMaterials.TOOLS, s[0], s[1], s[2], s[3], s[4], s[5], s[6], s[7], s[8]);
	});
	public static final Item UMBRAL_NETHERITE_HELMET = gear("helmet", p -> p.humanoidArmor(UmbralMaterials.ARMOR, ArmorType.HELMET));
	public static final Item UMBRAL_NETHERITE_CHESTPLATE = gear("chestplate", p -> p.humanoidArmor(UmbralMaterials.ARMOR, ArmorType.CHESTPLATE));
	public static final Item UMBRAL_NETHERITE_LEGGINGS = gear("leggings", p -> p.humanoidArmor(UmbralMaterials.ARMOR, ArmorType.LEGGINGS));
	public static final Item UMBRAL_NETHERITE_BOOTS = gear("boots", p -> p.humanoidArmor(UmbralMaterials.ARMOR, ArmorType.BOOTS));

	// Vanilla's tab keys are private in 26.3, so look the tab up by its ID.
	private static final ResourceKey<CreativeModeTab> SPAWN_EGGS =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("spawn_eggs"));

	private ModItems() {
	}

	private static Item gear(String piece, UnaryOperator<Item.Properties> stats) {
		return register("umbral_netherite_" + piece, Item::new, p -> stats.apply(p).fireResistant());
	}

	public static Item register(String name, Function<Item.Properties, Item> factory, UnaryOperator<Item.Properties> properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, NetherExpansion.id(name));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.apply(new Item.Properties().setId(key))));
		ALL.add(item);
		return item;
	}

	/** Gray, non-italic tooltip lines from item.netherexpansion.(name).lore1, lore2, ... */
	private static ItemLore lore(String name, int lines) {
		List<Component> text = new ArrayList<>();
		for (int i = 1; i <= lines; i++) {
			text.add(Component.translatable("item.netherexpansion." + name + ".lore" + i)
					.withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY)));
		}
		return new ItemLore(text);
	}

	/** Like the netherite template, but turns netherite gear into Umbral Netherite with an Umbral Shard. */
	private static Item umbralTemplate(Item.Properties properties) {
		String prefix = "item.netherexpansion.smithing_template.umbral_upgrade.";
		return new SmithingTemplateItem(
				Component.translatable(prefix + "applies_to").withStyle(ChatFormatting.BLUE),
				Component.translatable(prefix + "ingredients").withStyle(ChatFormatting.BLUE),
				Component.translatable(prefix + "base_slot_description"),
				Component.translatable(prefix + "additions_slot_description"),
				List.of("helmet", "sword", "chestplate", "pickaxe", "leggings", "axe", "boots", "hoe", "shovel", "spear").stream()
						.map(slot -> Identifier.withDefaultNamespace("container/slot/" + slot)).toList(),
				List.of(Identifier.withDefaultNamespace("container/slot/amethyst_shard")),
				properties);
	}

	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(SPAWN_EGGS).register(output -> output.accept(UNDERWORLD_WATCHER_SPAWN_EGG));
	}
}
