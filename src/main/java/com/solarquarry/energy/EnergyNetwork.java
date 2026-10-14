package com.solarquarry.energy;

import com.solarquarry.block.EnergyCableBlock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Discovers energy endpoints reachable from a node through energy cables.
 * Results are cached by each node and invalidated through a global topology version
 * that changes whenever a cable or energy machine is placed or removed.
 */
public final class EnergyNetwork {
	public static final int MAX_CABLES = 4096;
	private static final AtomicInteger TOPOLOGY_VERSION = new AtomicInteger();

	private EnergyNetwork() {
	}

	public static int topologyVersion() {
		return TOPOLOGY_VERSION.get();
	}

	public static void markTopologyChanged() {
		TOPOLOGY_VERSION.incrementAndGet();
	}

	/** Breadth-first search through loaded cables; returns positions of reachable energy nodes (excluding origin). */
	public static List<BlockPos> discoverEndpoints(Level level, BlockPos origin) {
		List<BlockPos> endpoints = new ArrayList<>();
		Set<BlockPos> visited = new HashSet<>();
		Set<BlockPos> endpointSet = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		visited.add(origin);

		for (Direction dir : Direction.values()) {
			visit(level, origin.relative(dir), visited, endpointSet, endpoints, queue);
		}

		int cables = 0;
		while (!queue.isEmpty() && cables < MAX_CABLES) {
			BlockPos cable = queue.poll();
			cables++;
			for (Direction dir : Direction.values()) {
				visit(level, cable.relative(dir), visited, endpointSet, endpoints, queue);
			}
		}

		return endpoints;
	}

	private static void visit(Level level, BlockPos pos, Set<BlockPos> visited, Set<BlockPos> endpointSet, List<BlockPos> endpoints, ArrayDeque<BlockPos> queue) {
		if (!visited.add(pos) || !level.isLoaded(pos)) {
			return;
		}

		if (level.getBlockState(pos).getBlock() instanceof EnergyCableBlock) {
			queue.add(pos);
			return;
		}

		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof EnergyNode && endpointSet.add(pos)) {
			endpoints.add(pos.immutable());
		}
	}

	/** Resolves a cached endpoint position to a live node, or null if it is gone or unloaded. */
	public static EnergyNode resolve(Level level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return null;
		}

		BlockEntity be = level.getBlockEntity(pos);
		return be instanceof EnergyNode node && !be.isRemoved() ? node : null;
	}

	/**
	 * Moves up to {@code maxAmount} from {@code source} into receivers having the given role.
	 * @return the amount actually moved
	 */
	public static long distribute(Level level, EnergyNode source, List<BlockPos> endpoints, EnergyNode.Role targetRole, long maxAmount, int rotation) {
		if (maxAmount <= 0 || endpoints.isEmpty()) {
			return 0;
		}

		List<EnergyNode> receivers = new ArrayList<>();
		for (BlockPos pos : endpoints) {
			EnergyNode node = resolve(level, pos);
			if (node != null && node != source && node.energyRole() == targetRole && node.insertEnergy(Long.MAX_VALUE, true) > 0) {
				receivers.add(node);
			}
		}

		if (receivers.isEmpty()) {
			return 0;
		}

		long remaining = Math.min(maxAmount, source.extractEnergy(maxAmount, true));
		long moved = 0;
		int count = receivers.size();
		int start = Math.floorMod(rotation, count);

		// Two passes: an even share first, then whatever is left to anyone with room.
		for (int pass = 0; pass < 2 && remaining > 0; pass++) {
			int left = count;
			for (int i = 0; i < count && remaining > 0; i++) {
				EnergyNode receiver = receivers.get((start + i) % count);
				long share = pass == 0 ? Math.max(1, remaining / left) : remaining;
				left--;
				long accepted = receiver.insertEnergy(share, true);
				if (accepted <= 0) {
					continue;
				}

				long extracted = source.extractEnergy(accepted, false);
				long inserted = receiver.insertEnergy(extracted, false);
				if (inserted < extracted) {
					// Should not happen after a successful simulation; return the difference to the source.
					source.restoreEnergy(extracted - inserted);
				}

				moved += inserted;
				remaining -= inserted;
			}
		}

		return moved;
	}
}
