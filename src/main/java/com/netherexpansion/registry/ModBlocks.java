package com.netherexpansion.registry;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

import com.netherexpansion.NetherExpansion;

/** Umbral decorative blocks, made from polished blackstone bricks and Umbral Shards. */
public final class ModBlocks {
	public static final Block UMBRAL_BRICKS = register("umbral_bricks", Block::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.POLISHED_BLACKSTONE_BRICKS));
	public static final Block UMBRAL_BRICK_STAIRS = register("umbral_brick_stairs",
			p -> new StairBlock(UMBRAL_BRICKS.defaultBlockState(), p) {
			}, BlockBehaviour.Properties.ofFullCopy(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS));
	public static final Block UMBRAL_BRICK_SLAB = register("umbral_brick_slab", SlabBlock::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB));
	public static final Block UMBRAL_BRICK_WALL = register("umbral_brick_wall", WallBlock::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.POLISHED_BLACKSTONE_BRICK_WALL));
	public static final Block CHISELED_UMBRAL_BRICKS = register("chiseled_umbral_bricks", Block::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.CHISELED_POLISHED_BLACKSTONE));
	/** A violet-flamed lantern, as bright as a soul lantern. */
	public static final Block UMBRAL_LANTERN = register("umbral_lantern", LanternBlock::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.SOUL_LANTERN));

	private ModBlocks() {
	}

	/** Registers a block and its block item under the same ID. */
	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, NetherExpansion.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		ModItems.register(name, p -> new BlockItem(block, p), p -> p.useBlockDescriptionPrefix());
		return block;
	}

	public static void init() {
		// Loads the class so the fields above register.
	}
}
