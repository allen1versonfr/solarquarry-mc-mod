package com.solarquarry.menu;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The quarry's torch supply: accepts only torches. */
public class TorchSlot extends ToggleableSlot {
	public TorchSlot(Container container, int slot, int x, int y) {
		super(container, slot, x, y);
	}

	@Override
	public boolean mayPlace(ItemStack itemStack) {
		return itemStack.is(Items.TORCH);
	}
}
