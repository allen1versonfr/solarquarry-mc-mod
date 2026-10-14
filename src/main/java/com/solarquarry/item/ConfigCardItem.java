package com.solarquarry.item;

import com.solarquarry.blockentity.ItemPipeBlockEntity;
import com.solarquarry.blockentity.QuarryBlockEntity;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Stores machine settings as a preset.
 * Sneak + use on a quarry or filter pipe: copy its settings. Use: apply the stored settings. Sneak + use in the air: clear.
 * Presets never contain items, energy or upgrades.
 */
public class ConfigCardItem extends Item implements MachineTool {
	public ConfigCardItem(Properties properties) {
		super(properties);
	}

	public static CompoundTag preset(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag().getCompoundOrEmpty("SolarQuarryPreset");
	}

	private static void store(ItemStack stack, CompoundTag preset) {
		CompoundTag root = new CompoundTag();
		root.put("SolarQuarryPreset", preset);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		Player player = context.getPlayer();
		if (player == null) {
			return InteractionResult.PASS;
		}

		BlockPos pos = context.getClickedPos();
		BlockEntity be = level.getBlockEntity(pos);
		boolean filter = be instanceof ItemPipeBlockEntity pipe && pipe.isFilter();
		if (!(be instanceof QuarryBlockEntity) && !filter) {
			return InteractionResult.PASS;
		}

		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		ItemStack stack = context.getItemInHand();
		Component message;
		if (player.isSecondaryUseActive()) {
			if (be instanceof QuarryBlockEntity quarry) {
				store(stack, quarry.writePreset());
			} else {
				store(stack, ((ItemPipeBlockEntity) be).writePreset(level.registryAccess()));
			}

			message = Component.translatable("solarquarry.card.copied");
		} else {
			CompoundTag preset = preset(stack);
			if (preset.isEmpty()) {
				message = Component.translatable("solarquarry.card.empty");
			} else if (!player.mayBuild() || !level.mayInteract(player, pos)) {
				message = Component.translatable("solarquarry.card.no_permission");
			} else if (be instanceof QuarryBlockEntity quarry) {
				if (player instanceof ServerPlayer serverPlayer && !quarry.isAuthorized(serverPlayer)) {
					message = Component.translatable("solarquarry.card.not_owner", quarry.getOwnerName());
				} else {
					message = quarry.applyPreset(preset);
				}
			} else {
				message = ((ItemPipeBlockEntity) be).applyPreset(preset, level.registryAccess());
			}
		}

		player.sendOverlayMessage(message);
		level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4F, 1.6F);
		return InteractionResult.SUCCESS_SERVER;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.isSecondaryUseActive() && stack.has(DataComponents.CUSTOM_DATA)) {
			if (!level.isClientSide()) {
				stack.remove(DataComponents.CUSTOM_DATA);
				player.sendOverlayMessage(Component.translatable("solarquarry.card.cleared"));
			}

			return InteractionResult.SUCCESS;
		}

		return InteractionResult.PASS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		CompoundTag preset = preset(stack);
		String kind = preset.getStringOr("Kind", "");
		if (kind.equals("quarry")) {
			builder.accept(Component.translatable("tooltip.solarquarry.card.quarry",
				Component.translatable("solarquarry.mode." + preset.getStringOr("QuarryMode", "strip_mine")),
				preset.getIntOr("Width", 16), preset.getIntOr("Length", 16)).withStyle(ChatFormatting.GOLD));
		} else if (kind.equals("filter")) {
			builder.accept(Component.translatable("tooltip.solarquarry.card.filter",
				Component.translatable(preset.getBooleanOr("Whitelist", false) ? "gui.solarquarry.whitelist" : "gui.solarquarry.blacklist"),
				preset.getIntOr("Priority", 0)).withStyle(ChatFormatting.GOLD));
		} else {
			builder.accept(Component.translatable("tooltip.solarquarry.card.blank").withStyle(ChatFormatting.DARK_GRAY));
		}

		builder.accept(Component.translatable("tooltip.solarquarry.card.usage").withStyle(ChatFormatting.GRAY));
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return !preset(stack).isEmpty();
	}
}
