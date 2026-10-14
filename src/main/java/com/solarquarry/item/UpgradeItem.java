package com.solarquarry.item;

import com.solarquarry.quarry.UpgradeType;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

public class UpgradeItem extends Item {
	private final UpgradeType type;
	private final int tier;

	public UpgradeItem(UpgradeType type, int tier, Properties properties) {
		super(properties);
		this.type = type;
		this.tier = tier;
	}

	public UpgradeType type() {
		return this.type;
	}

	/** Range tier (1 = 32x32, 2 = 64x64); 0 for other upgrades. */
	public int tier() {
		return this.tier;
	}

	@Override
	public void appendHoverText(ItemStack itemStack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag tooltipFlag) {
		String key = this.type == UpgradeType.RANGE ? "tooltip.solarquarry.upgrade.range_" + this.tier : "tooltip.solarquarry.upgrade." + this.type.id();
		builder.accept(Component.translatable(key).withStyle(ChatFormatting.GRAY));
		if (this.type.maxStack() > 1) {
			builder.accept(Component.translatable("tooltip.solarquarry.upgrade.stack", this.type.maxStack()).withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
