package com.solarquarry.gear;

import com.solarquarry.item.PoweredItem;
import com.solarquarry.item.TooltipLines;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Industrial Night-Vision Goggles: night vision while worn and charged ({@link WornGear}). */
public class GogglesItem extends Item implements TooltipLines, PoweredItem {
	public GogglesItem(Properties properties) {
		super(properties);
	}

	@Override
	public long capacity(ItemStack stack) {
		return WornGear.GOGGLES_CAPACITY;
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return !stack.isDamaged() || super.isBarVisible(stack);
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return stack.isDamaged() ? super.getBarWidth(stack) : PoweredItem.barWidth(stack);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return stack.isDamaged() ? super.getBarColor(stack) : PoweredItem.barColor(stack);
	}

	@Override
	public void addTooltipLines(ItemStack stack, Consumer<Component> builder) {
		builder.accept(PoweredItem.chargeLine(stack, WornGear.GOGGLES_DRAIN));
		builder.accept(Component.translatable("tooltip.solarquarry.goggles", WornGear.GOGGLES_DRAIN,
			Math.round(WornGear.GOGGLES_CAPACITY / (double) WornGear.GOGGLES_DRAIN / 20.0 / 60.0)).withStyle(ChatFormatting.GRAY));
	}
}
