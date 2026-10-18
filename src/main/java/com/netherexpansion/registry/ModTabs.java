package com.netherexpansion.registry;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import com.netherexpansion.NetherExpansion;

/** The Nether Expansion creative tab, holding everything the mod adds. */
public final class ModTabs {
	public static final CreativeModeTab NETHER_EXPANSION = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
			NetherExpansion.id("nether_expansion"),
			FabricCreativeModeTab.builder()
					.title(Component.translatable("itemGroup.netherexpansion"))
					.icon(() -> new ItemStack(ModItems.EYE_OF_THE_DEEP))
					.displayItems((parameters, output) -> ModItems.ALL.forEach(output::accept))
					.build());

	private ModTabs() {
	}

	public static void init() {
		// Loads the class so the tab registers.
	}
}
