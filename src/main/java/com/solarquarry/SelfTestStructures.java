package com.solarquarry;

import com.solarquarry.turret.LaserTurretBlockEntity;
import com.solarquarry.worldgen.IndustrialPiece;
import com.solarquarry.worldgen.Layout;
import com.solarquarry.worldgen.ModStructures;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;

/** Self-tests for Checkpoint K: every structure builds, has themed loot that rolls once, finite turrets, and generates. */
final class SelfTestStructures {
	private static final int BASE_X = 2000;
	private static final int BASE_Z = 2000;
	private static final List<IndustrialPiece> PIECES = new ArrayList<>();

	private SelfTestStructures() {
	}

	private static BlockPos originFor(ServerLevel level, Layout layout) {
		int x = BASE_X + layout.ordinal() * 48;
		int z = BASE_Z;
		int y = layout.underground() ? 40 : level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
		return new BlockPos(x, y, z);
	}

	private static void forChunks(BoundingBox box, java.util.function.Consumer<ChunkPos> action) {
		for (int cx = (box.minX() - 16) >> 4; cx <= (box.maxX() + 16) >> 4; cx++) {
			for (int cz = (box.minZ() - 16) >> 4; cz <= (box.maxZ() + 16) >> 4; cz++) {
				action.accept(new ChunkPos(cx, cz));
			}
		}
	}

	/** Builds all ten structures, chunk by chunk, like world generation does. */
	static void setup(ServerLevel level) {
		PIECES.clear();
		for (Layout layout : Layout.values()) {
			BlockPos origin = originFor(level, layout);
			IndustrialPiece piece = new IndustrialPiece(layout, origin);
			PIECES.add(piece);
			forChunks(piece.getBoundingBox(), chunk -> level.setChunkForced(chunk.x(), chunk.z(), true));
			forChunks(piece.getBoundingBox(), chunk -> {
				BoundingBox chunkBox = new BoundingBox(chunk.getMinBlockX(), level.getMinY(), chunk.getMinBlockZ(), chunk.getMaxBlockX(), level.getMaxY(), chunk.getMaxBlockZ());
				piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(chunk.x() * 341873128712L + chunk.z()), chunkBox, chunk, origin);
			});
		}
	}

	static void verify(ServerLevel level) {
		int tables = 0;
		for (String name : new String[] {"mining_facility", "power_station", "research_laboratory", "automated_factory", "tree_farm", "supply_bunker", "substation",
			"prototype_facility", "prototype_lab", "train_depot", "solar_array"}) {
			LootTable table = level.getServer().reloadableRegistries().getLootTable(ModStructures.loot(name));
			if (table != LootTable.EMPTY) {
				tables++;
			}
		}

		SelfTest.check(tables == 11, "structures: all 11 loot tables load (" + tables + ")");
		var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		var sets = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
		int registered = 0;
		for (Layout layout : Layout.values()) {
			if (structures.get(ModStructures.structure(layout)).isPresent()
				&& sets.get(net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE_SET, SolarQuarry.id(layout.id()))).isPresent()) {
				registered++;
			}
		}

		SelfTest.check(registered == 10, "structures: all 10 structures and their placement sets are registered from the data pack (" + registered + ")");
		for (IndustrialPiece piece : PIECES) {
			verifyPiece(level, piece);
		}
	}

	private static void verifyPiece(ServerLevel level, IndustrialPiece piece) {
		BoundingBox box = piece.getBoundingBox();
		int solid = 0;
		int containers = 0;
		int filled = 0;
		int rolledOnce = 0;
		int turrets = 0;
		int finiteTurrets = 0;
		int unknownItems = 0;
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY() - 8, box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (!level.getBlockState(pos).isAir()) {
				solid++;
			}

			BlockEntity be = level.getBlockEntity(pos);
			if (be instanceof RandomizableContainer loot && be instanceof Container container && loot.getLootTable() != null) {
				containers++;
				loot.unpackLootTable(null);
				if (!container.isEmpty()) {
					filled++;
				}

				for (int i = 0; i < container.getContainerSize(); i++) {
					ItemStack stack = container.getItem(i);
					if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals("air")) {
						unknownItems++;
					}
				}

				if (loot.getLootTable() == null) {
					rolledOnce++;
				}
			}

			if (be instanceof LaserTurretBlockEntity turret) {
				turrets++;
				if (turret.isDamaged() || turret.getEnergy() <= turret.getEnergyCapacity()) {
					finiteTurrets++;
				}
			}
		}

		for (MinecartChest cart : level.getEntitiesOfClass(MinecartChest.class, AABB.of(box).inflate(2))) {
			if (cart.getContainerLootTable() != null) {
				containers++;
				cart.unpackChestVehicleLootTable(null);
				filled += cart.isEmpty() ? 0 : 1;
				rolledOnce += cart.getContainerLootTable() == null ? 1 : 0;
			}
		}

		Layout layout = piece.layout();
		SolarQuarry.LOGGER.info("[SelfTest] structure {}: {} blocks, {} loot containers ({} filled), {} turrets", layout.id(), solid, containers, filled, turrets);
		SelfTest.check(solid > 150 && containers >= 1 && filled == containers && rolledOnce == containers && unknownItems == 0,
			"structure " + layout.id() + ": built (" + solid + " blocks) with " + containers + " loot containers, each filled once");
		if (layout == Layout.MINING_FACILITY || layout == Layout.POWER_STATION || layout == Layout.RESEARCH_LABORATORY || layout == Layout.PROTOTYPE_FACILITY) {
			SelfTest.check(turrets >= 1 && finiteTurrets == turrets, "structure " + layout.id() + ": has Laser Turrets, wrecked or with a finite charge (" + turrets + ")");
		}
	}

	/** End to end: the registered structure finds a site and generates through the game's own structure code. */
	static void verifyGeneration(ServerLevel level) {
		var holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ModStructures.structure(Layout.SUBSTATION));
		var generator = level.getChunkSource().getGenerator();
		ChunkPos chunk = new ChunkPos((BASE_X + 600) >> 4, BASE_Z >> 4);
		StructureStart start = holder.value().generate(holder, level.dimension(), level.registryAccess(), generator, generator.getBiomeSource(),
			level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(), chunk, 0, level, b -> true);
		SelfTest.check(start.isValid() && !start.getPieces().isEmpty() && start.getPieces().getFirst() instanceof IndustrialPiece,
			"structures: the substation finds a site on flat ground and generates through the game's structure code");
		if (start.isValid()) {
			BoundingBox box = start.getBoundingBox();
			forChunks(box, c -> level.setChunkForced(c.x(), c.z(), true));
			forChunks(box, c -> start.placeInChunk(level, level.structureManager(), generator, RandomSource.create(c.x() * 341873128712L + c.z()),
				new BoundingBox(c.getMinBlockX(), level.getMinY(), c.getMinBlockZ(), c.getMaxBlockX(), level.getMaxY(), c.getMaxBlockZ()), c));
			int chests = 0;
			for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				if (level.getBlockState(pos).is(Blocks.CHEST)) {
					chests++;
				}
			}

			SelfTest.check(chests >= 1, "structures: the generated substation has its control-shed chest (" + chests + ")");
			forChunks(box, c -> level.setChunkForced(c.x(), c.z(), false));
		}

		for (IndustrialPiece piece : PIECES) {
			forChunks(piece.getBoundingBox(), c -> level.setChunkForced(c.x(), c.z(), false));
		}
	}
}
