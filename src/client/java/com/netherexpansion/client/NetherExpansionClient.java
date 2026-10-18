package com.netherexpansion.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import com.netherexpansion.registry.ModEntities;

public class NetherExpansionClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.UNDERWORLD_WATCHER, UnderworldWatcherRenderer::new);
	}
}
