package com.solarquarry.pipe;

import com.solarquarry.block.ItemPipeBlock;
import com.solarquarry.block.QuarryBlock;
import com.solarquarry.blockentity.ItemPipeBlockEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 *
 * <p>Routing rules, in order (deterministic):
 * <ol>
 * <li>Destinations reached through an Allow filter that lists the item beat all others.</li>
 * <li>Higher priority wins. A path's priority is set by the last filter pipe it passes through (plain pipes keep it; default 0).</li>
 * <li>The nearest destination (fewest pipes) wins.</li>
 * <li>Remaining ties go to the first one found, scanning faces in the fixed order down, up, north, south, west, east.</li>
 * </ol>
 * The search is breadth-first, never revisits a pipe in the same state (no loops) and stops after {@link #MAX_NODES} states.
 * Results for items entering from machines are cached for one second and invalidated when pipes or filters change.
 */
public final class PipeRouter {
	public static final int MAX_NODES = 1024;
	private static final int CACHE_TICKS = 20;
	private static final int CACHE_SIZE = 256;
	private static int version;

	private static final Map<CacheKey, CachedRoute> CACHE = new LinkedHashMap<>(64, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<CacheKey, CachedRoute> eldest) {
			return this.size() > CACHE_SIZE;
		}
	};

	private record CacheKey(Level level, BlockPos start, ItemVariant variant, boolean matched) {
	}

	private record CachedRoute(List<Direction> route, long expires, int version) {
	}

	private record Node(BlockPos pos, boolean matched, int priority, int parent, @Nullable Direction via) {
	}

	private record Key(BlockPos pos, boolean matched, int priority) {
	}

	private PipeRouter() {
	}

	/** Called when pipes, filters or their neighbours change. */
	public static void invalidateCache() {
		version++;
		CACHE.clear();
	}

	/** Cached variant used when machines push items in (the hot path). The cached route is only a first guess: pipes re-check on arrival. */
	public static @Nullable List<Direction> findRouteCached(Level level, BlockPos start, ItemStack stack, boolean startMatched) {
		if (stack.isEmpty()) {
			return null;
		}

		CacheKey key = new CacheKey(level, start.immutable(), ItemVariant.of(stack), startMatched);
		long now = level.getGameTime();
		CachedRoute cached = CACHE.get(key);
		if (cached != null && cached.version == version && cached.expires >= now) {
			return cached.route;
		}

		List<Direction> route = findRoute(level, start, stack, startMatched);
		if (route != null) {
			CACHE.put(key, new CachedRoute(List.copyOf(route), now + CACHE_TICKS, version));
		}

		return route;
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
		int startPriority = level.getBlockEntity(start) instanceof ItemPipeBlockEntity startPipe ? startPipe.getPriority() : 0;
		nodes.add(new Node(start, startMatched, startPriority, -1, null));
		queue.add(0);
		visited.add(new Key(start, startMatched, startPriority));

		int bestNode = -1;
		Direction bestDir = null;
		boolean bestMatched = false;
		int bestPriority = Integer.MIN_VALUE;

		while (!queue.isEmpty() && nodes.size() < MAX_NODES) {
			int index = queue.poll();
			Node node = nodes.get(index);
			if (bestNode >= 0 && bestMatched && bestPriority >= ItemPipeBlockEntity.MAX_PRIORITY) {
				break; // nothing can beat a matched, maximum-priority destination found earlier (and nearer)
			}

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
					int priority = pipe.isFilter() ? pipe.getPriority() : node.priority();
					Key key = new Key(next, matched, priority);
					if (visited.add(key)) {
						nodes.add(new Node(next, matched, priority, index, dir));
						queue.add(nodes.size() - 1);
					}

					continue;
				}

				if (state.getBlock() instanceof QuarryBlock || state.isAir()) {
					continue;
				}

				if (!better(node.matched(), node.priority(), bestNode >= 0, bestMatched, bestPriority)) {
					continue;
				}

				Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, next, dir.getOpposite());
				if (storage == null || !storage.supportsInsertion()) {
					continue;
				}

				if (StorageUtil.simulateInsert(storage, variant, amount, null) > 0) {
					bestNode = index;
					bestDir = dir;
					bestMatched = node.matched();
					bestPriority = node.priority();
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

	/** Strictly better than the current best (ties keep the earlier, nearer one). */
	static boolean better(boolean matched, int priority, boolean haveBest, boolean bestMatched, int bestPriority) {
		if (!haveBest) {
			return true;
		}

		if (matched != bestMatched) {
			return matched;
		}

		return priority > bestPriority;
	}
}
