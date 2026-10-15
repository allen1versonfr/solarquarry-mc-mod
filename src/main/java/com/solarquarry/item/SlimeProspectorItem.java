package com.solarquarry.item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.WorldgenRandom;

/**
 * Slime Prospector: tells whether the chunk you stand in is a slime chunk and lists the nearest slime chunks within
 * {@link #RADIUS} chunks, with coordinates and direction. It uses the game's own slime-chunk rule (the world seed and
 * {@link WorldgenRandom#seedSlimeChunk}), the same test slimes use to spawn underground, so no chunk is loaded or
 * scanned. Slime chunks are about the chunk, not about slimes being there right now. Overworld only.
 */
public class SlimeProspectorItem extends Item implements TooltipLines, PoweredItem {
	public static final long CAPACITY = 20_000;
	public static final long SCAN_COST = 300;
	public static final int RADIUS = 6;
	public static final int LISTED = 6;
	public static final int COOLDOWN = 20;
	/** The salt the game uses for slime chunks. */
	public static final long SLIME_SALT = 987234911L;

	public SlimeProspectorItem(Properties properties) {
		super(properties);
	}

	@Override
	public long capacity(ItemStack stack) {
		return CAPACITY;
	}

	/** The vanilla rule (see Slime#checkSlimeSpawnRules). */
	public static boolean isSlimeChunk(long seed, int chunkX, int chunkZ) {
		return WorldgenRandom.seedSlimeChunk(chunkX, chunkZ, seed, SLIME_SALT).nextInt(10) == 0;
	}

	/** Slime chunks within the radius, nearest first. */
	public static List<ChunkPos> nearby(long seed, ChunkPos center, int radius) {
		List<ChunkPos> found = new ArrayList<>();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (isSlimeChunk(seed, center.x() + dx, center.z() + dz)) {
					found.add(new ChunkPos(center.x() + dx, center.z() + dz));
				}
			}
		}

		found.sort(Comparator.comparingInt(c -> (c.x() - center.x()) * (c.x() - center.x()) + (c.z() - center.z()) * (c.z() - center.z())));
		return found;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}

		if (level instanceof ServerLevel server) {
			if (server.dimension() != Level.OVERWORLD) {
				player.sendSystemMessage(Component.translatable("solarquarry.slime_prospector.dimension").withStyle(ChatFormatting.GRAY));
				return InteractionResult.FAIL;
			}

			if (!ProspectorItem.pay(player, stack, SCAN_COST)) {
				return InteractionResult.FAIL;
			}

			long seed = server.getSeed();
			ChunkPos here = player.chunkPosition();
			boolean slimy = isSlimeChunk(seed, here.x(), here.z());
			player.sendSystemMessage(Component.translatable(slimy ? "solarquarry.slime_prospector.here_yes" : "solarquarry.slime_prospector.here_no", here.x(), here.z())
				.withStyle(slimy ? ChatFormatting.GREEN : ChatFormatting.GRAY));
			List<ChunkPos> found = nearby(seed, here, RADIUS);
			found.remove(here);
			if (found.isEmpty()) {
				player.sendSystemMessage(Component.translatable("solarquarry.slime_prospector.none", RADIUS).withStyle(ChatFormatting.GRAY));
			} else {
				player.sendSystemMessage(Component.translatable("solarquarry.slime_prospector.count", found.size(), RADIUS).withStyle(ChatFormatting.AQUA));
				for (ChunkPos chunk : found.subList(0, Math.min(LISTED, found.size()))) {
					int cx = chunk.getMiddleBlockX();
					int cz = chunk.getMiddleBlockZ();
					int blocks = (int) Math.round(Math.sqrt(Math.pow(cx - player.getX(), 2) + Math.pow(cz - player.getZ(), 2)));
					player.sendSystemMessage(Component.translatable("solarquarry.slime_prospector.entry", chunk.x(), chunk.z(), cx, cz, blocks,
						Component.translatable("solarquarry.direction." + direction(cx - player.getX(), cz - player.getZ()))).withStyle(ChatFormatting.WHITE));
				}
			}

			player.getCooldowns().addCooldown(stack, COOLDOWN);
			level.playSound(null, player.blockPosition(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.6F, 1.0F);
		}

		return InteractionResult.SUCCESS;
	}

	/** Compass direction (north is -z). */
	public static String direction(double dx, double dz) {
		double angle = Math.toDegrees(Math.atan2(dx, -dz));
		String[] names = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
		return names[(int) Math.floorMod(Math.round(angle / 45.0), 8)];
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
		builder.accept(Component.translatable("tooltip.solarquarry.slime_prospector", RADIUS).withStyle(ChatFormatting.GRAY));
	}
}
