package com.solarquarry.construction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * What a machine supplies to its {@link ConstructionSite}: how it pays for each step and how it shows the work.
 * The site owns the lifecycle; the host owns everything machine-specific.
 */
public interface ConstructionHost {
	/** Takes the energy for one construction step; returns false (and takes nothing) if there is not enough. */
	boolean payForStep();

	/** Called after a block was placed ({@code pos} is null for steps that place no block, like the quarry's frame). */
	default void onStepDone(ServerLevel level, @Nullable BlockPos pos) {
	}

	/** Called once when the structure is complete. */
	default void onCompleted(ServerLevel level) {
	}

	/**
	 * Whether an existing block may be replaced by the structure. The default accepts air and plants, snow and other
	 * replaceable blocks without liquid; machines that build in water (kelp farm) also accept water.
	 */
	default boolean canReplace(BlockState existing) {
		return isClearable(existing);
	}

	/**
	 * Air, replaceable blocks (grass, snow layers) and wild plants (flowers, ferns, bushes, leaf litter, small
	 * mushrooms), never liquids. Saplings, crops and stems are someone's planting and count as obstructions.
	 */
	static boolean isClearable(BlockState state) {
		if (state.isAir()) {
			return true;
		}

		if (!state.getFluidState().isEmpty() || state.hasBlockEntity()) {
			return false;
		}

		if (state.canBeReplaced()) {
			return true;
		}

		var block = state.getBlock();
		return block instanceof net.minecraft.world.level.block.FlowerBlock || block instanceof net.minecraft.world.level.block.TallGrassBlock
			|| block instanceof net.minecraft.world.level.block.DoublePlantBlock || block instanceof net.minecraft.world.level.block.BushBlock
			|| block instanceof net.minecraft.world.level.block.DryVegetationBlock || block instanceof net.minecraft.world.level.block.FireflyBushBlock
			|| block instanceof net.minecraft.world.level.block.FlowerBedBlock || block instanceof net.minecraft.world.level.block.LeafLitterBlock
			|| block instanceof net.minecraft.world.level.block.MushroomBlock;
	}
}
