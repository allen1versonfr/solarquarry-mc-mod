package com.solarquarry.fluid;

import com.solarquarry.blockentity.QuarryBlockEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * Finds where a quarry's pumped fluid can go: fluid storages (Liquid Tanks first) touching the quarry directly or
 * reachable through connected {@link FluidPipeBlock}s. Nearest destinations come first.
 */
public final class FluidNetwork {
	public static final int MAX_PIPES = 512;
	private static volatile int version;

	private FluidNetwork() {
	}

	/** Called when a pipe or tank is placed or removed, so quarries rebuild their destination lists. */
	public static void markChanged() {
		version++;
	}

	public static int version() {
		return version;
	}

	/** A destination: the storage block and the side it is reached from. */
	public record Target(BlockPos pos, Direction side) {
		public Storage<FluidVariant> find(ServerLevel level) {
			return level.isLoaded(this.pos) ? FluidStorage.SIDED.find(level, this.pos, this.side) : null;
		}
	}

	/**
	 * Destinations for a quarry at {@code origin}; {@code pipes} is set to whether any fluid pipe is attached (shown in
	 * the quarry screen so the player knows the second pipe is missing).
	 */
	public static List<Target> scan(ServerLevel level, BlockPos origin, boolean[] pipes) {
		List<Target> targets = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		seen.add(origin);
		queue.add(origin);
		boolean anyPipe = false;
		int visited = 0;
		while (!queue.isEmpty() && visited < MAX_PIPES) {
			BlockPos at = queue.poll();
			visited++;
			for (Direction dir : Direction.values()) {
				BlockPos next = at.relative(dir);
				if (!seen.add(next) || !level.isLoaded(next)) {
					continue;
				}

				if (level.getBlockState(next).getBlock() instanceof FluidPipeBlock) {
					anyPipe = true;
					queue.add(next);
					continue;
				}

				if (level.getBlockEntity(next) instanceof QuarryBlockEntity) {
					continue;
				}

				Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, next, dir.getOpposite());
				if (storage != null && storage.supportsInsertion()) {
					targets.add(new Target(next.immutable(), dir.getOpposite()));
				}
			}
		}

		// Tanks first, then other fluid storages, nearest first within each group (BFS order is already nearest first).
		List<Target> ordered = new ArrayList<>(targets.size());
		for (Target target : targets) {
			if (level.getBlockEntity(target.pos()) instanceof LiquidTankBlockEntity) {
				ordered.add(target);
			}
		}

		for (Target target : targets) {
			if (!(level.getBlockEntity(target.pos()) instanceof LiquidTankBlockEntity)) {
				ordered.add(target);
			}
		}

		if (pipes != null && pipes.length > 0) {
			pipes[0] = anyPipe;
		}

		return ordered;
	}
}
