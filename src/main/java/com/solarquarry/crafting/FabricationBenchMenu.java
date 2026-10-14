package com.solarquarry.crafting;

import com.solarquarry.registry.ModBlocks;
import com.solarquarry.registry.ModMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Fabrication Bench menu: only the player's inventory; crafting goes through {@link BenchRecipes#craft}. */
public class FabricationBenchMenu extends AbstractContainerMenu {
	public static final int INVENTORY_X = 48;
	public static final int INVENTORY_Y = 140;
	public static final int HOTBAR_Y = 198;
	private final ContainerLevelAccess access;

	public FabricationBenchMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, ContainerLevelAccess.NULL);
	}

	public FabricationBenchMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
		super(ModMenus.FABRICATION_BENCH, containerId);
		this.access = access;
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
			}
		}

		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		boolean moved = index < 27 ? this.moveItemStackTo(stack, 27, 36, false) : this.moveItemStackTo(stack, 0, 27, false);
		if (!moved) {
			return ItemStack.EMPTY;
		}

		slot.setChanged();
		return original;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(this.access, player, ModBlocks.FABRICATION_BENCH);
	}
}
