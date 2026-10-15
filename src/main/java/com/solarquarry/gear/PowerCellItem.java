package com.solarquarry.gear;

import com.solarquarry.item.PoweredItem;
import com.solarquarry.item.TooltipLines;
import com.solarquarry.util.EnergyFormat;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Portable Power Cell / Prototype Battery: carried energy. Use it to pour its charge into the powered gear you carry
 * and wear (boots, prospectors, helmets, scanners), up to {@link #transfer} E per use. Other cells are not filled, so
 * cells cannot pass energy around in a circle. Recharge it in a Charging Station or on a battery.
 */
public class PowerCellItem extends Item implements TooltipLines, PoweredItem {
	private final long capacity;
	private final long transfer;

	public PowerCellItem(long capacity, long transfer, Properties properties) {
		super(properties);
		this.capacity = capacity;
		this.transfer = transfer;
	}

	@Override
	public long capacity(ItemStack stack) {
		return this.capacity;
	}

	@Override
	public long chargeRate(ItemStack stack) {
		return Math.max(200L, this.capacity / 200L);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack cell = player.getItemInHand(hand);
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		long budget = Math.min(this.transfer, PoweredItem.energy(cell));
		long given = 0;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize() && budget > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack == cell || !PoweredItem.isPowered(stack) || stack.getItem() instanceof PowerCellItem) {
				continue;
			}

			long moved = PoweredItem.charge(stack, budget, false);
			budget -= moved;
			given += moved;
		}

		if (given <= 0) {
			player.sendOverlayMessage(Component.translatable(PoweredItem.energy(cell) <= 0 ? "solarquarry.power_cell.empty" : "solarquarry.power_cell.nothing")
				.withStyle(ChatFormatting.GRAY));
			return InteractionResult.FAIL;
		}

		PoweredItem.setEnergy(cell, PoweredItem.energy(cell) - given);
		player.sendOverlayMessage(Component.translatable("solarquarry.power_cell.gave", EnergyFormat.format(given)).withStyle(ChatFormatting.GREEN));
		level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.4F, 1.6F);
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return PoweredItem.barWidth(stack);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return PoweredItem.barColor(stack);
	}

	@Override
	public void addTooltipLines(ItemStack stack, Consumer<Component> builder) {
		builder.accept(PoweredItem.chargeLine(stack, 1));
		builder.accept(Component.translatable("tooltip.solarquarry.power_cell", EnergyFormat.format(this.transfer)).withStyle(ChatFormatting.GRAY));
	}
}
