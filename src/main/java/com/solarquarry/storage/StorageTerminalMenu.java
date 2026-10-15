package com.solarquarry.storage;

import com.solarquarry.blockentity.SortingTerminalBlockEntity;
import com.solarquarry.network.StorageActionPayload;
import com.solarquarry.network.StorageSnapshotPayload;
import com.solarquarry.registry.ModMenus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Storage view of the Storage Terminal: every chest, barrel and crate on its pipe network as one searchable
 * inventory. Only the player inventory has real slots; the network list is streamed by {@link StorageSnapshotPayload}
 * and every withdrawal or deposit is decided on the server ({@link #perform}).
 */
public class StorageTerminalMenu extends AbstractContainerMenu {
	public static final int INVENTORY_X = 8;
	public static final int INVENTORY_Y = 140;
	public static final int HOTBAR_Y = 198;
	private static final int SNAPSHOT_INTERVAL = 20;

	private final BlockPos pos;
	private final @Nullable SortingTerminalBlockEntity terminal;
	private final @Nullable ServerPlayer player;
	private boolean refresh = true;
	private int ticks;

	public StorageTerminalMenu(int containerId, Inventory inventory, BlockPos pos) {
		this(containerId, inventory, pos, null);
	}

	public StorageTerminalMenu(int containerId, Inventory inventory, SortingTerminalBlockEntity terminal) {
		this(containerId, inventory, terminal.getBlockPos(), terminal);
	}

	private StorageTerminalMenu(int containerId, Inventory inventory, BlockPos pos, @Nullable SortingTerminalBlockEntity terminal) {
		super(ModMenus.STORAGE_TERMINAL, containerId);
		this.pos = pos;
		this.terminal = terminal;
		this.player = inventory.player instanceof ServerPlayer serverPlayer && terminal != null ? serverPlayer : null;
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
			}
		}

		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
		}
	}

	/** Opens the storage view of a terminal. */
	public static ExtendedMenuProvider<BlockPos> provider(SortingTerminalBlockEntity terminal) {
		return new ExtendedMenuProvider<>() {
			@Override
			public BlockPos getScreenOpeningData(ServerPlayer player) {
				return terminal.getBlockPos();
			}

			@Override
			public Component getDisplayName() {
				return Component.translatable("block.solarquarry.sorting_terminal");
			}

			@Override
			public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
				return new StorageTerminalMenu(containerId, inventory, terminal);
			}
		};
	}

	public BlockPos getPos() {
		return this.pos;
	}

	private List<BlockPos> network() {
		return this.terminal == null ? List.of() : StorageNetwork.inventories(this.terminal);
	}

	// ---- server actions ----

	/** Applies one request from the client (already checked: right menu, right terminal, in range). */
	public void perform(ServerPlayer player, int action, ItemStack item, int amount) {
		if (this.terminal == null || this.terminal.getLevel() == null) {
			return;
		}

		switch (action) {
			case StorageActionPayload.WITHDRAW_TO_CURSOR -> this.withdrawToCursor(item, amount);
			case StorageActionPayload.WITHDRAW_TO_INVENTORY -> this.withdrawToInventory(player, item, amount);
			case StorageActionPayload.DEPOSIT_CURSOR -> this.depositCursor(player, Integer.MAX_VALUE);
			case StorageActionPayload.DEPOSIT_ONE_FROM_CURSOR -> this.depositCursor(player, 1);
			case StorageActionPayload.DEPOSIT_INVENTORY -> this.depositInventory(player);
			case StorageActionPayload.OPEN_RULES -> {
				player.openMenu(this.terminal);
				return;
			}
			default -> {
			}
		}

		this.refresh = true;
	}

	private void withdrawToCursor(ItemStack item, int amount) {
		if (item.isEmpty()) {
			return;
		}

		ItemStack carried = this.getCarried();
		if (!carried.isEmpty() && !ItemStack.isSameItemSameComponents(carried, item)) {
			return;
		}

		int room = item.getMaxStackSize() - carried.getCount();
		ItemStack got = StorageNetwork.extract(this.terminal.getLevel(), this.network(), ItemVariant.of(item), Math.min(Math.max(1, amount), room));
		if (got.isEmpty()) {
			return;
		}

		if (carried.isEmpty()) {
			this.setCarried(got);
		} else {
			carried.grow(got.getCount());
		}
	}

	private void withdrawToInventory(ServerPlayer player, ItemStack item, int amount) {
		if (item.isEmpty()) {
			return;
		}

		int room = roomFor(player.getInventory(), item);
		ItemStack got = StorageNetwork.extract(this.terminal.getLevel(), this.network(), ItemVariant.of(item), Math.min(Math.max(1, amount), room));
		if (!got.isEmpty() && !player.getInventory().add(got)) {
			player.drop(got, false); // cannot happen (room was checked), but never lose items
		}
	}

	/** Space for this item in the player's main inventory (36 slots). */
	public static int roomFor(Inventory inventory, ItemStack item) {
		int room = 0;
		for (int i = 0; i < 36; i++) {
			ItemStack slot = inventory.getItem(i);
			if (slot.isEmpty()) {
				room += item.getMaxStackSize();
			} else if (ItemStack.isSameItemSameComponents(slot, item)) {
				room += Math.max(0, slot.getMaxStackSize() - slot.getCount());
			}
		}

		return room;
	}

	private void depositCursor(ServerPlayer player, int max) {
		ItemStack carried = this.getCarried();
		if (carried.isEmpty()) {
			return;
		}

		ItemStack part = carried.copyWithCount(Math.min(max, carried.getCount()));
		int before = part.getCount();
		StorageNetwork.deposit(this.terminal, this.network(), part);
		int stored = before - part.getCount();
		carried.shrink(stored);
		if (stored == 0) {
			this.noRoom(player, carried);
		}
	}

	private void depositInventory(ServerPlayer player) {
		List<BlockPos> network = this.network();
		ItemStack refused = ItemStack.EMPTY;
		for (int i = 9; i < 36; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!stack.isEmpty()) {
				StorageNetwork.deposit(this.terminal, network, stack);
				if (!stack.isEmpty() && refused.isEmpty()) {
					refused = stack;
				}
			}
		}

		player.getInventory().setChanged();
		if (!refused.isEmpty()) {
			this.noRoom(player, refused);
		}
	}

	private void noRoom(ServerPlayer player, ItemStack stack) {
		String key = this.terminal.getRules().isEmpty() ? "gui.solarquarry.storage.no_room" : "gui.solarquarry.storage.no_rule";
		player.sendSystemMessage(Component.translatable(key, stack.getHoverName()), true);
	}

	/** Shift-clicking an inventory stack stores it in the network (following the sorting rules). */
	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		Slot slot = this.slots.get(slotIndex);
		if (this.terminal == null || !slot.hasItem() || !(player instanceof ServerPlayer serverPlayer)) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = slot.getItem();
		int before = stack.getCount();
		StorageNetwork.deposit(this.terminal, this.network(), stack);
		if (stack.getCount() == before) {
			this.noRoom(serverPlayer, stack);
		}

		slot.setChanged();
		this.refresh = true;
		return ItemStack.EMPTY;
	}

	@Override
	public void broadcastChanges() {
		super.broadcastChanges();
		if (this.terminal == null || this.player == null || this.terminal.getLevel() == null) {
			return;
		}

		this.ticks++;
		if (!this.refresh && this.ticks % SNAPSHOT_INTERVAL != 0) {
			return;
		}

		this.refresh = false;
		ServerPlayNetworking.send(this.player, snapshot(this.terminal));
	}

	/** The network's contents as sent to the screen (largest amounts first). */
	public static StorageSnapshotPayload snapshot(SortingTerminalBlockEntity terminal) {
		StorageNetwork.Contents contents = StorageNetwork.contents(terminal.getLevel(), StorageNetwork.inventories(terminal));
		List<StorageSnapshotPayload.Entry> entries = new ArrayList<>();
		for (Map.Entry<ItemVariant, Long> e : contents.amounts().entrySet()) {
			entries.add(new StorageSnapshotPayload.Entry(e.getKey().toStack(), e.getValue()));
		}

		entries.sort((a, b) -> Long.compare(b.amount(), a.amount()));
		boolean truncated = entries.size() > StorageSnapshotPayload.MAX_ENTRIES;
		if (truncated) {
			entries = new ArrayList<>(entries.subList(0, StorageSnapshotPayload.MAX_ENTRIES));
		}

		return new StorageSnapshotPayload(terminal.getBlockPos(), entries, contents.inventories(), contents.usedSlots(), contents.totalSlots(), truncated);
	}

	@Override
	public boolean stillValid(Player player) {
		return this.terminal == null || this.terminal.stillValid(player);
	}
}
