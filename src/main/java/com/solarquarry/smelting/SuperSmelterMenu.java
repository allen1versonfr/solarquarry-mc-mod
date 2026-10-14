package com.solarquarry.smelting;

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
import org.jspecify.annotations.Nullable;

/** Super Smelter menu: three lanes (input, result), the player inventory and the machine's synced numbers. */
public class SuperSmelterMenu extends AbstractContainerMenu {
	public static final int INPUT_X = 44;
	public static final int OUTPUT_X = 116;
	public static final int LANE_Y = 22;
	public static final int LANE_STEP = 22;
	public static final int INVENTORY_X = 8;
	public static final int INVENTORY_Y = 104;
	public static final int HOTBAR_Y = 162;
	private final Container container;
	private final ContainerData data;
	private final BlockPos pos;
	private final @Nullable SuperSmelterBlockEntity smelter;

	/** Client constructor. */
	public SuperSmelterMenu(int containerId, Inventory inventory, BlockPos pos) {
		this(containerId, inventory, pos, new SimpleContainer(SuperSmelterBlockEntity.SLOTS), new SimpleContainerData(SuperSmelterBlockEntity.DATA_COUNT), null);
	}

	/** Server constructor. */
	public SuperSmelterMenu(int containerId, Inventory inventory, SuperSmelterBlockEntity smelter) {
		this(containerId, inventory, smelter.getBlockPos(), smelter, smelter.getDataAccess(), smelter);
	}

	private SuperSmelterMenu(int containerId, Inventory inventory, BlockPos pos, Container container, ContainerData data, @Nullable SuperSmelterBlockEntity smelter) {
		super(ModMenus.SUPER_SMELTER, containerId);
		checkContainerSize(container, SuperSmelterBlockEntity.SLOTS);
		checkContainerDataCount(data, SuperSmelterBlockEntity.DATA_COUNT);
		this.container = container;
		this.data = data;
		this.pos = pos;
		this.smelter = smelter;
		container.startOpen(inventory.player);
		for (int lane = 0; lane < SuperSmelterBlockEntity.LANES; lane++) {
			this.addSlot(new Slot(container, lane, INPUT_X, LANE_Y + lane * LANE_STEP) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return SuperSmelterMenu.this.container.canPlaceItem(this.getContainerSlot(), stack);
				}
			});
		}

		for (int lane = 0; lane < SuperSmelterBlockEntity.LANES; lane++) {
			this.addSlot(new ResultSlot(container, SuperSmelterBlockEntity.LANES + lane, OUTPUT_X, LANE_Y + lane * LANE_STEP));
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

	/** A result slot: take only; taking results also hands out the smelting experience. */
	private final class ResultSlot extends Slot {
		ResultSlot(Container container, int slot, int x, int y) {
			super(container, slot, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}

		@Override
		public void onTake(Player player, ItemStack stack) {
			super.onTake(player, stack);
			if (SuperSmelterMenu.this.smelter != null) {
				SuperSmelterMenu.this.smelter.awardExperience(player);
			}
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

	/** 0..1 */
	public float getProgress(int lane) {
		return Math.min(1.0F, this.data.get(4 + lane) / (float) SuperSmelterBlockEntity.COOK_TICKS);
	}

	public SuperSmelterBlockEntity.Status getStatus() {
		int ordinal = this.data.get(7);
		SuperSmelterBlockEntity.Status[] values = SuperSmelterBlockEntity.Status.values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : SuperSmelterBlockEntity.Status.IDLE;
	}

	public int getPowerDraw() {
		return this.data.get(8);
	}

	public int getStoredExperience() {
		return this.data.get(9);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		int machineEnd = SuperSmelterBlockEntity.SLOTS;
		if (index < machineEnd) {
			if (!this.moveItemStackTo(stack, machineEnd, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}

			if (index >= SuperSmelterBlockEntity.LANES) {
				slot.onTake(player, stack);
			}
		} else if (!this.container.canPlaceItem(0, stack) || !this.moveItemStackTo(stack, 0, SuperSmelterBlockEntity.LANES, false)) {
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
