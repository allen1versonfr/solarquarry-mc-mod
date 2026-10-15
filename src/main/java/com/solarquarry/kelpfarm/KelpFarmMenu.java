package com.solarquarry.kelpfarm;

import com.solarquarry.construction.ConstructionState;
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

/** Kelp Farm menu: kelp 1x3, bone meal, output 6x3, upgrades, player inventory. */
public class KelpFarmMenu extends AbstractContainerMenu {
	public static final int KELP_X = 8;
	public static final int ROW_Y = 66;
	public static final int BONE_X = 38;
	public static final int OUTPUT_X = 98;
	public static final int UPGRADE_X = 214;
	public static final int UPGRADE_Y = 18;
	public static final int INVENTORY_X = 48;
	public static final int INVENTORY_Y = 136;
	public static final int HOTBAR_Y = 194;
	private final Container container;
	private final ContainerData data;
	private final BlockPos pos;

	public KelpFarmMenu(int containerId, Inventory inventory, BlockPos pos) {
		this(containerId, inventory, pos, new SimpleContainer(KelpFarmBlockEntity.TOTAL_SLOTS), new SimpleContainerData(KelpFarmBlockEntity.DATA_COUNT));
	}

	public KelpFarmMenu(int containerId, Inventory inventory, KelpFarmBlockEntity farm) {
		this(containerId, inventory, farm.getBlockPos(), farm, farm.getDataAccess());
	}

	private KelpFarmMenu(int containerId, Inventory inventory, BlockPos pos, Container container, ContainerData data) {
		super(ModMenus.KELP_FARM, containerId);
		checkContainerSize(container, KelpFarmBlockEntity.TOTAL_SLOTS);
		checkContainerDataCount(data, KelpFarmBlockEntity.DATA_COUNT);
		this.container = container;
		this.data = data;
		this.pos = pos;
		container.startOpen(inventory.player);
		for (int i = 0; i < KelpFarmBlockEntity.KELP_SLOTS; i++) {
			this.addSlot(new RuleSlot(container, i, KELP_X, ROW_Y + i * 18));
		}

		this.addSlot(new RuleSlot(container, KelpFarmBlockEntity.BONE_MEAL_SLOT, BONE_X, ROW_Y));
		for (int i = 0; i < KelpFarmBlockEntity.OUTPUT_SLOTS; i++) {
			this.addSlot(new RuleSlot(container, KelpFarmBlockEntity.OUTPUT_START + i, OUTPUT_X + (i % 6) * 18, ROW_Y + (i / 6) * 18));
		}

		for (int i = 0; i < KelpFarmBlockEntity.UPGRADE_SLOTS; i++) {
			this.addSlot(new RuleSlot(container, KelpFarmBlockEntity.UPGRADE_START + i, UPGRADE_X + (i % 2) * 18, UPGRADE_Y + (i / 2) * 18) {
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

	private static class RuleSlot extends Slot {
		RuleSlot(Container container, int slot, int x, int y) {
			super(container, slot, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return KelpFarmBlockEntity.canPlace(this.container, this.getContainerSlot(), stack);
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

	public KelpFarmBlockEntity.Status getStatus() {
		KelpFarmBlockEntity.Status[] values = KelpFarmBlockEntity.Status.values();
		int o = this.data.get(4);
		return o >= 0 && o < values.length ? values[o] : KelpFarmBlockEntity.Status.IDLE;
	}

	public int getHarvested() {
		return this.data.get(5);
	}

	public int getColumns() {
		return this.data.get(6);
	}

	public ConstructionState getConstructionState() {
		return ConstructionState.byOrdinal(this.data.get(7));
	}

	public int getConstructionPercent() {
		return this.data.get(8);
	}

	public @Nullable BlockPos getBlockedAt() {
		if (this.data.get(9) == 0) {
			return null;
		}

		return this.pos.offset((short) this.data.get(10), (short) this.data.get(11), (short) this.data.get(12));
	}

	public int getSpeed() {
		return this.data.get(13);
	}

	public int getGrowth() {
		return this.data.get(14);
	}

	public int getTallest() {
		return this.data.get(15);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		int machine = KelpFarmBlockEntity.TOTAL_SLOTS;
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
