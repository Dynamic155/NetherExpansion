package com.netherexpansion.registry;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

import com.netherexpansion.NetherExpansion;

public final class ModItems {
	public static final Item UNDERWORLD_WATCHER_SPAWN_EGG = register("underworld_watcher_spawn_egg",
			key -> new SpawnEggItem(new Item.Properties().setId(key).spawnEgg(ModEntities.UNDERWORLD_WATCHER)));

	// Vanilla's tab keys are private in 26.3, so look the tab up by its ID.
	private static final ResourceKey<CreativeModeTab> SPAWN_EGGS =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("spawn_eggs"));

	private ModItems() {
	}

	private static Item register(String name, java.util.function.Function<ResourceKey<Item>, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, NetherExpansion.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(key));
	}

	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(SPAWN_EGGS).register(output -> output.accept(UNDERWORLD_WATCHER_SPAWN_EGG));
	}
}
