package com.solarquarry.menu;

import com.solarquarry.blockentity.ItemPipeBlockEntity;
import com.solarquarry.registry.ModMenus;
import net.minecraft.core.BlockPos;
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

public class FilterPipeMenu extends AbstractContainerMenu {
	public static final int BUTTON_TOGGLE_MODE = 0;
	public static final int BUTTON_TOGGLE_COMPONENTS = 1;
	public static final int BUTTON_PRIORITY_DOWN = 2;
	public static final int BUTTON_PRIORITY_UP = 3;
	public static final int FILTER_X = 62;
	public static final int FILTER_Y = 24;
	public static final int INVENTORY_X = 8;
	public static final int INVENTORY_Y = 150;
	public static final int HOTBAR_Y = 208;
	private static final int FILTER_SLOTS = ItemPipeBlockEntity.FILTER_SLOTS;
	private static final int PLAYER_START = FILTER_SLOTS;

	private final Container filter;
	private final ContainerData data;
	private final BlockPos pos;
	private final @Nullable ItemPipeBlockEntity pipe;

	public FilterPipeMenu(int containerId, Inventory inventory, BlockPos pos) {
		this(containerId, inventory, pos, new SimpleContainer(FILTER_SLOTS), new SimpleContainerData(3), null);
	}

	public FilterPipeMenu(int containerId, Inventory inventory, ItemPipeBlockEntity pipe) {
		this(containerId, inventory, pipe.getBlockPos(), pipe.getFilter(), pipe.getFilterData(), pipe);
	}

	private FilterPipeMenu(int containerId, Inventory inventory, BlockPos pos, Container filter, ContainerData data, @Nullable ItemPipeBlockEntity pipe) {
		super(ModMenus.FILTER_PIPE, containerId);
		this.filter = filter;
		this.data = data;
		this.pos = pos;
		this.pipe = pipe;

		for (int i = 0; i < FILTER_SLOTS; i++) {
			this.addSlot(new GhostSlot(filter, i, FILTER_X + (i % 3) * 18, FILTER_Y + (i / 3) * 18));
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

	public int getPriority() {
		return this.data.get(2) + ItemPipeBlockEntity.MIN_PRIORITY;
	}

	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		if (this.pipe == null) {
			return false;
		}

		if (buttonId == BUTTON_TOGGLE_MODE) {
			this.pipe.toggleWhitelist();
			return true;
		}

		if (buttonId == BUTTON_TOGGLE_COMPONENTS) {
			this.pipe.toggleMatchComponents();
			return true;
		}

		if (buttonId == BUTTON_PRIORITY_DOWN || buttonId == BUTTON_PRIORITY_UP) {
			this.pipe.setPriority(this.pipe.getPriority() + (buttonId == BUTTON_PRIORITY_UP ? 1 : -1));
			return true;
		}

		return false;
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

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		Slot slot = this.slots.get(slotIndex);
		if (slotIndex < PLAYER_START || !slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		// Shift-clicking an item in the inventory adds it to the first free filter slot as a template.
		ItemStack stack = slot.getItem();
		for (int i = 0; i < FILTER_SLOTS; i++) {
			if (!this.filter.getItem(i).isEmpty() && this.filter.getItem(i).is(stack.getItem())) {
				return ItemStack.EMPTY;
			}
		}

		for (int i = 0; i < FILTER_SLOTS; i++) {
			if (this.filter.getItem(i).isEmpty()) {
				this.filter.setItem(i, stack.copyWithCount(1));
				this.filter.setChanged();
				break;
			}
		}

		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return this.pipe == null || this.pipe.stillValid(player);
	}
}
