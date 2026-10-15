package com.solarquarry;

import com.solarquarry.block.QuarryBlock;
import com.solarquarry.blockentity.BatteryBlockEntity;
import com.solarquarry.blockentity.QuarryBlockEntity;
import com.solarquarry.construction.ConstructionBlocks;
import com.solarquarry.construction.ConstructionProblem;
import com.solarquarry.construction.ConstructionState;
import com.solarquarry.quarry.QuarryFrames;
import com.solarquarry.quarry.QuarryMode;
import com.solarquarry.quarry.QuarryStatus;
import com.solarquarry.registry.ModBlocks;
import com.solarquarry.treefarm.TreeFarmBlock;
import com.solarquarry.treefarm.TreeFarmBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * Self-tests for the shared construction framework (4.0): the quarry raises its frame before mining, and the Tree
 * Farm builds its mast and posts on natural, grassy ground powered through a real cable, stops at obstructions
 * without overwriting them, survives save/load, and takes its structure down when it is broken.
 */
final class SelfTestConstruction {
	static final BlockPos CQ = new BlockPos(170, 200, 140);
	static final BlockPos CQ2 = new BlockPos(180, 200, 140);
	static final BlockPos TF = new BlockPos(170, 200, 170);
	static final BlockPos TF_BATTERY = TF.north(2);
	private static int frameTotal;

	private SelfTestConstruction() {
	}

