package com.solarquarry.menu;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** Buffer slot: players can take mined items but cannot insert anything. */
public class OutputSlot extends ToggleableSlot {
	public OutputSlot(Container container, int slot, int x, int y) {
		super(container, slot, x, y);
	}

	@Override
	public boolean mayPlace(ItemStack itemStack) {
		return false;
	}
}
