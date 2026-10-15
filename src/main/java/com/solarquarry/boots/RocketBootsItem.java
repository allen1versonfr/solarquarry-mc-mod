package com.solarquarry.boots;

import com.solarquarry.registry.ModItems;
import com.solarquarry.util.EnergyFormat;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Rocket Boots: crouch and jump to fire; energy is stored on the item and charged from a Solar Quarry battery. */
public class RocketBootsItem extends Item implements com.solarquarry.item.TooltipLines {
	private final RocketTier tier;

	public RocketBootsItem(RocketTier tier, Properties properties) {
		super(properties);
		this.tier = tier;
	}

	public RocketTier tier() {
		return this.tier;
	}

	public static long energy(ItemStack stack) {
		return Math.max(0L, stack.getOrDefault(ModItems.ENERGY, 0L));
	}

	public static void setEnergy(ItemStack stack, long energy) {
		if (stack.getItem() instanceof RocketBootsItem boots) {
			stack.set(ModItems.ENERGY, Math.clamp(energy, 0L, boots.tier.capacity()));
		}
	}

	/** The rocket boots the player is wearing, or EMPTY. */
	public static ItemStack worn(Player player) {
		ItemStack feet = player.getItemBySlot(EquipmentSlot.FEET);
		return feet.getItem() instanceof RocketBootsItem ? feet : ItemStack.EMPTY;
	}

	@Override
	public void addTooltipLines(ItemStack stack, Consumer<Component> builder) {
		builder.accept(Component.translatable("tooltip.solarquarry.rocket_boots.energy", EnergyFormat.format(energy(stack)), EnergyFormat.format(this.tier.capacity()))
			.withStyle(energy(stack) >= RocketTier.JUMP_COST ? ChatFormatting.GREEN : ChatFormatting.RED));
		builder.accept(Component.translatable("tooltip.solarquarry.rocket_boots.jump",
			String.format(java.util.Locale.ROOT, "%.1f", RocketTier.jumpHeight(this.tier.launch())), RocketTier.JUMP_COST).withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("tooltip.solarquarry.rocket_boots.fall", Math.round(this.tier.fallProtection() * 100), RocketTier.FALL_COST_PER_POINT)
			.withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("tooltip.solarquarry.rocket_boots.use").withStyle(ChatFormatting.DARK_GRAY));
	}
}