	private static void force(ServerLevel level, int x0, int z0, int x1, int z1) {
		for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
			for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
				level.setChunkForced(cx, cz, true);
				level.getChunk(cx, cz);
			}
		}
	}

	private static QuarryBlockEntity quarryAt(ServerLevel level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof QuarryBlockEntity q ? q : null;
	}

	private static TreeFarmBlockEntity farm(ServerLevel level) {
		return level.getBlockEntity(TF) instanceof TreeFarmBlockEntity f ? f : null;
	}

	// ---- quarry frame ----

	static void setupQuarryFrame(ServerLevel level) {
		force(level, 160, 130, 195, 150);
		for (BlockPos q : new BlockPos[] {CQ, CQ2}) {
			for (int x = q.getX() - 4; x <= q.getX() + 4; x++) {
				for (int z = q.getZ() - 6; z <= q.getZ() + 6; z++) {
					level.setBlock(new BlockPos(x, 198, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
					level.setBlock(new BlockPos(x, 199, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
					for (int y = 200; y <= 206; y++) {
						level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}

			level.setBlock(q, ModBlocks.QUARRY.defaultBlockState().setValue(QuarryBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
			QuarryBlockEntity quarry = quarryAt(level, q);
			quarry.setQuarryMode(QuarryMode.STRIP_MINE);
			quarry.setWidth(3);
			quarry.setLength(3);
			quarry.setHeight(1);
			quarry.setTargetY(199);
		}

		quarryAt(level, CQ).restoreEnergy(50_000L);
		quarryAt(level, CQ).start();
		quarryAt(level, CQ2).start(); // no energy: must wait without building anything
	}

	/** A few ticks in: building, partly solid, nothing mined, progress survives save/load. */
	static void verifyQuarryFrameEarly(ServerLevel level) {
		QuarryBlockEntity quarry = quarryAt(level, CQ);
		var site = quarry.getFrameConstruction();
		frameTotal = site.totalSteps();
		SelfTest.check(quarry.getStatus() == QuarryStatus.CONSTRUCTING && site.isUnderConstruction() && site.stepsDone() > 0 && site.stepsDone() < frameTotal,
			"construction: the quarry raises its frame first (" + site.stepsDone() + "/" + frameTotal + " steps, " + quarry.getStatus() + ")");
		SelfTest.check(quarry.getBlocksMined() == 0, "construction: nothing is mined while the frame is going up");
		int solid = QuarryFrames.boxesIntersecting(level, new AABB(CQ).inflate(8)).size();
		SelfTest.check(solid < frameTotal / QuarryBlockEntity.FRAME_SEGMENTS, "construction: only finished frame parts are solid (" + solid + " of "
			+ frameTotal / QuarryBlockEntity.FRAME_SEGMENTS + ")");
		CompoundTag tag = quarry.saveWithFullMetadata(level.registryAccess());
		BlockEntity copy = BlockEntity.loadStatic(CQ, level.getBlockState(CQ), tag, level.registryAccess());
		SelfTest.check(copy instanceof QuarryBlockEntity c && c.getFrameConstruction().stepsDone() == site.stepsDone()
			&& c.getFrameConstruction().state() == site.state(), "construction: frame progress survives save/load");
		QuarryBlockEntity starved = quarryAt(level, CQ2);
		SelfTest.check(starved.getStatus() == QuarryStatus.WAITING_FOR_POWER && starved.getFrameConstruction().stepsDone() == 0
			&& starved.getFrameConstruction().problem() == ConstructionProblem.NO_POWER, "construction: without energy it waits and builds nothing");
	}

	/** Later: frame complete exactly once, mining under way; the starved quarry builds once powered. */
	static void verifyQuarryFrameDone(ServerLevel level) {
		QuarryBlockEntity quarry = quarryAt(level, CQ);
		var site = quarry.getFrameConstruction();
		SelfTest.check(site.isComplete() && site.placedCount() == site.totalSteps(), "construction: frame finished, each step done exactly once ("
			+ site.placedCount() + "/" + site.totalSteps() + ")");
		SelfTest.check(quarry.getBlocksMined() > 0, "construction: mining starts once the frame stands (" + quarry.getBlocksMined() + " mined)");
		SelfTest.check(QuarryFrames.boxesIntersecting(level, new AABB(CQ).inflate(8)).size() == site.totalSteps() / QuarryBlockEntity.FRAME_SEGMENTS,
			"construction: the whole frame is solid when complete");
		quarryAt(level, CQ2).restoreEnergy(50_000L);
	}

	static void verifyQuarryFrameResumed(ServerLevel level) {
		QuarryBlockEntity starved = quarryAt(level, CQ2);
		SelfTest.check(starved.getFrameConstruction().isComplete(), "construction: a quarry that waited for power finishes its frame once powered");
		// A quarry saved before 4.0 while running has its frame up already: it must not rebuild it.
		CompoundTag tag = starved.saveWithFullMetadata(level.registryAccess());
		tag.remove("Construction");
		level.removeBlock(CQ2, false);
		level.setBlock(CQ2, ModBlocks.QUARRY.defaultBlockState().setValue(QuarryBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
		quarryAt(level, CQ2).loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
			net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), tag));
	}

	static void verifyQuarryMigration(ServerLevel level) {
		QuarryBlockEntity migrated = quarryAt(level, CQ2);
		SelfTest.check(migrated.getFrameConstruction().isComplete() && migrated.getStatus() != QuarryStatus.CONSTRUCTING,
			"construction: a running quarry from 3.x keeps its frame (no rebuild after updating)");
		for (BlockPos q : new BlockPos[] {CQ, CQ2}) {
			QuarryBlockEntity quarry = quarryAt(level, q);
			if (quarry != null) {
				quarry.clearContent();
			}

			level.removeBlock(q, false);
		}

		SelfTest.check(QuarryFrames.boxesIntersecting(level, new AABB(CQ).inflate(8)).isEmpty(), "construction: the frame disappears with its quarry");
	}

	// ---- tree farm on natural ground ----

	static void setupTreeFarmNatural(ServerLevel level) {
		force(level, 160, 160, 182, 184);
		Block[] plants = {Blocks.SHORT_GRASS, Blocks.POPPY, Blocks.DANDELION, Blocks.SHORT_GRASS, Blocks.FERN};
		int i = 0;
		for (int x = 160; x <= 180; x++) {
			for (int z = 162; z <= 182; z++) {
				level.setBlock(new BlockPos(x, 198, z), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
				level.setBlock(new BlockPos(x, 199, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
				for (int y = 200; y <= 225; y++) {
					level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}

				level.setBlock(new BlockPos(x, 200, z), plants[i++ % plants.length].defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}

		// Power comes from a battery through a cable, like in a real base.
		level.setBlock(TF_BATTERY, ModBlocks.BATTERY_BASIC.defaultBlockState(), Block.UPDATE_ALL);
		((BatteryBlockEntity) level.getBlockEntity(TF_BATTERY)).restoreEnergy(400_000L);
		level.setBlock(TF.north(), ModBlocks.ENERGY_CABLE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(TF, ModBlocks.TREE_FARM.defaultBlockState().setValue(TreeFarmBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
		// A player block where the mast's second segment goes: construction must stop there, not replace it.
		level.setBlock(TF.above(2), Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
		TreeFarmBlockEntity farm = farm(level);
		farm.setItem(0, new ItemStack(Items.OAK_SAPLING, 16));
		farm.setItem(TreeFarmBlockEntity.BONE_MEAL_START, new ItemStack(Items.BONE_MEAL, 64));
		farm.setItem(TreeFarmBlockEntity.UPGRADE_START, new ItemStack(com.solarquarry.registry.ModItems.SPEED_UPGRADE, 4));
	}

	static void verifyTreeFarmBlocked(ServerLevel level) {
		TreeFarmBlockEntity farm = farm(level);
		var site = farm.getConstruction();
		SelfTest.check(farm.getEnergy() > 0 || site.stepsDone() > 0, "tree farm: powered through the cable (" + farm.getEnergy() + " E)");
		SelfTest.check(level.getBlockState(TF.above()).is(ConstructionBlocks.INDUSTRIAL_FRAME), "tree farm: the drone builds its mast");
		SelfTest.check(site.state() == ConstructionState.PAUSED && site.problem() == ConstructionProblem.OBSTRUCTED && TF.above(2).equals(site.problemPos()),
			"construction: stops at a player block and reports where (" + site.state() + " " + site.problem() + " " + site.problemPos() + ")");
		SelfTest.check(level.getBlockState(TF.above(2)).is(Blocks.COBBLESTONE), "construction: the player block is not replaced");
		SelfTest.check(farm.getStatus() == TreeFarmBlockEntity.Status.BUILD_BLOCKED && farm.getSaplingsPlanted() == 0,
			"tree farm: says construction is blocked and does not start planting");
		CompoundTag tag = farm.saveWithFullMetadata(level.registryAccess());
		BlockEntity copy = BlockEntity.loadStatic(TF, level.getBlockState(TF), tag, level.registryAccess());
		SelfTest.check(copy instanceof TreeFarmBlockEntity c && c.getConstruction().stepsDone() == site.stepsDone()
			&& c.getConstruction().plan().equals(site.plan()) && c.getConstruction().state() == site.state(), "construction: plan and progress survive save/load");
		level.setBlock(TF.above(2), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
	}

	static void verifyTreeFarmNatural(ServerLevel level) {
		TreeFarmBlockEntity farm = farm(level);
		var site = farm.getConstruction();
		int frames = 0;
		for (BlockPos p : BlockPos.betweenClosed(TF.offset(-10, -2, -3), TF.offset(10, 6, 14))) {
			if (level.getBlockState(p).is(ConstructionBlocks.INDUSTRIAL_FRAME)) {
				frames++;
			}
		}

		SelfTest.check(site.isComplete() && frames == site.totalSteps() && frames == 11,
			"tree farm: mast and four corner posts built on grassy ground (" + frames + " frame blocks, " + site.state() + ")");
		SelfTest.check(site.placedCount() == site.totalSteps(), "construction: no block placed twice after resuming (" + site.placedCount() + ")");
		int saplings = 0;
		for (BlockPos p : BlockPos.betweenClosed(TF.offset(-5, 0, 1), TF.offset(5, 0, 10))) {
			var state = level.getBlockState(p);
			if (state.getBlock() instanceof net.minecraft.world.level.block.SaplingBlock || state.is(net.minecraft.tags.BlockTags.LOGS)) {
				saplings++;
			}
		}

		SelfTest.check(farm.getSaplingsPlanted() >= 5 && saplings >= 5, "tree farm: plants through grass and flowers (" + farm.getSaplingsPlanted() + " planted, "
			+ saplings + " standing)");
		SelfTest.check(farm.getStatus() != TreeFarmBlockEntity.Status.IDLE && farm.getStatus() != TreeFarmBlockEntity.Status.NO_GROUND
			&& farm.getStatus() != TreeFarmBlockEntity.Status.BUILD_BLOCKED, "tree farm: working, not idle (" + farm.getStatus() + ")");
		if (farm.getTreesFelled() == 0) { // DIAG
			for (BlockPos spot : farm.plantingSpots()) {
				StringBuilder sb = new StringBuilder();
				for (int dy = -3; dy <= 6; dy++) {
					sb.append(dy).append('=').append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(spot.above(dy)).getBlock()).getPath()).append(' ');
				}
				SolarQuarry.LOGGER.info("[SelfTest] DIAG spot {} : {}", spot, sb);
			}
			SolarQuarry.LOGGER.info("[SelfTest] DIAG energy {} bonemeal {} saplings {}", farm.getEnergy(), farm.getItem(TreeFarmBlockEntity.BONE_MEAL_START), farm.getItem(0));
		}
		SelfTest.check(farm.getTreesFelled() >= 1, "tree farm: grows and fells a tree on natural ground (" + farm.getTreesFelled() + " felled)");
		farm.clearContent();
		level.removeBlock(TF, false);
	}

	static void verifyTreeFarmRemoved(ServerLevel level) {
		int frames = 0;
		for (BlockPos p : BlockPos.betweenClosed(TF.offset(-10, -2, -3), TF.offset(10, 6, 14))) {
			if (level.getBlockState(p).is(ConstructionBlocks.INDUSTRIAL_FRAME)) {
				frames++;
			}
		}

		SelfTest.check(frames == 0, "construction: breaking the farm takes its mast and posts down (" + frames + " left)");
		SelfTest.check(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(TF).inflate(12),
			e -> e.getItem().is(ConstructionBlocks.INDUSTRIAL_FRAME.asItem())).isEmpty(), "construction: frame blocks never drop as items");
		for (int cx = 160 >> 4; cx <= 182 >> 4; cx++) {
			for (int cz = 130 >> 4; cz <= 184 >> 4; cz++) {
				level.setChunkForced(cx, cz, false);
			}
		}
	}
}
