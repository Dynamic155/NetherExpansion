package com.netherexpansion;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;

import com.netherexpansion.entity.Execution;
import com.netherexpansion.entity.GraspRoots;
import com.netherexpansion.entity.WatcherSpawner;
import com.netherexpansion.registry.ModEntities;
import com.netherexpansion.registry.ModItems;
import com.netherexpansion.registry.ModParticles;
import com.netherexpansion.registry.ModSounds;

public class NetherExpansion implements ModInitializer {
	public static final String MOD_ID = "netherexpansion";

	@Override
	public void onInitialize() {
		ModSounds.init();
		ModParticles.init();
		ModEntities.init();
		ModItems.init();
		ServerTickEvents.END_SERVER_TICK.register(WatcherSpawner::tick);
		ServerTickEvents.END_SERVER_TICK.register(GraspRoots::tick);
		Execution.init();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
