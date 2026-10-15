package com.solarquarry.storage;

import com.solarquarry.blockentity.SortingTerminalBlockEntity;
import com.solarquarry.pipe.SortRule;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * The Storage Terminal's view of its pipe network as one inventory. Storage means chests, barrels, shulker boxes and
 * Solar Quarry crates on the network, plus any inventory registered as a sorting destination. Everything runs on the
 * server thread inside one transaction per action, so nothing can be duplicated; unloaded chunks are skipped, never
 * loaded.
 *
 * <p>Deposits follow the sorting rules: when the terminal has destinations, an item goes to the best-ranked one that
 * has room (listed items, then "everything except", then fallback, each by priority), exactly like items arriving
 * through the pipes. Without any rules, items go to storage that already holds the same item, then to free space.
 */
public final class StorageNetwork {
	/** Upper bound on inventories looked at per action. */
	public static final int MAX_INVENTORIES = 128;

	private StorageNetwork() {
	}

	/** Chests, barrels, shulker boxes, trapped chests and crates; not hoppers, dispensers or machines. */
	public static boolean isPlainStorage(@Nullable BlockEntity be) {
		return be instanceof CrateBlockEntity
			|| (be instanceof RandomizableContainerBlockEntity && !(be instanceof HopperBlockEntity) && !(be instanceof DispenserBlockEntity));
	}

	/** Inventories the terminal can use: plain storage on its network plus registered destinations. Loaded only. */
	public static List<BlockPos> inventories(SortingTerminalBlockEntity terminal) {
		Level level = terminal.getLevel();
		if (level == null) {
			return List.of();
		}

		Set<BlockPos> result = new LinkedHashSet<>();
		for (BlockPos pos : terminal.discover()) {
			if (result.size() >= MAX_INVENTORIES) {
				break;
			}

			if ((terminal.getRule(pos) != null || isPlainStorage(level.getBlockEntity(pos))) && !result.contains(otherHalf(level, pos))) {
				result.add(pos);
			}
		}

		for (SortRule rule : terminal.getRules()) {
			BlockPos pos = BlockPos.of(rule.pos());
			if (result.size() < MAX_INVENTORIES && level.isLoaded(pos) && !result.contains(otherHalf(level, pos)) && storageAt(level, pos) != null) {
				result.add(pos);
			}
		}

		return new ArrayList<>(result);
	}

	/** The other half of a double chest, or the position itself. */
	public static BlockPos otherHalf(Level level, BlockPos pos) {
		var state = level.getBlockState(pos);
		if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock
			&& state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE) != net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
			return pos.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state));
		}

		return pos;
	}

	/**
	 * The storage of one block through the Fabric transfer API (a double chest is one storage).
	 */
	public static @Nullable Storage<ItemVariant> storageAt(Level level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return null;
		}

		// The transfer API joins both halves of a double chest; inventories() keeps only one half of each pair.
		Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, pos, null);
		if (storage == null && level.getBlockEntity(pos) instanceof Container container) {
			storage = ContainerStorage.of(container, null);
		}

		return storage;
	}

	/** Totals of one network: item kind -> amount, in first-found order; plus slot usage. */
	public record Contents(Map<ItemVariant, Long> amounts, int inventories, int usedSlots, int totalSlots) {
	}

	public static Contents contents(Level level, List<BlockPos> inventories) {
		Map<ItemVariant, Long> amounts = new LinkedHashMap<>();
		int used = 0;
		int total = 0;
		int count = 0;
		for (BlockPos pos : inventories) {
			Storage<ItemVariant> storage = storageAt(level, pos);
			if (storage == null) {
				continue;
			}

			count++;
			for (StorageView<ItemVariant> view : storage) {
				total++;
				if (view.isResourceBlank() || view.getAmount() <= 0) {
					continue;
				}

				used++;
				amounts.merge(view.getResource(), view.getAmount(), Long::sum);
			}
		}

		return new Contents(amounts, count, used, total);
	}

	/** Removes up to {@code max} of {@code variant} from the network and returns it as a stack (never more than one stack). */
	public static ItemStack extract(Level level, List<BlockPos> inventories, ItemVariant variant, int max) {
		if (variant.isBlank() || max <= 0) {
			return ItemStack.EMPTY;
		}

		int limit = Math.min(max, variant.getItem().getDefaultMaxStackSize());
		limit = Math.min(limit, variant.toStack().getMaxStackSize());
		long taken = 0;
		try (Transaction tx = Transaction.openOuter()) {
			for (BlockPos pos : inventories) {
				if (taken >= limit) {
					break;
				}

				Storage<ItemVariant> storage = storageAt(level, pos);
				if (storage != null && storage.supportsExtraction()) {
					taken += storage.extract(variant, limit - taken, tx);
				}
			}

			tx.commit();
		}

		return taken <= 0 ? ItemStack.EMPTY : variant.toStack((int) taken);
	}

	/**
	 * Puts as much of {@code stack} as possible into the network following the terminal's rules; the stack shrinks by
	 * what was stored. Returns false if nothing at all could be stored.
	 */
	public static boolean deposit(SortingTerminalBlockEntity terminal, List<BlockPos> inventories, ItemStack stack) {
		Level level = terminal.getLevel();
		if (level == null || stack.isEmpty()) {
			return false;
		}

		List<BlockPos> order = destinations(terminal, inventories, stack);
		ItemVariant variant = ItemVariant.of(stack);
		long stored = 0;
		try (Transaction tx = Transaction.openOuter()) {
			for (BlockPos pos : order) {
				if (stored >= stack.getCount()) {
					break;
				}

				Storage<ItemVariant> storage = storageAt(level, pos);
				if (storage != null && storage.supportsInsertion()) {
					stored += storage.insert(variant, stack.getCount() - stored, tx);
				}
			}

			tx.commit();
		}

		if (stored > 0) {
			stack.shrink((int) stored);
			return true;
		}

		return false;
	}

	/** Where a deposited item may go, best first. */
	public static List<BlockPos> destinations(SortingTerminalBlockEntity terminal, List<BlockPos> inventories, ItemStack stack) {
		Level level = terminal.getLevel();
		List<BlockPos> order = new ArrayList<>();
		if (!terminal.getRules().isEmpty()) {
			List<BlockPos> ranked = new ArrayList<>();
			Map<BlockPos, Integer> ranks = new LinkedHashMap<>();
			for (BlockPos pos : inventories) {
				int rank = terminal.rankFor(pos, stack);
				if (rank > 0) {
					ranks.put(pos, rank);
					ranked.add(pos);
				}
			}

			ranked.sort((a, b) -> Integer.compare(ranks.get(b), ranks.get(a)));
			return ranked;
		}

		// No rules: storage already holding this item first, then the rest (nearest first).
		ItemVariant variant = ItemVariant.of(stack);
		List<BlockPos> rest = new ArrayList<>();
		for (BlockPos pos : inventories) {
			Storage<ItemVariant> storage = level == null ? null : storageAt(level, pos);
			boolean holds = false;
			if (storage != null) {
				for (StorageView<ItemVariant> view : storage) {
					if (!view.isResourceBlank() && view.getResource().equals(variant)) {
						holds = true;
						break;
					}
				}
			}

			(holds ? order : rest).add(pos);
		}

		order.addAll(rest);
		return order;
	}
}
