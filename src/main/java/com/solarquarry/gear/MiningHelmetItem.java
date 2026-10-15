package com.solarquarry.gear;

import com.solarquarry.item.TooltipLines;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Mining Helmet: iron-grade protection and Haste I while you are underground (below y {@link WornGear#MINING_Y}). */
public class MiningHelmetItem extends Item implements TooltipLines {
	public MiningHelmetItem(Properties properties) {
		super(properties);
	}

	@Override
	public void addTooltipLines(ItemStack stack, Consumer<Component> builder) {
		builder.accept(Component.translatable("tooltip.solarquarry.mining_helmet", WornGear.MINING_Y).withStyle(ChatFormatting.GRAY));
	}
}
