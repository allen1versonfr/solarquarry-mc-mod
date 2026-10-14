package com.solarquarry.menu;

import com.solarquarry.blockentity.QuarryBlockEntity;
import com.solarquarry.item.UpgradeItem;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public class UpgradeSlot extends ToggleableSlot {
	public UpgradeSlot(Container container, int slot, int x, int y) {
		super(container, slot, x, y);
	}

	@Override
	public boolean mayPlace(ItemStack itemStack) {
		return itemStack.getItem() instanceof UpgradeItem && QuarryBlockEntity.canInstallUpgrade(this.container, this.getContainerSlot(), itemStack);
	}

	@Override
	public int getMaxStackSize(ItemStack itemStack) {
		if (itemStack.getItem() instanceof UpgradeItem upgrade) {
			return Math.min(upgrade.type().maxStack(), itemStack.getMaxStackSize());
		}

		return 1;
	}
}
