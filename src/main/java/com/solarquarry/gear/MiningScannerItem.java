package com.solarquarry.gear;

import com.solarquarry.item.PoweredItem;
import com.solarquarry.item.SlimeProspectorItem;
import com.solarquarry.item.TooltipLines;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Advanced Mining Scanner: scans a sphere of {@link #RADIUS} blocks around you (only loaded blocks; sections without
 * ores are skipped) and reports every ore type with its count, the nearest one's coordinates and direction. The Ore
 * Prospector only counts a chunk column; this finds where to dig.
 */
public class MiningScannerItem extends Item implements TooltipLines, PoweredItem {
	public static final long CAPACITY = 50_000;
	public static final long SCAN_COST = 2_000;
	public static final int RADIUS = 12;
	public static final int COOLDOWN = 60;

	public MiningScannerItem(Properties properties) {
		super(properties);
	}

	@Override
	public long capacity(ItemStack stack) {
		return CAPACITY;
	}

	/** One ore type found: count and nearest position. */
	public record Found(Block block, int count, BlockPos nearest) {
	}

	/** Every ore within the radius of {@code center}, most common first. */
	public static List<Found> scan(ServerLevel level, BlockPos center) {
		Object2IntOpenHashMap<Block> counts = new Object2IntOpenHashMap<>();
		Map<Block, BlockPos> nearest = new HashMap<>();
		Map<Block, Double> nearestDist = new HashMap<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int r = RADIUS;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				for (int dy = -r; dy <= r; dy++) {
					double d = dx * dx + dy * dy + dz * dz;
					if (d > r * r) {
						continue;
					}

					pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					if (pos.getY() < level.getMinY() || pos.getY() > level.getMaxY() || !level.isLoaded(pos)) {
						continue;
					}

					BlockState state = level.getBlockState(pos);
					if (!state.is(ConventionalBlockTags.ORES)) {
						continue;
					}

					counts.addTo(state.getBlock(), 1);
					if (d < nearestDist.getOrDefault(state.getBlock(), Double.MAX_VALUE)) {
						nearestDist.put(state.getBlock(), d);
						nearest.put(state.getBlock(), pos.immutable());
					}
				}
			}
		}

		List<Found> found = new ArrayList<>();
		counts.forEach((block, count) -> found.add(new Found(block, count, nearest.get(block))));
		found.sort((a, b) -> Integer.compare(b.count(), a.count()));
		return found;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}

		if (level instanceof ServerLevel server) {
			if (!PoweredItem.use(stack, SCAN_COST)) {
				player.sendOverlayMessage(Component.translatable("solarquarry.powered.needs_charge", stack.getHoverName(), SCAN_COST).withStyle(ChatFormatting.RED));
				return InteractionResult.FAIL;
			}

			List<Found> found = scan(server, player.blockPosition());
			player.sendSystemMessage(Component.translatable("solarquarry.mining_scanner.header", RADIUS).withStyle(ChatFormatting.AQUA));
			if (found.isEmpty()) {
				player.sendSystemMessage(Component.translatable("solarquarry.prospector.none").withStyle(ChatFormatting.GRAY));
			}

			for (Found f : found) {
				BlockPos n = f.nearest();
				int blocks = (int) Math.round(Math.sqrt(n.distSqr(player.blockPosition())));
				player.sendSystemMessage(Component.translatable("solarquarry.mining_scanner.entry", f.block().getName(), f.count(), n.getX(), n.getY(), n.getZ(), blocks,
					Component.translatable("solarquarry.direction." + SlimeProspectorItem.direction(n.getX() - player.getX(), n.getZ() - player.getZ())),
					n.getY() < player.getBlockY() ? Component.translatable("solarquarry.mining_scanner.below", player.getBlockY() - n.getY())
						: Component.translatable("solarquarry.mining_scanner.above", n.getY() - player.getBlockY())));
			}

			player.getCooldowns().addCooldown(stack, COOLDOWN);
			level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.7F, 0.8F);
		}

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
		builder.accept(PoweredItem.chargeLine(stack, SCAN_COST));
		builder.accept(Component.translatable("tooltip.solarquarry.powered.scan_cost", SCAN_COST).withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("tooltip.solarquarry.mining_scanner", RADIUS).withStyle(ChatFormatting.GRAY));
	}
}
