package com.solarquarry.pipe;

import com.solarquarry.block.ItemPipeBlock;
import com.solarquarry.block.QuarryBlock;
import com.solarquarry.blockentity.ItemPipeBlockEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Finds a route for an item through a pipe network.
 * Breadth-first, so the nearest destination wins, except that destinations reached through a
 * whitelist filter that explicitly lists the item are preferred over unfiltered ones.
 */
public final class PipeRouter {
	public static final int MAX_NODES = 1024;

	private PipeRouter() {
	}

	private record Node(BlockPos pos, boolean matched, int parent, @Nullable Direction via) {
	}

	private record Key(BlockPos pos, boolean matched) {
	}

	/**
	 * @param start pipe the item is currently in (its own filter has already been checked)
	 * @param startMatched whether the item already passed a whitelist filter that lists it
	 * @return directions to leave each pipe through, ending with the face into the destination; null if nowhere accepts it
	 */
	public static @Nullable List<Direction> findRoute(Level level, BlockPos start, ItemStack stack, boolean startMatched) {
		if (stack.isEmpty()) {
			return null;
		}

		ItemVariant variant = ItemVariant.of(stack);
		long amount = stack.getCount();
		List<Node> nodes = new ArrayList<>();
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		Set<Key> visited = new HashSet<>();
		nodes.add(new Node(start, startMatched, -1, null));
		queue.add(0);
		visited.add(new Key(start, startMatched));

		int bestNode = -1;
		Direction bestDir = null;
		boolean bestMatched = false;

		while (!queue.isEmpty() && nodes.size() < MAX_NODES) {
			int index = queue.poll();
			Node node = nodes.get(index);
			if (bestMatched) {
				// Breadth-first order: nothing later can beat a preferred destination already found.
				break;
			}

			boolean canImprove = bestNode < 0 || node.matched();

			for (Direction dir : Direction.values()) {
				BlockPos next = node.pos().relative(dir);
				if (!level.isLoaded(next)) {
					continue;
				}

				BlockState state = level.getBlockState(next);
				if (state.getBlock() instanceof ItemPipeBlock) {
					BlockEntity be = level.getBlockEntity(next);
					if (!(be instanceof ItemPipeBlockEntity pipe)) {
						continue;
					}

					FilterResult result = pipe.testFilter(stack);
					if (result == FilterResult.BLOCKED) {
						continue;
					}

					boolean matched = node.matched() || result == FilterResult.PASS_MATCHED;
					Key key = new Key(next, matched);
					if (visited.add(key)) {
						nodes.add(new Node(next, matched, index, dir));
						queue.add(nodes.size() - 1);
					}

					continue;
				}

				if (state.getBlock() instanceof QuarryBlock || state.isAir()) {
					continue;
				}

				Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, next, dir.getOpposite());
				if (storage == null || !storage.supportsInsertion()) {
					continue;
				}

				if (canImprove && StorageUtil.simulateInsert(storage, variant, amount, null) > 0) {
					if (bestNode < 0 || node.matched() && !bestMatched) {
						bestNode = index;
						bestDir = dir;
						bestMatched = node.matched();
					}
				}
			}
		}

		if (bestNode < 0) {
			return null;
		}

		List<Direction> route = new ArrayList<>();
		route.add(bestDir);
		Node cursor = nodes.get(bestNode);
		while (cursor.parent() >= 0) {
			route.add(cursor.via());
			cursor = nodes.get(cursor.parent());
		}

		Collections.reverse(route);
		return route;
	}
}
