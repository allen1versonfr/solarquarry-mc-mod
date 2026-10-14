package com.solarquarry.quarry;

import com.mojang.serialization.Codec;
import com.solarquarry.SolarQuarry;
import com.solarquarry.blockentity.QuarryBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/**
 * Chunk loading for quarries with a Chunk Loader Upgrade.
 * <ul>
 * <li>Tickets are timed (5 s) and refreshed by the running quarry, so nothing stays loaded once the quarry stops,
 * loses power, loses the upgrade or is removed. Tickets themselves are never written to the save file.</li>
 * <li>The quarry keeps the 3 x 3 chunks around itself ticking (panels and batteries there keep working) and loads the
 * chunk it is mining (plus its neighbours).</li>
 * <li>Each dimension remembers which quarries were loading chunks (a Fabric world attachment); when the server starts,
 * those quarries get their tickets back so they resume without a player nearby.</li>
 * </ul>
 */
public final class QuarryChunkLoading {
	/** Timed ticket: loads and simulates, keeps the dimension active; not persisted. */
	public static final TicketType TICKET = Registry.register(BuiltInRegistries.TICKET_TYPE, SolarQuarry.id("quarry"),
		new TicketType(100L, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
	/** Radius 2 around the quarry: its own 3 x 3 chunks are block-ticking. */
	public static final int MACHINE_RADIUS = 2;
	/** Radius 1 around the chunk being mined: that chunk is fully loaded. */
	public static final int TARGET_RADIUS = 1;
	private static final int PRUNE_INTERVAL = 200;

	public static final AttachmentType<List<Long>> LOADERS = AttachmentRegistry.createPersistent(SolarQuarry.id("chunk_loading_quarries"), Codec.LONG.listOf());

	private QuarryChunkLoading() {
	}

	public static void init() {
	}

	public static void keepMachineLoaded(ServerLevel level, BlockPos quarry) {
		level.getChunkSource().addTicketWithRadius(TICKET, ChunkPos.containing(quarry), MACHINE_RADIUS);
	}

	public static void loadTarget(ServerLevel level, BlockPos target) {
		level.getChunkSource().addTicketWithRadius(TICKET, ChunkPos.containing(target), TARGET_RADIUS);
	}

	public static boolean isRegistered(ServerLevel level, BlockPos quarry) {
		List<Long> list = level.getAttached(LOADERS);
		return list != null && list.contains(quarry.asLong());
	}

	public static void register(ServerLevel level, BlockPos quarry) {
		List<Long> list = level.getAttachedOrElse(LOADERS, List.of());
		long key = quarry.asLong();
		if (!list.contains(key)) {
			List<Long> copy = new ArrayList<>(list);
			copy.add(key);
			level.setAttached(LOADERS, List.copyOf(copy));
		}
	}

	public static void unregister(ServerLevel level, BlockPos quarry) {
		List<Long> list = level.getAttached(LOADERS);
		long key = quarry.asLong();
		if (list != null && list.contains(key)) {
			List<Long> copy = new ArrayList<>(list);
			copy.remove(key);
			if (copy.isEmpty()) {
				level.removeAttached(LOADERS);
			} else {
				level.setAttached(LOADERS, List.copyOf(copy));
			}
		}
	}

	/** Server start: give every remembered quarry its machine ticket back, so it can tick and refresh it. */
	public static void restoreAll(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			List<Long> list = level.getAttached(LOADERS);
			if (list == null) {
				continue;
			}

			for (long key : list) {
				keepMachineLoaded(level, BlockPos.of(key));
			}

			if (!list.isEmpty()) {
				SolarQuarry.LOGGER.info("Restored chunk loading for {} quarr{} in {}", list.size(), list.size() == 1 ? "y" : "ies", level.dimension().identifier());
			}
		}
	}

	/** Drops remembered positions whose quarry is gone or no longer loading chunks. */
	public static void prune(MinecraftServer server) {
		if (server.getTickCount() % PRUNE_INTERVAL != 0) {
			return;
		}

		for (ServerLevel level : server.getAllLevels()) {
			List<Long> list = level.getAttached(LOADERS);
			if (list == null || list.isEmpty()) {
				continue;
			}

			List<Long> keep = new ArrayList<>(list.size());
			for (long key : list) {
				BlockPos pos = BlockPos.of(key);
				if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof QuarryBlockEntity quarry && quarry.isChunkLoadingActive()) {
					keep.add(key);
				}
			}

			if (keep.size() != list.size()) {
				if (keep.isEmpty()) {
					level.removeAttached(LOADERS);
				} else {
					level.setAttached(LOADERS, List.copyOf(keep));
				}
			}
		}
	}
}
