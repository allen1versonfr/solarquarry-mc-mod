package com.solarquarry.item;

import com.solarquarry.blockentity.QuarryBlockEntity;
import com.solarquarry.config.SolarQuarryConfig;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Handheld ore prospector.
 * <ul>
 * <li>Use in the air: exact count of ore blocks in the chunk you stand in, from your feet down to the world bottom.</li>
 * <li>Use on a quarry: exact count of the ores left in its remaining work volume, scanned over a few ticks
 * ({@link ProspectorScans}).</li>
 * </ul>
 * Each use costs durability and starts a cooldown, so it cannot map the world for free.
 */
public class ProspectorItem extends Item implements MachineTool, TooltipLines {

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
			report(counts).forEach(player::sendSystemMessage);
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

		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			if (ProspectorScans.isBusy(serverPlayer)) {
				player.sendSystemMessage(Component.translatable("solarquarry.prospector.busy").withStyle(ChatFormatting.GRAY));
				return InteractionResult.FAIL;
			}

			long blocks = ProspectorScans.start(serverPlayer, serverLevel, quarry);
			player.sendSystemMessage(Component.translatable("solarquarry.prospector.scanning", blocks).withStyle(ChatFormatting.AQUA));
			this.spend(serverLevel, player, stack, context.getHand());
		}

		return InteractionResult.SUCCESS;
	}

	private void spend(ServerLevel level, Player player, ItemStack stack, InteractionHand hand) {
		player.getCooldowns().addCooldown(stack, SolarQuarryConfig.get().prospectorCooldownTicks());
		level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6F, 1.6F);
		stack.hurtAndBreak(1, player, hand);
	}

	/** The complete list: a total line, then every ore kind with its count, most common first (one per line). */
	static List<Component> report(Object2IntOpenHashMap<Block> counts) {
		List<Component> lines = new ArrayList<>();
		if (counts.isEmpty()) {
			lines.add(Component.translatable("solarquarry.prospector.none").withStyle(ChatFormatting.GRAY));
			return lines;
		}

		List<Object2IntOpenHashMap.Entry<Block>> entries = new ArrayList<>(counts.object2IntEntrySet());
		entries.sort((a, b) -> b.getIntValue() != a.getIntValue() ? Integer.compare(b.getIntValue(), a.getIntValue())
			: a.getKey().getName().getString().compareTo(b.getKey().getName().getString()));
		long total = 0;
		for (var entry : entries) {
			total += entry.getIntValue();
		}

		lines.add(Component.translatable("solarquarry.prospector.total", total).withStyle(ChatFormatting.YELLOW));
		for (var entry : entries) {
			MutableComponent line = Component.literal("  ").append(entry.getKey().getName().copy().withStyle(ChatFormatting.WHITE));
			line.append(Component.literal(" x" + entry.getIntValue()).withStyle(ChatFormatting.GRAY));
			lines.add(line);
		}

		return lines;
	}

	@Override
	public void addTooltipLines(ItemStack stack, Consumer<Component> builder) {
		builder.accept(Component.translatable("tooltip.solarquarry.prospector.chunk").withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("tooltip.solarquarry.prospector.quarry").withStyle(ChatFormatting.GRAY));
	}
}
