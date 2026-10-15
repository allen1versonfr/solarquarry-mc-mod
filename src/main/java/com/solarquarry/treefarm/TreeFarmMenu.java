package com.solarquarry.treefarm;

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

/** Tree Farm menu: saplings 3x3, bone meal 1x3, output 6x3, upgrades 2x2, player inventory. */
public class TreeFarmMenu extends AbstractContainerMenu {
	public static final int SAPLING_X = 8;
	public static final int SAPLING_Y = 66;
	public static final int BONE_X = 68;
	public static final int OUTPUT_X = 98;
	public static final int UPGRADE_X = 214;
	public static final int UPGRADE_Y = 18;
	public static final int INVENTORY_X = 48;
	public static final int INVENTORY_Y = 136;
	public static final int HOTBAR_Y = 194;
	private final Container container;
	private final ContainerData data;
	private final BlockPos pos;

	public TreeFarmMenu(int containerId, Inventory inventory, BlockPos pos) {
		this(containerId, inventory, pos, new SimpleContainer(TreeFarmBlockEntity.TOTAL_SLOTS), new SimpleContainerData(TreeFarmBlockEntity.DATA_COUNT));
	}

	public TreeFarmMenu(int containerId, Inventory inventory, TreeFarmBlockEntity farm) {
		this(containerId, inventory, farm.getBlockPos(), farm, farm.getDataAccess());
	}

	private TreeFarmMenu(int containerId, Inventory inventory, BlockPos pos, Container container, ContainerData data) {
		super(ModMenus.TREE_FARM, containerId);
		checkContainerSize(container, TreeFarmBlockEntity.TOTAL_SLOTS);
		checkContainerDataCount(data, TreeFarmBlockEntity.DATA_COUNT);
		this.container = container;
		this.data = data;
		this.pos = pos;
		container.startOpen(inventory.player);
		for (int i = 0; i < TreeFarmBlockEntity.SAPLING_SLOTS; i++) {
			this.addSlot(new RuleSlot(container, i, SAPLING_X + (i % 3) * 18, SAPLING_Y + (i / 3) * 18));
		}

		for (int i = 0; i < TreeFarmBlockEntity.BONE_MEAL_SLOTS; i++) {
			this.addSlot(new RuleSlot(container, TreeFarmBlockEntity.BONE_MEAL_START + i, BONE_X, SAPLING_Y + i * 18));
		}

		for (int i = 0; i < TreeFarmBlockEntity.OUTPUT_SLOTS; i++) {
			this.addSlot(new RuleSlot(container, TreeFarmBlockEntity.OUTPUT_START + i, OUTPUT_X + (i % 6) * 18, SAPLING_Y + (i / 6) * 18));
		}

		for (int i = 0; i < TreeFarmBlockEntity.UPGRADE_SLOTS; i++) {
			this.addSlot(new RuleSlot(container, TreeFarmBlockEntity.UPGRADE_START + i, UPGRADE_X + (i % 2) * 18, UPGRADE_Y + (i / 2) * 18) {
				@Override
				public int getMaxStackSize(ItemStack stack) {
					return stack.getItem() instanceof com.solarquarry.item.UpgradeItem upgrade ? upgrade.type().maxStack() : 1;
				}
			});
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

	/** Slot that follows the farm's slot rules (outputs are take-only). */
	private static class RuleSlot extends Slot {
		RuleSlot(Container container, int slot, int x, int y) {
			super(container, slot, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return TreeFarmBlockEntity.canPlace(this.container, this.getContainerSlot(), stack);
		}
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

	public TreeFarmBlockEntity.Status getStatus() {
		int o = this.data.get(4);
		TreeFarmBlockEntity.Status[] values = TreeFarmBlockEntity.Status.values();
		return o >= 0 && o < values.length ? values[o] : TreeFarmBlockEntity.Status.IDLE;
	}

	public int getRadius() {
		return this.data.get(5);
	}

	public int getTreesFelled() {
		return this.data.get(6);
	}

	public int getSaplingsPlanted() {
		return this.data.get(7);
	}

	public int getFellingPercent() {
		return this.data.get(8);
	}

	public int getSpeed() {
		return this.data.get(9);
	}

	public int getGrowth() {
		return this.data.get(10);
	}

	public boolean hasHarvest() {
		return this.data.get(11) > 0;
	}

	public int getTicksPerOperation() {
		return this.data.get(12);
	}

	public int getPlanted() {
		return this.data.get(13);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		int machine = TreeFarmBlockEntity.TOTAL_SLOTS;
		if (index < machine) {
			if (!this.moveItemStackTo(stack, machine, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else {
			boolean moved = false;
			for (int i = 0; i < machine && !stack.isEmpty(); i++) {
				Slot target = this.slots.get(i);
				if (target.mayPlace(stack)) {
					moved |= this.moveItemStackTo(stack, i, i + 1, false);
				}
			}

			if (!moved) {
				return ItemStack.EMPTY;
			}
		}

		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}

		return original;
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
