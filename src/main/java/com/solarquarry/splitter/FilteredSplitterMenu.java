package com.solarquarry.splitter;

import com.solarquarry.menu.GhostSlot;
import com.solarquarry.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Filtered Splitter settings: 18 filter templates, mode, output and input faces. Every button is applied on the server. */
public class FilteredSplitterMenu extends AbstractContainerMenu {
	public static final int BUTTON_MODE = 0;
	public static final int BUTTON_COMPONENTS = 1;
	public static final int BUTTON_MATCH_FACE = 2;
	public static final int BUTTON_OTHER_FACE = 3;
	public static final int BUTTON_INPUT_FACE = 4;
	public static final int BUTTON_RESET = 5;
	public static final int BUTTON_CLEAR = 6;
	public static final int FILTER_X = 8;
	public static final int FILTER_Y = 24;
	public static final int INVENTORY_X = 8;
	public static final int INVENTORY_Y = 158;
	public static final int HOTBAR_Y = 216;
	private static final int FILTER_SLOTS = FilteredSplitterBlockEntity.FILTER_SLOTS;

	private final Container filter;
	private final ContainerData data;
	private final BlockPos pos;
	private final @Nullable FilteredSplitterBlockEntity splitter;

	public FilteredSplitterMenu(int containerId, Inventory inventory, BlockPos pos) {
		this(containerId, inventory, pos, new SimpleContainer(FILTER_SLOTS), new SimpleContainerData(FilteredSplitterBlockEntity.DATA_COUNT), null);
	}

	public FilteredSplitterMenu(int containerId, Inventory inventory, FilteredSplitterBlockEntity splitter) {
		this(containerId, inventory, splitter.getBlockPos(), splitter.getFilter(), splitter.getDataAccess(), splitter);
	}

	private FilteredSplitterMenu(int containerId, Inventory inventory, BlockPos pos, Container filter, ContainerData data, @Nullable FilteredSplitterBlockEntity splitter) {
		super(ModMenus.FILTERED_SPLITTER, containerId);
		this.filter = filter;
		this.data = data;
		this.pos = pos;
		this.splitter = splitter;
		for (int i = 0; i < FILTER_SLOTS; i++) {
			this.addSlot(new GhostSlot(filter, i, FILTER_X + (i % 9) * 18, FILTER_Y + (i / 9) * 18));
		}

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

	public boolean isWhitelist() {
		return this.data.get(0) != 0;
	}

	public boolean isMatchComponents() {
		return this.data.get(1) != 0;
	}

	public Direction matchFace() {
		return Direction.values()[Math.clamp(this.data.get(2), 0, 5)];
	}

	public Direction otherFace() {
		return Direction.values()[Math.clamp(this.data.get(3), 0, 5)];
	}

	/** Input face, or null for "any face that is not an output". */
	public @Nullable Direction inputFace() {
		int v = this.data.get(4);
		return v >= 0 && v < 6 ? Direction.values()[v] : null;
	}

	public FilteredSplitterBlockEntity.OutputState matchState() {
		return FilteredSplitterBlockEntity.OutputState.values()[Math.clamp(this.data.get(5), 0, 3)];
	}

	public FilteredSplitterBlockEntity.OutputState otherState() {
		return FilteredSplitterBlockEntity.OutputState.values()[Math.clamp(this.data.get(6), 0, 3)];
	}

	public int matchedCount() {
		return this.data.get(7);
	}

	public int otherCount() {
		return this.data.get(8);
	}

	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		if (this.splitter == null) {
			return false;
		}

		switch (buttonId) {
			case BUTTON_MODE -> this.splitter.toggleWhitelist();
			case BUTTON_COMPONENTS -> this.splitter.toggleMatchComponents();
			case BUTTON_MATCH_FACE -> this.splitter.cycleMatch();
			case BUTTON_OTHER_FACE -> this.splitter.cycleOther();
			case BUTTON_INPUT_FACE -> this.splitter.cycleInput();
			case BUTTON_RESET -> this.splitter.resetToDefaults();
			case BUTTON_CLEAR -> this.splitter.clearFilter();
			default -> {
				return false;
			}
		}

		return true;
	}

	@Override
	public void clicked(int slotIndex, int buttonNum, ContainerInput containerInput, Player player) {
		if (slotIndex >= 0 && slotIndex < FILTER_SLOTS) {
			if (containerInput == ContainerInput.PICKUP || containerInput == ContainerInput.QUICK_MOVE || containerInput == ContainerInput.CLONE) {
				ItemStack carried = this.getCarried();
				Slot slot = this.slots.get(slotIndex);
				slot.set(carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
				slot.setChanged();
			}

			return;
		}

		super.clicked(slotIndex, buttonNum, containerInput, player);
	}

	@Override
	public boolean canDragTo(Slot slot) {
		return !(slot instanceof GhostSlot) && super.canDragTo(slot);
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
		return !(target instanceof GhostSlot) && super.canTakeItemForPickAll(carried, target);
	}

	/** Shift-clicking an inventory item adds it to the filter as a template (real items never move). */
	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		Slot slot = this.slots.get(slotIndex);
		if (slotIndex < FILTER_SLOTS || !slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		addTemplate(this.filter, slot.getItem());
		return ItemStack.EMPTY;
	}

	/** Adds a 1-item template to the first free filter slot unless the item is already listed. */
	public static boolean addTemplate(Container filter, ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}

		for (int i = 0; i < filter.getContainerSize(); i++) {
			if (filter.getItem(i).is(stack.getItem())) {
				return false;
			}
		}

		for (int i = 0; i < filter.getContainerSize(); i++) {
			if (filter.getItem(i).isEmpty()) {
				filter.setItem(i, stack.copyWithCount(1));
				filter.setChanged();
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean stillValid(Player player) {
		return this.splitter == null || this.splitter.stillValid(player);
	}
}
