package com.solarquarry.item;

import com.solarquarry.blockentity.QuarryBlockEntity;
import com.solarquarry.quarry.MiningPattern;
import com.solarquarry.quarry.QuarryMode;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Handheld ore prospector.
 * <ul>
 * <li>Use in the air: exact count of ore blocks in the chunk you stand in, from your feet down to the world bottom.</li>
 * <li>Use on a quarry: estimate of the ores left in its remaining work volume, from a sample of 1 in 16 columns.</li>
 * </ul>
 * Each use costs durability and starts a cooldown, so it cannot map the world for free.
 */
public class ProspectorItem extends Item implements MachineTool {
	public static final int COOLDOWN_TICKS = 100;
	private static final int MAX_SAMPLES = 200_000;
	private static final int TOP_ENTRIES = 6;

	public ProspectorItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}

		if (level instanceof ServerLevel serverLevel) {
			ChunkPos chunk = player.chunkPosition();
			int top = Math.min(level.getMaxY(), player.getBlockY());
			Object2IntOpenHashMap<Block> counts = new Object2IntOpenHashMap<>();
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
				for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
					for (int y = top; y >= level.getMinY(); y--) {
						BlockState state = level.getBlockState(pos.set(x, y, z));
						if (state.is(ConventionalBlockTags.ORES)) {
							counts.addTo(state.getBlock(), 1);
						}
					}
				}
			}

			player.sendSystemMessage(Component.translatable("solarquarry.prospector.chunk", chunk.x(), chunk.z(), top, level.getMinY()).withStyle(ChatFormatting.AQUA));
			player.sendSystemMessage(report(counts, 1.0, false));
			this.spend(serverLevel, player, stack, hand);
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		Level level = context.getLevel();
		if (player == null || !(level.getBlockEntity(context.getClickedPos()) instanceof QuarryBlockEntity quarry)) {
			return InteractionResult.PASS;
		}

		ItemStack stack = context.getItemInHand();
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}

		if (level instanceof ServerLevel serverLevel) {
			MiningPattern pattern = quarry.pattern();
			long start = quarry.getQuarryMode() == QuarryMode.ORE_PRIORITY && !quarry.isFinished() ? quarry.pattern().layerStart(quarry.getScanStep()) : pattern.layerStart(quarry.getStep());
			Object2IntOpenHashMap<Block> counts = new Object2IntOpenHashMap<>();
			long remaining = Math.max(0, pattern.total() - start);
			int samples = 0;
			// Sample one cell in sixteen (every 4th lateral and forward position) of the remaining volume.
			for (long step = start; step < pattern.total() && samples < MAX_SAMPLES; step++) {
				MiningPattern.Cell cell = pattern.cell(step);
				if (cell.lateral() % 4 != 0 || cell.forward() % 4 != 1) {
					continue;
				}

				samples++;
				BlockPos pos = quarry.toWorld(cell, pattern);
				if (!level.isLoaded(pos)) {
					continue;
				}

				BlockState state = level.getBlockState(pos);
				if (state.is(ConventionalBlockTags.ORES)) {
					counts.addTo(state.getBlock(), 1);
				}
			}

			double scale = samples == 0 ? 0 : (double) remaining / samples;
			BlockPos qp = quarry.getBlockPos();
			player.sendSystemMessage(Component.translatable("solarquarry.prospector.quarry", qp.getX(), qp.getY(), qp.getZ(), samples, remaining).withStyle(ChatFormatting.AQUA));
			player.sendSystemMessage(report(counts, scale, true));
			player.sendSystemMessage(Component.translatable("solarquarry.prospector.estimate_note").withStyle(ChatFormatting.DARK_GRAY));
			this.spend(serverLevel, player, stack, context.getHand());
		}

		return InteractionResult.SUCCESS;
	}

	private void spend(ServerLevel level, Player player, ItemStack stack, InteractionHand hand) {
		player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
		level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6F, 1.6F);
		stack.hurtAndBreak(1, player, hand);
	}

	private static Component report(Object2IntOpenHashMap<Block> counts, double scale, boolean estimate) {
		if (counts.isEmpty()) {
			return Component.translatable("solarquarry.prospector.none").withStyle(ChatFormatting.GRAY);
		}

		List<Object2IntOpenHashMap.Entry<Block>> entries = new ArrayList<>(counts.object2IntEntrySet());
		entries.sort((a, b) -> Integer.compare(b.getIntValue(), a.getIntValue()));
		long total = 0;
		for (var entry : entries) {
			total += entry.getIntValue();
		}

		MutableComponent line = Component.translatable(estimate ? "solarquarry.prospector.total_estimate" : "solarquarry.prospector.total",
			Math.round(total * scale)).withStyle(ChatFormatting.YELLOW);
		for (int i = 0; i < Math.min(TOP_ENTRIES, entries.size()); i++) {
			var entry = entries.get(i);
			line.append(Component.literal(i == 0 ? "  " : ", ").withStyle(ChatFormatting.GRAY));
			line.append(entry.getKey().getName().copy().withStyle(ChatFormatting.WHITE));
			line.append(Component.literal((estimate ? " ~" : " x") + Math.round(entry.getIntValue() * scale)).withStyle(ChatFormatting.GRAY));
		}

		if (entries.size() > TOP_ENTRIES) {
			line.append(Component.translatable("solarquarry.prospector.more", entries.size() - TOP_ENTRIES).withStyle(ChatFormatting.GRAY));
		}

		return line;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.solarquarry.prospector.chunk").withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("tooltip.solarquarry.prospector.quarry").withStyle(ChatFormatting.GRAY));
	}
}
