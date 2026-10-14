package com.solarquarry.item;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Extra tooltip lines for Solar Quarry items, added on the client through Fabric's ItemTooltipCallback. */
public interface TooltipLines {
	void addTooltipLines(ItemStack stack, Consumer<Component> lines);
}
