package com.solarquarry.charging;

import com.solarquarry.item.PoweredItem;
import com.solarquarry.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Charging Station menu: the item being charged, the finished slot, the player inventory, synced numbers. */
public class ChargingStationMenu extends AbstractContainerMenu {
	public static final int INPUT_X = 52;
	public static final int OUTPUT_X = 124;
	public static final int SLOT_Y = 34;
	public static final int INVENTORY_X = 8;
	public static final int INVENTORY_Y = 92;
	public static final int HOTBAR_Y = 150;
	private final Container container;
	private final ContainerData data;
	private final BlockPos pos;

	/** Client constructor. */
	public ChargingStationMenu(int containerId, Inventory inventory, BlockPos pos) {
		this(containerId, inventory, pos, new SimpleContainer(ChargingStationBlockEntity.SLOTS), new SimpleContainerData(ChargingStationBlockEntity.DATA_COUNT));
	}

	/** Server constructor. */
	public ChargingStationMenu(int containerId, Inventory inventory, ChargingStationBlockEntity station) {
		this(containerId, inventory, station.getBlockPos(), station, station.getDataAccess());
	}

	private ChargingStationMenu(int containerId, Inventory inventory, BlockPos pos, Container container, ContainerData data) {
		super(ModMenus.CHARGING_STATION, containerId);
		checkContainerSize(container, ChargingStationBlockEntity.SLOTS);
		checkContainerDataCount(data, ChargingStationBlockEntity.DATA_COUNT);
		this.container = container;
		this.data = data;
		this.pos = pos;
		container.startOpen(inventory.player);
		this.addSlot(new Slot(container, ChargingStationBlockEntity.INPUT, INPUT_X, SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return ChargingStationBlockEntity.canPlace(ChargingStationBlockEntity.INPUT, stack);
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		this.addSlot(new Slot(container, ChargingStationBlockEntity.OUTPUT, OUTPUT_X, SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
			}
		}

		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
		}

		this.addDataSlots(data);
	}

	public BlockPos getPos() {
		return this.pos;
	}

	private int unsigned(int index) {
		return this.data.get(index) & 0xFFFF;
	}

	public long getEnergy() {
		return this.unsigned(0) | ((long) this.unsigned(1) << 16);
	}

	public long getCapacity() {
		return this.unsigned(2) | ((long) this.unsigned(3) << 16);
	}

	public int getRate() {
		return this.data.get(4);
	}

	public ChargingStationBlockEntity.Status getStatus() {
		int ordinal = this.data.get(5);
		var values = ChargingStationBlockEntity.Status.values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : ChargingStationBlockEntity.Status.EMPTY;
	}

	/** The item shown as charging: the input, else the finished one. */
	public ItemStack getShownItem() {
		ItemStack input = this.slots.get(0).getItem();
		return input.isEmpty() ? this.slots.get(1).getItem() : input;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		int machineEnd = ChargingStationBlockEntity.SLOTS;
		if (index < machineEnd) {
			if (!this.moveItemStackTo(stack, machineEnd, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (!PoweredItem.isPowered(stack) || !this.moveItemStackTo(stack, 0, 1, false)) {
			return ItemStack.EMPTY;
		}

		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}

		return stack.getCount() == original.getCount() ? ItemStack.EMPTY : original;
	}

	@Override
	public boolean stillValid(Player player) {
		return this.container.stillValid(player);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.container.stopOpen(player);
	}
}
