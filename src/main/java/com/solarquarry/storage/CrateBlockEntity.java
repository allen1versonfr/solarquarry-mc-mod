package com.solarquarry.storage;

import com.solarquarry.item.Dismantlable;
import com.solarquarry.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Storage crate: 54, 108 or 162 slots. A plain container, so pipes, hoppers and the Fabric transfer API use it like a
 * chest. The wrench picks it up with its contents (like a shulker box); breaking it drops the contents.
 */
public class CrateBlockEntity extends BaseContainerBlockEntity implements Dismantlable, net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider<Integer> {
	private NonNullList<ItemStack> items;

	public CrateBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CRATE, pos, state);
		this.items = NonNullList.withSize(tierOf(state).slots(), ItemStack.EMPTY);
	}

	public static CrateTier tierOf(BlockState state) {
		return state.getBlock() instanceof CrateBlock crate ? crate.tier() : CrateTier.BASIC;
	}

	public CrateTier tier() {
		return tierOf(this.getBlockState());
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.solarquarry.crate_" + this.tier().id());
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return this.items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	public int getContainerSize() {
		return this.items.size();
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return new CrateMenu(containerId, inventory, this, this.tier());
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.items = NonNullList.withSize(this.tier().slots(), ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, this.items);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items);
	}

	@Override
	public CompoundTag extractForPickup(HolderLookup.Provider registries) {
		CompoundTag data = this.saveCustomOnly(registries);
		this.items.clear();
		this.setChanged();
		return data;
	}

	public int usedSlots() {
		int used = 0;
		for (ItemStack stack : this.items) {
			if (!stack.isEmpty()) {
				used++;
			}
		}

		return used;
	}

	@Override
	public Integer getScreenOpeningData(net.minecraft.server.level.ServerPlayer player) {
		return this.tier().ordinal();
	}
}
