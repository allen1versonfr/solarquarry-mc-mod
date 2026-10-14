package com.solarquarry.menu;

import java.util.function.BooleanSupplier;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;

/** Slot whose visibility can be switched by the screen (used for the quarry's configuration page). */
public class ToggleableSlot extends Slot {
	private BooleanSupplier active = () -> true;

	public ToggleableSlot(Container container, int slot, int x, int y) {
		super(container, slot, x, y);
	}

	public void setActiveCondition(BooleanSupplier active) {
		this.active = active;
	}

	@Override
	public boolean isActive() {
		return this.active.getAsBoolean();
	}
}
