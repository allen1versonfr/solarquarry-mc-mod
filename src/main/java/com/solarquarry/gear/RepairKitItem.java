package com.solarquarry.gear;

import com.solarquarry.SolarQuarry;
import com.solarquarry.item.TooltipLines;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
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
 * Industrial Repair Kit: restores {@link #REPAIR} durability to the most worn Solar Quarry tool or armour piece you
 * carry or wear (Helionite gear, Rocket Boots, helmets, hammers, pickaxes). Vanilla gear is not repaired. One kit is
 * used up per repair.
 */
public class RepairKitItem extends Item implements TooltipLines {
	public static final int REPAIR = 300;

	public RepairKitItem(Properties properties) {
		super(properties);
	}

	/** Whether the kit can repair this stack. */
	public static boolean repairs(ItemStack stack) {
		return !stack.isEmpty() && stack.isDamageableItem() && stack.isDamaged()
			&& BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(SolarQuarry.MOD_ID);
	}

	/** The most damaged repairable stack the player has, or EMPTY. */
	public static ItemStack mostWorn(Player player) {
		ItemStack best = ItemStack.EMPTY;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = player.getItemBySlot(slot);
			if (repairs(stack) && (best.isEmpty() || stack.getDamageValue() > best.getDamageValue())) {
				best = stack;
			}
		}

		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (repairs(stack) && (best.isEmpty() || stack.getDamageValue() > best.getDamageValue())) {
				best = stack;
			}
		}

		return best;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack kit = player.getItemInHand(hand);
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		ItemStack target = mostWorn(player);
		if (target.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("solarquarry.repair_kit.nothing").withStyle(ChatFormatting.GRAY));
			return InteractionResult.FAIL;
		}

		int before = target.getDamageValue();
		target.setDamageValue(Math.max(0, before - REPAIR));
		kit.consume(1, player);
		player.sendOverlayMessage(Component.translatable("solarquarry.repair_kit.repaired", target.getHoverName(), before - target.getDamageValue())
			.withStyle(ChatFormatting.GREEN));
		level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.5F, 1.4F);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void addTooltipLines(ItemStack stack, Consumer<Component> builder) {
		builder.accept(Component.translatable("tooltip.solarquarry.repair_kit", REPAIR).withStyle(ChatFormatting.GRAY));
	}
}
