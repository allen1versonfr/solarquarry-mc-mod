package com.solarquarry.gear;

import com.solarquarry.item.PoweredItem;
import com.solarquarry.item.TooltipLines;
import com.solarquarry.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Advanced Powered Pickaxe: a diamond-grade pickaxe with an electric <b>area mode</b> (crouch and use to toggle).
 * In area mode, breaking a block also breaks the blocks around it in a 3 x 3 square facing you, each for
 * {@link #AREA_COST} E and normal durability. The extra blocks are broken through the game's own player block
 * breaking, so drops, claims and protection rules apply; blocks that are harder than the one you broke, unbreakable
 * or need a better tool are left alone. Without energy it is still a normal pickaxe.
 */
public class PoweredPickaxeItem extends Item implements TooltipLines, PoweredItem {
	public static final long CAPACITY = 100_000;
	public static final long AREA_COST = 100;
	private static final ThreadLocal<Boolean> BREAKING = ThreadLocal.withInitial(() -> false);

	public PoweredPickaxeItem(Properties properties) {
		super(properties);
	}

	@Override
	public long capacity(ItemStack stack) {
		return CAPACITY;
	}

	public static boolean areaMode(ItemStack stack) {
		return Boolean.TRUE.equals(stack.get(ModItems.AREA_MODE));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}

		ItemStack stack = player.getItemInHand(hand);
		if (!level.isClientSide()) {
			boolean on = !areaMode(stack);
			stack.set(ModItems.AREA_MODE, on);
			player.sendOverlayMessage(Component.translatable(on ? "solarquarry.powered_pickaxe.area_on" : "solarquarry.powered_pickaxe.area_off")
				.withStyle(on ? ChatFormatting.GOLD : ChatFormatting.GRAY));
		}

		return InteractionResult.SUCCESS;
	}

	/** The other eight positions of the 3 x 3 square around {@code center}, facing the player. */
	public static List<BlockPos> area(BlockPos center, Player player) {
		Direction.Axis normal;
		float pitch = player.getXRot();
		if (pitch > 45 || pitch < -45) {
			normal = Direction.Axis.Y;
		} else {
			normal = player.getDirection().getAxis();
		}

		List<BlockPos> list = new ArrayList<>();
		for (int a = -1; a <= 1; a++) {
			for (int b = -1; b <= 1; b++) {
				if (a == 0 && b == 0) {
					continue;
				}

				list.add(switch (normal) {
					case Y -> center.offset(a, 0, b);
					case X -> center.offset(0, a, b);
					case Z -> center.offset(a, b, 0);
				});
			}
		}

		return list;
	}

	/** Called after the player broke {@code center} with this pickaxe (server side). */
	public static void afterBreak(ServerPlayer player, BlockPos center, BlockState broken) {
		ItemStack tool = player.getMainHandItem();
		if (BREAKING.get() || !(tool.getItem() instanceof PoweredPickaxeItem) || !areaMode(tool) || player.isCreative()) {
			return;
		}

		float hardness = broken.getDestroySpeed(player.level(), center);
		BREAKING.set(true);
		try {
			for (BlockPos pos : area(center, player)) {
				if (tool.isEmpty() || !(tool.getItem() instanceof PoweredPickaxeItem) || PoweredItem.energy(tool) < AREA_COST) {
					break;
				}

				BlockState state = player.level().getBlockState(pos);
				float h = state.getDestroySpeed(player.level(), pos);
				if (state.isAir() || h < 0 || h > hardness + 1.5F || !tool.isCorrectToolForDrops(state) || !state.getFluidState().isEmpty()) {
					continue;
				}

				if (player.gameMode.destroyBlock(pos)) {
					PoweredItem.use(tool, AREA_COST);
				}
			}
		} finally {
			BREAKING.set(false);
		}
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return true;
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
		builder.accept(PoweredItem.chargeLine(stack, AREA_COST));
		builder.accept(Component.translatable(areaMode(stack) ? "tooltip.solarquarry.powered_pickaxe.on" : "tooltip.solarquarry.powered_pickaxe.off", AREA_COST)
			.withStyle(areaMode(stack) ? ChatFormatting.GOLD : ChatFormatting.GRAY));
		builder.accept(Component.translatable("tooltip.solarquarry.powered_pickaxe.use").withStyle(ChatFormatting.DARK_GRAY));
	}
}
