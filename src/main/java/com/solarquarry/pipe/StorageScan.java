package com.solarquarry.pipe;

import com.solarquarry.block.ItemPipeBlock;
import com.solarquarry.block.QuarryBlock;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Read-only survey of the inventories a machine delivers to: directly adjacent ones and those reachable through
 * its item-pipe network (bounded search). Used by the remote terminal's storage view.
 */
public final class StorageScan {
	public static final int MAX_PIPES = 1024;

	/** Totals across every inventory found. Slot counts are exact for vanilla containers and estimated otherwise. */
	public record Result(int inventories, long items, int usedSlots, int totalSlots, int pipes) {
		public int freePercent() {
			return this.totalSlots <= 0 ? 0 : (int) Math.round(100.0 * (this.totalSlots - this.usedSlots) / this.totalSlots);
		}

		public boolean lowSpace() {
			return this.inventories > 0 && this.freePercent() < 10;
		}
	}

	private StorageScan() {
	}

	public static Result scan(Level level, BlockPos machine) {
		Set<BlockPos> inventories = new LinkedHashSet<>();
		Set<BlockPos> visited = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		visited.add(machine);
		queue.add(machine);
		int pipes = 0;
		while (!queue.isEmpty() && pipes <= MAX_PIPES) {
			BlockPos pos = queue.poll();
			for (Direction dir : Direction.values()) {
				BlockPos next = pos.relative(dir);
				if (visited.contains(next) || !level.isLoaded(next)) {
					continue;
				}

				var state = level.getBlockState(next);
				if (state.getBlock() instanceof ItemPipeBlock) {
					visited.add(next.immutable());
					queue.add(next.immutable());
					pipes++;
				} else if (!(state.getBlock() instanceof QuarryBlock) && !state.isAir()
					&& ItemStorage.SIDED.find(level, next, dir.getOpposite()) != null) {
					inventories.add(next.immutable());
				}
			}
		}

		long items = 0;
		int used = 0;
		int total = 0;
		for (BlockPos pos : inventories) {
			long[] counts = slots(level, pos);
			items += counts[0];
			used += (int) counts[1];
			total += (int) counts[2];
		}

		return new Result(inventories.size(), items, used, total, pipes);
	}

	/** {items, usedSlots, totalSlots} of one inventory; all zero when it exposes no slots. */
	public static long[] slots(Level level, BlockPos pos) {
		long items = 0;
		long used = 0;
		long total = 0;
		BlockEntity be = level.isLoaded(pos) ? level.getBlockEntity(pos) : null;
		if (be instanceof Container container) {
			for (int i = 0; i < container.getContainerSize(); i++) {
				var stack = container.getItem(i);
				total++;
				if (!stack.isEmpty()) {
					used++;
					items += stack.getCount();
				}
			}

			return new long[] {items, used, total};
		}

		Storage<ItemVariant> storage = level.isLoaded(pos) ? ItemStorage.SIDED.find(level, pos, null) : null;
		if (storage != null) {
			for (StorageView<ItemVariant> view : storage) {
				total++;
				if (!view.isResourceBlank() && view.getAmount() > 0) {
					used++;
					items += view.getAmount();
				}
			}
		}

		return new long[] {items, used, total};
	}

	/**
	 * Inventories next to the pipes connected to {@code start} (pipes only; an inventory touching {@code start} itself
	 * but no pipe is not on the network). Nearest first, bounded by {@link #MAX_PIPES}.
	 */
	public static java.util.List<BlockPos> inventoriesOnNetwork(Level level, BlockPos start) {
		Set<BlockPos> inventories = new LinkedHashSet<>();
		Set<BlockPos> visited = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		visited.add(start);
		for (Direction dir : Direction.values()) {
			BlockPos next = start.relative(dir);
			if (level.isLoaded(next) && level.getBlockState(next).getBlock() instanceof ItemPipeBlock) {
				visited.add(next.immutable());
				queue.add(next.immutable());
			}
		}

		int pipes = 0;
		while (!queue.isEmpty() && pipes <= MAX_PIPES) {
			BlockPos pos = queue.poll();
			pipes++;
			for (Direction dir : Direction.values()) {
				BlockPos next = pos.relative(dir);
				if (visited.contains(next) || !level.isLoaded(next)) {
					continue;
				}

				var state = level.getBlockState(next);
				if (state.getBlock() instanceof ItemPipeBlock) {
					visited.add(next.immutable());
					queue.add(next.immutable());
				} else if (!(state.getBlock() instanceof QuarryBlock) && !state.isAir()
					&& !(level.getBlockEntity(next) instanceof com.solarquarry.blockentity.SortingTerminalBlockEntity)
					&& ItemStorage.SIDED.find(level, next, dir.getOpposite()) != null) {
					inventories.add(next.immutable());
				}
			}
		}

		return new java.util.ArrayList<>(inventories);
	}
}
