package com.solarquarry.pipe;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** An item stack moving through one pipe segment. */
public final class TravelingItem {
	public ItemStack stack;
	/** Face the item entered through (it travels from this face toward the center). */
	public Direction from;
	/** Face the item leaves through, or null while it has no route. */
	public @Nullable Direction to;
	/** Remaining exits from this pipe onward; element 0 equals {@link #to}. */
	public List<Direction> route;
	public int ticks;
	public boolean waiting;
	public int retry;

	public TravelingItem(ItemStack stack, Direction from, List<Direction> route) {
		this.stack = stack;
		this.from = from;
		this.route = new ArrayList<>(route);
		this.to = route.isEmpty() ? null : route.get(0);
	}

	public void setRoute(List<Direction> newRoute) {
		this.route = new ArrayList<>(newRoute);
		this.to = newRoute.isEmpty() ? null : newRoute.get(0);
	}
}
