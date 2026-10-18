package com.netherexpansion.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.core.particles.SimpleParticleType;

import com.netherexpansion.NetherExpansion;
import com.netherexpansion.registry.ModEntities;
import com.netherexpansion.registry.ModParticles;

public class NetherExpansionClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.UNDERWORLD_WATCHER, UnderworldWatcherRenderer::new);

		registerParticle(ModParticles.WATCHER_ASH, WatcherParticle.Kind.ASH);
		registerParticle(ModParticles.WATCHER_EMBER, WatcherParticle.Kind.EMBER);
		registerParticle(ModParticles.VOID_MOTE, WatcherParticle.Kind.VOID_MOTE);
		registerParticle(ModParticles.OBSIDIAN_SHARD, WatcherParticle.Kind.OBSIDIAN_SHARD);
		registerParticle(ModParticles.EYE_LIGHT, WatcherParticle.Kind.EYE_LIGHT);

		HudElementRegistry.addLast(NetherExpansion.id("execution_fade"), ExecutionFadeOverlay::extract);
		ClientTickEvents.END_CLIENT_TICK.register(ExecutionLock::tick);
	}

	private static void registerParticle(SimpleParticleType type, WatcherParticle.Kind kind) {
		ParticleProviderRegistry.getInstance().register(type, sprites -> (options, level, x, y, z, xd, yd, zd, random) ->
				new WatcherParticle(level, x, y, z, xd, yd, zd, sprites, kind));
	}
}
