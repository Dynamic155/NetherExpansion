package com.netherexpansion.registry;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import com.netherexpansion.NetherExpansion;
import com.netherexpansion.entity.UnderworldWatcher;

public final class ModEntities {
	public static final ResourceKey<EntityType<?>> UNDERWORLD_WATCHER_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, NetherExpansion.id("underworld_watcher"));

	public static final EntityType<UnderworldWatcher> UNDERWORLD_WATCHER = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			UNDERWORLD_WATCHER_KEY,
			EntityType.Builder.of(UnderworldWatcher::new, MobCategory.MONSTER)
					.sized(0.9F, 3.2F)
					.eyeHeight(2.95F)
					.fireImmune()
					.clientTrackingRange(10)
					.build(UNDERWORLD_WATCHER_KEY));

	private ModEntities() {
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(UNDERWORLD_WATCHER, UnderworldWatcher.createAttributes());
	}
}
