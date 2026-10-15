package com.solarquarry.boots;

import com.solarquarry.item.PoweredItem;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Rocket Boots: hold crouch in the air to fly; energy is stored on the item (Charging Station or a battery). */
public class RocketBootsItem extends Item implements com.solarquarry.item.TooltipLines, PoweredItem {
	private final RocketTier tier;

	public RocketBootsItem(RocketTier tier, Properties properties) {
		super(properties);
		this.tier = tier;
	}

	public RocketTier tier() {
		return this.tier;
	}

	@Override
	public long capacity(ItemStack stack) {
		return this.tier.capacity();
	}

	public static long energy(ItemStack stack) {
		return PoweredItem.energy(stack);
	}

	public static void setEnergy(ItemStack stack, long energy) {
		PoweredItem.setEnergy(stack, energy);
	}

	/** The rocket boots the player is wearing, or EMPTY. */
	public static ItemStack worn(Player player) {
		ItemStack feet = player.getItemBySlot(EquipmentSlot.FEET);
		return feet.getItem() instanceof RocketBootsItem ? feet : ItemStack.EMPTY;
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
		builder.accept(PoweredItem.chargeLine(stack, this.tier.thrustCost()));
		builder.accept(Component.translatable("tooltip.solarquarry.rocket_boots.flight",
			String.format(java.util.Locale.ROOT, "%.1f", this.tier.climb() * 20.0), this.tier.thrustCost(),
			Math.round(this.tier.flightSeconds())).withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("tooltip.solarquarry.rocket_boots.fall", Math.round(this.tier.fallProtection() * 100), RocketTier.FALL_COST_PER_POINT)
			.withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("tooltip.solarquarry.rocket_boots.use").withStyle(ChatFormatting.DARK_GRAY));
	}
}
