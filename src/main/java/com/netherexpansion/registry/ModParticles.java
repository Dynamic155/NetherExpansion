package com.netherexpansion.registry;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

import com.netherexpansion.NetherExpansion;

/** The Underworld Watcher's particles. Sprites: textures/particle; behavior: client WatcherParticle. */
public final class ModParticles {
	/** Black ash flakes that drift and tumble. */
	public static final SimpleParticleType WATCHER_ASH = register("watcher_ash");
	/** Glowing red embers that rise, flicker and burn out. */
	public static final SimpleParticleType WATCHER_EMBER = register("watcher_ember");
	/** Tiny violet motes that sink and vanish before they reach the ground. */
	public static final SimpleParticleType VOID_MOTE = register("void_mote");
	/** Spinning obsidian shards that fall and sink into the ground. */
	public static final SimpleParticleType OBSIDIAN_SHARD = register("obsidian_shard");
	/** Bright white-violet sparks of eye light. */
	public static final SimpleParticleType EYE_LIGHT = register("eye_light");

	private ModParticles() {
	}

	private static SimpleParticleType register(String name) {
		// Always visible, like vanilla's important particles, so the Watcher's tells aren't culled away
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, NetherExpansion.id(name), FabricParticleTypes.simple(true));
	}

	public static void init() {
		// Loads the class so the fields above register.
	}
}
