package com.solarquarry.item;

import com.solarquarry.blockentity.QuarryBlockEntity;
import com.solarquarry.quarry.MiningPattern;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Exact ore count of a quarry's remaining work volume, spread over several ticks so even a 64 x 64 quarry down to
 * bedrock (about 1.6 million blocks) never stalls the server: at most {@link #BLOCKS_PER_TICK} blocks are read per
 * tick across all scans, one scan per player at a time. The volume is captured when the scan starts.
 */
public final class ProspectorScans {
	public static final int BLOCKS_PER_TICK = 65_536;
	private static final Map<UUID, Job> JOBS = new LinkedHashMap<>();

	private ProspectorScans() {
	}

	/** A running scan: the quarry's volume as it was when the scan started. */
	static final class Job {
		final UUID player;
		final ResourceKey<Level> dimension;
		final BlockPos quarry;
		final Direction forward;
		final MiningPattern pattern;
		final long end;
		long next;
		long scanned;
		long unloaded;
		final Object2IntOpenHashMap<Block> counts = new Object2IntOpenHashMap<>();

		Job(UUID player, ResourceKey<Level> dimension, BlockPos quarry, Direction forward, MiningPattern pattern, long start) {
			this.player = player;
			this.dimension = dimension;
			this.quarry = quarry.immutable();
			this.forward = forward;
			this.pattern = pattern;
			this.next = start;
			this.end = pattern.total();
		}

		long remaining() {
			return Math.max(0, this.end - this.next);
		}
	}

	public static boolean isBusy(ServerPlayer player) {
		return JOBS.containsKey(player.getUUID());
	}

	/** Starts a scan of the cells the quarry has not reached yet. Returns the number of blocks that will be scanned. */
	public static long start(ServerPlayer player, ServerLevel level, QuarryBlockEntity quarry) {
		MiningPattern pattern = quarry.pattern();
		long start = Math.max(0, Math.min(pattern.total(), quarry.getStep()));
		Job job = new Job(player.getUUID(), level.dimension(), quarry.getBlockPos(), quarry.getAreaDirection(), pattern, start);
		JOBS.put(player.getUUID(), job);
		return job.remaining();
	}

	public static void tick(MinecraftServer server) {
		if (JOBS.isEmpty()) {
			return;
		}

		int budget = BLOCKS_PER_TICK;
		Iterator<Job> iterator = JOBS.values().iterator();
		while (iterator.hasNext() && budget > 0) {
			Job job = iterator.next();
			ServerLevel level = server.getLevel(job.dimension);
			if (level == null) {
				iterator.remove();
				continue;
			}

			budget -= advance(level, job, budget);
			if (job.next >= job.end) {
				iterator.remove();
				ServerPlayer player = server.getPlayerList().getPlayer(job.player);
				if (player != null) {
					report(job, player::sendSystemMessage);
				}
			}
		}
	}

	/** Reads up to {@code budget} cells; returns how many were read. Package-visible for the self-test. */
	static int advance(ServerLevel level, Job job, int budget) {
		Direction lateral = job.forward.getClockWise();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int done = 0;
		while (done < budget && job.next < job.end) {
			MiningPattern.Cell cell = job.pattern.cell(job.next);
			int lat = job.pattern.lateralOffset(cell.lateral());
			pos.set(job.quarry.getX() + job.forward.getStepX() * cell.forward() + lateral.getStepX() * lat,
				job.quarry.getY() + cell.dy(),
				job.quarry.getZ() + job.forward.getStepZ() * cell.forward() + lateral.getStepZ() * lat);
			job.next++;
			done++;
			job.scanned++;
			if (!level.isLoaded(pos)) {
				job.unloaded++;
				continue;
			}

			BlockState state = level.getBlockState(pos);
			if (state.is(ConventionalBlockTags.ORES)) {
				job.counts.addTo(state.getBlock(), 1);
			}
		}

		return done;
	}

	static void report(Job job, Consumer<Component> out) {
		out.accept(Component.translatable("solarquarry.prospector.quarry_exact", job.quarry.getX(), job.quarry.getY(), job.quarry.getZ(), job.scanned)
			.withStyle(ChatFormatting.AQUA));
		out.accept(ProspectorItem.report(job.counts));
		if (job.unloaded > 0) {
			out.accept(Component.translatable("solarquarry.prospector.unloaded", job.unloaded).withStyle(ChatFormatting.GRAY));
		}
	}

	public static void clear() {
		JOBS.clear();
	}

	/** Self-test helper: runs a complete scan immediately and returns the ore total and the unloaded count. */
	public static long[] scanNow(ServerLevel level, QuarryBlockEntity quarry, List<Component> out) {
		MiningPattern pattern = quarry.pattern();
		Job job = new Job(new UUID(0, 0), level.dimension(), quarry.getBlockPos(), quarry.getAreaDirection(), pattern, Math.max(0, quarry.getStep()));
		while (job.next < job.end) {
			advance(level, job, BLOCKS_PER_TICK);
		}

		report(job, out::add);
		long total = 0;
		for (var entry : job.counts.object2IntEntrySet()) {
			total += entry.getIntValue();
		}

		return new long[] {total, job.unloaded};
	}
}
