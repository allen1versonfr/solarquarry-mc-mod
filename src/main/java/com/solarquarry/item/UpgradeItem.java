package com.solarquarry.item;

import com.solarquarry.config.SolarQuarryConfig;
import com.solarquarry.quarry.UpgradeType;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class UpgradeItem extends Item implements TooltipLines {
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
	public void addTooltipLines(ItemStack itemStack, Consumer<Component> builder) {
		String key = this.type == UpgradeType.RANGE ? "tooltip.solarquarry.upgrade.range_" + this.tier : "tooltip.solarquarry.upgrade." + this.type.id();
		builder.accept(Component.translatable(key).withStyle(ChatFormatting.GRAY));
		if (this.type == UpgradeType.CHUNK_LOADER) {
			SolarQuarryConfig config = SolarQuarryConfig.get();
			builder.accept(config.allowChunkLoading()
				? Component.translatable("tooltip.solarquarry.upgrade.chunk_loader.upkeep", config.chunkLoaderUpkeep()).withStyle(ChatFormatting.DARK_GRAY)
				: Component.translatable("tooltip.solarquarry.upgrade.chunk_loader.disabled").withStyle(ChatFormatting.RED));
		}

		if (this.type.maxStack() > 1) {
			builder.accept(Component.translatable("tooltip.solarquarry.upgrade.stack", this.type.maxStack()).withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
