package com.solarquarry.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

/**
 * Liquid Prospector: scans the whole chunk you stand in (every height) for water and lava and reports the source
 * blocks, flowing blocks and the nearest source of each. The scan reads the chunk the player is in, which is always
 * loaded, and skips sections whose palette holds no liquid at all, so it is complete and cheap. Costs energy per scan.
 */
public class LiquidProspectorItem extends Item implements TooltipLines, PoweredItem {
	public static final long CAPACITY = 20_000;
	public static final long SCAN_COST = 500;
	public static final int COOLDOWN = 40;

	public LiquidProspectorItem(Properties properties) {
		super(properties);
	}

	@Override
	public long capacity(ItemStack stack) {
		return CAPACITY;
	}

	/** What one scan found for one liquid. */
	public static final class Found {
		public int sources;
		public int flowing;
		public @Nullable BlockPos nearest;
		double nearestDist = Double.MAX_VALUE;

		void add(BlockPos pos, boolean source, BlockPos from) {
			if (source) {
				this.sources++;
				double d = pos.distSqr(from);
				if (d < this.nearestDist) {
					this.nearestDist = d;
					this.nearest = pos.immutable();
				}
			} else {
				this.flowing++;
			}
		}
	}

	public record Scan(ChunkPos chunk, Found water, Found lava, int other) {
	}

	/** Scans the loaded chunk at {@code from}. */
	public static Scan scan(ServerLevel level, BlockPos from) {
		LevelChunk chunk = level.getChunkAt(from);
		Found water = new Found();
		Found lava = new Found();
		int other = 0;
		LevelChunkSection[] sections = chunk.getSections();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < sections.length; i++) {
			LevelChunkSection section = sections[i];
			if (section.hasOnlyAir() || !section.getStates().maybeHas(s -> !s.getFluidState().isEmpty())) {
				continue;
			}

			int baseY = level.getSectionYFromSectionIndex(i) << 4;
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						FluidState fluid = section.getFluidState(x, y, z);
						if (fluid.isEmpty()) {
							continue;
						}

						pos.set(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z);
						if (fluid.is(FluidTags.WATER)) {
							water.add(pos, fluid.isSource(), from);
						} else if (fluid.is(FluidTags.LAVA)) {
							lava.add(pos, fluid.isSource(), from);
						} else {
							other++;
						}
					}
				}
			}
		}

		return new Scan(chunk.getPos(), water, lava, other);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}

		if (level instanceof ServerLevel server) {
			if (!ProspectorItem.pay(player, stack, SCAN_COST)) {
				return InteractionResult.FAIL;
			}

			Scan scan = scan(server, player.blockPosition());
			player.sendSystemMessage(Component.translatable("solarquarry.liquid_prospector.chunk", scan.chunk().x(), scan.chunk().z()).withStyle(ChatFormatting.AQUA));
			report(player, "water", scan.water(), ChatFormatting.BLUE);
			report(player, "lava", scan.lava(), ChatFormatting.GOLD);
			if (scan.other() > 0) {
				player.sendSystemMessage(Component.translatable("solarquarry.liquid_prospector.other", scan.other()).withStyle(ChatFormatting.GRAY));
			}

			if (scan.water().sources + scan.water().flowing + scan.lava().sources + scan.lava().flowing + scan.other() == 0) {
				player.sendSystemMessage(Component.translatable("solarquarry.liquid_prospector.none").withStyle(ChatFormatting.GRAY));
			}

			player.getCooldowns().addCooldown(stack, COOLDOWN);
			level.playSound(null, player.blockPosition(), SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 0.5F, 1.4F);
		}

		return InteractionResult.SUCCESS;
	}

	private static void report(Player player, String kind, Found found, ChatFormatting color) {
		if (found.sources + found.flowing == 0) {
			return;
		}

		Component name = Component.translatable("solarquarry.liquid_prospector." + kind).withStyle(color);
		if (found.nearest != null) {
			player.sendSystemMessage(Component.translatable("solarquarry.liquid_prospector.found", name, found.sources, found.flowing,
				found.nearest.getX(), found.nearest.getY(), found.nearest.getZ()));
		} else {
			player.sendSystemMessage(Component.translatable("solarquarry.liquid_prospector.flowing_only", name, found.flowing));
		}
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
		builder.accept(Component.translatable("tooltip.solarquarry.liquid_prospector").withStyle(ChatFormatting.GRAY));
	}
}
