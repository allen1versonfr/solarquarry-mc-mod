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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Emergency Energy Injector: a single-use charge of {@link #AMOUNT} E for the emptiest powered item you carry or
 * wear. It has no crafting recipe and cannot be refilled, so it adds a fixed, limited amount of energy to the world.
 */
public class EnergyInjectorItem extends Item implements TooltipLines {
	public static final long AMOUNT = 20_000;

	public EnergyInjectorItem(Properties properties) {
		super(properties);
	}

	/** The powered item missing the most energy, or EMPTY when all are full. */
	public static ItemStack neediest(Player player) {
		ItemStack best = ItemStack.EMPTY;
		long bestRoom = 0;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = player.getItemBySlot(slot);
			long room = PoweredItem.charge(stack, Long.MAX_VALUE, true);
			if (room > bestRoom) {
				best = stack;
				bestRoom = room;
			}
		}

		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			long room = PoweredItem.charge(stack, Long.MAX_VALUE, true);
			if (room > bestRoom) {
				best = stack;
				bestRoom = room;
			}
		}

		return best;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack injector = player.getItemInHand(hand);
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		ItemStack target = neediest(player);
		if (target.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("solarquarry.injector.nothing").withStyle(ChatFormatting.GRAY));
			return InteractionResult.FAIL;
		}

		long given = PoweredItem.charge(target, AMOUNT, false);
		injector.consume(1, player);
		player.sendOverlayMessage(Component.translatable("solarquarry.injector.used", target.getHoverName(), EnergyFormat.format(given)).withStyle(ChatFormatting.GREEN));
		level.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.5F, 1.5F);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void addTooltipLines(ItemStack stack, Consumer<Component> builder) {
		builder.accept(Component.translatable("tooltip.solarquarry.injector", EnergyFormat.format(AMOUNT)).withStyle(ChatFormatting.GRAY));
	}
}
