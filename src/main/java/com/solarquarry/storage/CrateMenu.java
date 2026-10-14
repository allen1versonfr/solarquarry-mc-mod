package com.solarquarry.storage;

import com.solarquarry.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Crate menu: one 9 x 6 page at a time. The page slots show {@code page * 54 + i} of the crate; the page number is a
 * synced data slot, and page buttons are menu buttons (validated on the server).
 */
public class CrateMenu extends AbstractContainerMenu {
	public static final int ROWS = 6;
	public static final int GRID_Y = 18;
	public static final int INVENTORY_Y = GRID_Y + ROWS * 18 + 14;
	public static final int HOTBAR_Y = INVENTORY_Y + 58;
	public static final int BUTTON_PREVIOUS = 0;
	public static final int BUTTON_NEXT = 1;
	private final Container crate;
	private final CrateTier tier;
	private final DataSlot page = DataSlot.standalone();

	/** Client constructor: the client only mirrors the visible page (the tier comes with the opening data). */
	public CrateMenu(int containerId, Inventory inventory, CrateTier tier) {
		this(containerId, inventory, new SimpleContainer(CrateTier.PAGE_SLOTS), tier);
	}

	public CrateMenu(int containerId, Inventory inventory, Container crate, CrateTier tier) {
		super(ModMenus.CRATE, containerId);
		this.crate = crate;
		this.tier = tier;
		crate.startOpen(inventory.player);
		this.addDataSlot(this.page);
		for (int row = 0; row < ROWS; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new PageSlot(crate, row * 9 + col, 8 + col * 18, GRID_Y + row * 18));
			}
		}

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
			}
		}

		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(inventory, col, 8 + col * 18, HOTBAR_Y));
		}
	}

	public CrateTier tier() {
		return this.tier;
	}

	public int page() {
		return Math.clamp(this.page.get(), 0, this.tier.pages() - 1);
	}

	/** A crate slot whose real index depends on the current page. */
	private final class PageSlot extends Slot {
		private final int offset;

		PageSlot(Container container, int offset, int x, int y) {
			super(container, offset, x, y);
			this.offset = offset;
		}

		private int index() {
			// The client mirror holds only the visible page; the server container holds every page.
			return this.container.getContainerSize() <= CrateTier.PAGE_SLOTS ? this.offset : CrateMenu.this.page() * CrateTier.PAGE_SLOTS + this.offset;
		}

		@Override
		public ItemStack getItem() {
			return this.container.getItem(this.index());
		}

		@Override
		public void set(ItemStack stack) {
			this.container.setItem(this.index(), stack);
			this.setChanged();
		}

		@Override
		public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
			this.set(newStack);
		}

		@Override
		public ItemStack remove(int amount) {
			return this.container.removeItem(this.index(), amount);
		}

		@Override
		public int getContainerSlot() {
			return this.index();
		}
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		int pages = this.tier.pages();
		if (id == BUTTON_PREVIOUS && this.page() > 0) {
			this.page.set(this.page() - 1);
			return true;
		}

		if (id == BUTTON_NEXT && this.page() < pages - 1) {
			this.page.set(this.page() + 1);
			return true;
		}

		return false;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		Slot slot = this.slots.get(slotIndex);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		int pageEnd = CrateTier.PAGE_SLOTS;
		if (slotIndex < pageEnd) {
			if (!this.moveItemStackTo(stack, pageEnd, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (!this.moveIntoCrate(stack)) {
			return ItemStack.EMPTY;
		}

		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}

		return stack.getCount() == original.getCount() ? ItemStack.EMPTY : original;
	}

	/** Shift-click from the player: fill matching stacks anywhere in the crate first, then empty slots (all pages). */
	private boolean moveIntoCrate(ItemStack stack) {
		boolean moved = false;
		for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
			for (int i = 0; i < this.crate.getContainerSize() && !stack.isEmpty(); i++) {
				ItemStack existing = this.crate.getItem(i);
				if (pass == 0 && !existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
					int room = Math.min(this.crate.getMaxStackSize(existing), existing.getMaxStackSize()) - existing.getCount();
					int add = Math.min(room, stack.getCount());
					if (add > 0) {
						existing.grow(add);
						stack.shrink(add);
						this.crate.setChanged();
						moved = true;
					}
				} else if (pass == 1 && existing.isEmpty() && this.crate.canPlaceItem(i, stack)) {
					int add = Math.min(stack.getCount(), Math.min(this.crate.getMaxStackSize(stack), stack.getMaxStackSize()));
					this.crate.setItem(i, stack.split(add));
					moved = true;
				}
			}
		}

		return moved;
	}

	@Override
	public boolean stillValid(Player player) {
		return this.crate.stillValid(player);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.crate.stopOpen(player);
	}
}
