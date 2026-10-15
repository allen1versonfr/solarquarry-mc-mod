package com.solarquarry.item;

import com.solarquarry.registry.ModItems;
import com.solarquarry.util.EnergyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * An item that stores Solar Quarry energy (E) on itself, in the {@link ModItems#ENERGY} component, so the charge
 * stays with the item when it is moved, dropped, stored or traded. Charged in the Charging Station or by using the
 * item on a Solar Quarry battery. Durability and enchantments are separate components and never touched by charging.
 */
public interface PoweredItem {
	/** Energy the item holds when full. */
	long capacity(ItemStack stack);

	/** Highest rate (E per tick) the item accepts while charging. */
	default long chargeRate(ItemStack stack) {
		return Math.max(100L, this.capacity(stack) / 100L);
	}

	static boolean isPowered(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof PoweredItem;
	}

	static long energy(ItemStack stack) {
		return Math.max(0L, stack.getOrDefault(ModItems.ENERGY, 0L));
	}

	static long capacityOf(ItemStack stack) {
		return stack.getItem() instanceof PoweredItem powered ? powered.capacity(stack) : 0L;
	}

	static void setEnergy(ItemStack stack, long energy) {
		if (stack.getItem() instanceof PoweredItem powered) {
			stack.set(ModItems.ENERGY, Math.clamp(energy, 0L, powered.capacity(stack)));
		}
	}

	/** Takes {@code amount} if the item holds it; returns false (and takes nothing) otherwise. */
	static boolean use(ItemStack stack, long amount) {
		long energy = energy(stack);
		if (amount < 0 || energy < amount) {
			return false;
		}

		setEnergy(stack, energy - amount);
		return true;
	}

	/** Adds up to {@code amount} (limited by capacity); returns how much was accepted. */
	static long charge(ItemStack stack, long amount, boolean simulate) {
		if (!(stack.getItem() instanceof PoweredItem powered) || amount <= 0) {
			return 0L;
		}

		long energy = energy(stack);
		long accepted = Math.max(0L, Math.min(amount, powered.capacity(stack) - energy));
		if (!simulate && accepted > 0) {
			stack.set(ModItems.ENERGY, energy + accepted);
		}

		return accepted;
	}

	static boolean isFull(ItemStack stack) {
		return energy(stack) >= capacityOf(stack);
	}

	/** Tooltip line: "Charge: 12.0k / 50.0k E". */
	static Component chargeLine(ItemStack stack, long minimumUse) {
		long energy = energy(stack);
		return Component.translatable("tooltip.solarquarry.powered.charge", EnergyFormat.format(energy), EnergyFormat.format(capacityOf(stack)))
			.withStyle(energy >= minimumUse ? ChatFormatting.GREEN : ChatFormatting.RED);
	}

	/** Charge bar shown under the item (unless the item shows a damaged durability bar). */
	static int barWidth(ItemStack stack) {
		long capacity = Math.max(1L, capacityOf(stack));
		return Math.round(13.0F * energy(stack) / capacity);
	}

	static int barColor(ItemStack stack) {
		float f = energy(stack) / (float) Math.max(1L, capacityOf(stack));
		return Mth.hsvToRgb(0.13F + 0.05F * f, 0.9F, 1.0F); // amber when low, yellow when full
	}
}
