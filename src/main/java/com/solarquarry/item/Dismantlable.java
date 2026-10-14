package com.solarquarry.item;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/**
 * A block entity the wrench can pick up with its state. {@link #extractForPickup} returns the data to store in the
 * item and empties the block entity at the same time, so removing the block afterwards cannot drop or duplicate items.
 */
public interface Dismantlable {
	CompoundTag extractForPickup(HolderLookup.Provider registries);
}
