package com.solarquarry.construction;

import com.solarquarry.SolarQuarry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * Blocks that self-building machines place for their structures. They have no item and drop nothing: the machine
 * builds them from energy and removes them when it is broken, so they can never be farmed or duplicated. Breaking
 * one by hand only makes its machine rebuild it.
 */
public final class ConstructionBlocks {
	/** Steel girder with a hazard band: masts, posts and farm frames. */
	public static final Block INDUSTRIAL_FRAME = register("industrial_frame", Block::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.METAL).strength(4.0F, 8.0F).sound(SoundType.NETHERITE_BLOCK).noLootTable());
	/** Reinforced glass panel: keeps water in (Kelp Farm) and closes windows (Mechanic's house). */
	public static final Block INDUSTRIAL_GLASS = register("industrial_glass", TransparentBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.NONE).strength(2.0F, 8.0F).sound(SoundType.GLASS).noOcclusion().noLootTable()
		.isValidSpawn((s, l, p, t) -> false).isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false));

	private ConstructionBlocks() {
	}

	/** True for the blocks only machines place (safe for a machine to remove when it is broken). */
	public static boolean isMachineBuilt(BlockState state) {
		return state.is(INDUSTRIAL_FRAME) || state.is(INDUSTRIAL_GLASS);
	}

	private static Block register(String name, java.util.function.Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, SolarQuarry.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	public static void init() {
	}
}
