package com.solarquarry;

import com.solarquarry.construction.ConstructionBlocks;
import com.solarquarry.kelpfarm.KelpFarmBlock;
import com.solarquarry.kelpfarm.KelpFarmBlockEntity;
import com.solarquarry.registry.ModBlocks;
import com.solarquarry.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/** Self-tests for the 4.0 Kelp Farm: builds its basin, fills it, plants and harvests real kelp. */
final class SelfTestFarms {
	static final BlockPos KELP = new BlockPos(260, 200, 260);
	static final BlockPos KELP_BLOCKED = new BlockPos(280, 200, 260);
	private static long kelpBefore;
	private static int harvestedBefore;

	private SelfTestFarms() {
	}

	private static KelpFarmBlockEntity farm(ServerLevel level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof KelpFarmBlockEntity f ? f : null;
	}

	static int countOutput(KelpFarmBlockEntity farm, net.minecraft.world.item.Item item) {
		int n = 0;
		for (int i = KelpFarmBlockEntity.OUTPUT_START; i < KelpFarmBlockEntity.OUTPUT_START + KelpFarmBlockEntity.OUTPUT_SLOTS; i++) {
			if (farm.getItem(i).is(item)) {
				n += farm.getItem(i).getCount();
			}
		}

		return n;
	}

	static void setupKelp(ServerLevel level) {
		SelfTestLogistics.force(level, 250, 250, 295, 275);
		SelfTestLogistics.clear(level, 250, 199, 252, 292, 210, 272);
		level.setBlock(KELP, ModBlocks.KELP_FARM.defaultBlockState().setValue(KelpFarmBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
		KelpFarmBlockEntity farm = farm(level, KELP);
		farm.setItem(0, new ItemStack(Items.KELP, 32));
		farm.setItem(KelpFarmBlockEntity.BONE_MEAL_SLOT, new ItemStack(Items.BONE_MEAL, 64));
		farm.setItem(KelpFarmBlockEntity.UPGRADE_START, new ItemStack(ModItems.SPEED_UPGRADE, 4));
		farm.setItem(KelpFarmBlockEntity.UPGRADE_START + 1, new ItemStack(ModItems.GROWTH_UPGRADE, 2));
		farm.restoreEnergy(farm.getEnergyCapacity());

		// A second farm with a player's block where its wall goes.
		level.setBlock(KELP_BLOCKED.offset(3, 0, 3), Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(KELP_BLOCKED, ModBlocks.KELP_FARM.defaultBlockState().setValue(KelpFarmBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
		farm(level, KELP_BLOCKED).restoreEnergy(20_000);
	}

	static void verifyKelpBuilt(ServerLevel level) {
		KelpFarmBlockEntity farm = farm(level, KELP);
		int walls = 0;
		for (var p : farm.getConstruction().positions()) {
			if (ConstructionBlocks.isMachineBuilt(level.getBlockState(p))) {
				walls++;
			}
		}

		SelfTest.check(farm.getConstruction().isComplete() && walls == farm.getConstruction().positions().size() && walls == 24 * KelpFarmBlockEntity.HEIGHT,
			"kelp farm: builds its glass basin (" + walls + " wall blocks, " + farm.getConstruction().state() + ")");
		KelpFarmBlockEntity blocked = farm(level, KELP_BLOCKED);
		SelfTest.check(blocked.getStatus() == KelpFarmBlockEntity.Status.BUILD_BLOCKED && level.getBlockState(KELP_BLOCKED.offset(3, 0, 3)).is(Blocks.COBBLESTONE),
			"kelp farm: a player's block stops construction and is left in place (" + blocked.getStatus() + ")");
	}

	static void verifyKelpFarming(ServerLevel level) {
		KelpFarmBlockEntity farm = farm(level, KELP);
		int water = 0;
		int kelpColumns = 0;
		for (BlockPos column : farm.columns()) {
			if (KelpFarmBlockEntity.kelpHeight(level, column) > 0) {
				kelpColumns++;
			}

			for (int y = 0; y < KelpFarmBlockEntity.HEIGHT; y++) {
				var s = level.getBlockState(column.above(y));
				if (s.is(Blocks.WATER) || s.is(Blocks.KELP) || s.is(Blocks.KELP_PLANT)) {
					water++;
				}
			}
		}

		int outside = 0;
		BlockPos c = farm.basinCenter();
		for (int d = -5; d <= 5; d++) {
			for (BlockPos p : new BlockPos[] {c.offset(d, 0, -5), c.offset(d, 0, 5), c.offset(-5, 0, d), c.offset(5, 0, d)}) {
				if (!level.getFluidState(p).isEmpty()) {
					outside++;
				}
			}
		}

		int kelp = countOutput(farm, Items.KELP);
		SolarQuarry.LOGGER.info("[SelfTest] kelp farm: {} columns, {} water cells, harvested {}, output {} kelp, status {}, energy {}",
			kelpColumns, water, farm.getHarvested(), kelp, farm.getStatus(), farm.getEnergy());
		SelfTest.check(water == 25 * KelpFarmBlockEntity.HEIGHT && outside == 0, "kelp farm: basin full of water, nothing leaks out (" + water + " cells, " + outside + " outside)");
		SelfTest.check(kelpColumns == 25 && farm.getPlanted() == 25, "kelp farm: kelp planted in every column (" + kelpColumns + ")");
		SelfTest.check(farm.getHarvested() >= 10 && kelp == farm.getHarvested(), "kelp farm: grown kelp harvested into the output (" + farm.getHarvested() + " harvested, " + kelp + " in the output)");
		SelfTest.check(farm.getEnergy() < farm.getEnergyCapacity(), "kelp farm: uses energy for its work");
		int dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(c).inflate(8), e -> e.getItem().is(Items.KELP)).size();
		SelfTest.check(dropped == 0, "kelp farm: no kelp dropped in the world (" + dropped + ")");

		// Full output: harvesting stops, nothing is lost, and the kelp keeps standing.
		for (int i = KelpFarmBlockEntity.OUTPUT_START; i < KelpFarmBlockEntity.OUTPUT_START + KelpFarmBlockEntity.OUTPUT_SLOTS; i++) {
			farm.setItem(i, new ItemStack(Items.DIRT, 64));
		}

		harvestedBefore = (int) farm.getHarvested();
		farm.setItem(KelpFarmBlockEntity.BONE_MEAL_SLOT, new ItemStack(Items.BONE_MEAL, 64));
		farm.restoreEnergy(farm.getEnergyCapacity());
		CompoundTag tag = farm.saveWithFullMetadata(level.registryAccess());
		BlockEntity copy = BlockEntity.loadStatic(KELP, level.getBlockState(KELP), tag, level.registryAccess());
		SelfTest.check(copy instanceof KelpFarmBlockEntity k && k.getHarvested() == farm.getHarvested() && k.getConstruction().isComplete()
			&& k.getPlanted() == farm.getPlanted() && k.getItem(KelpFarmBlockEntity.UPGRADE_START).is(ModItems.SPEED_UPGRADE),
			"kelp farm: survives save/load (construction, totals, upgrades)");
	}

	static void verifyKelpFull(ServerLevel level) {
		KelpFarmBlockEntity farm = farm(level, KELP);
		int tall = 0;
		for (BlockPos column : farm.columns()) {
			tall = Math.max(tall, KelpFarmBlockEntity.kelpHeight(level, column));
		}

		SelfTest.check(farm.getStatus() == KelpFarmBlockEntity.Status.OUTPUT_FULL && farm.getHarvested() == harvestedBefore && tall >= KelpFarmBlockEntity.HEIGHT,
			"kelp farm: a full output stops harvesting without losing kelp (" + farm.getStatus() + ", tallest " + tall + ")");
		// A chest next to the machine takes the output.
		level.setBlock(KELP.west(), Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		for (int i = KelpFarmBlockEntity.OUTPUT_START; i < KelpFarmBlockEntity.OUTPUT_START + KelpFarmBlockEntity.OUTPUT_SLOTS; i++) {
			farm.setItem(i, ItemStack.EMPTY);
		}
	}

	static void verifyKelpPushed(ServerLevel level) {
		KelpFarmBlockEntity farm = farm(level, KELP);
		int inChest = SelfTestLogistics.count(level, KELP.west(), Items.KELP);
		SelfTest.check(inChest > 0 && farm.getHarvested() > harvestedBefore, "kelp farm: resumes and pushes kelp into a chest next to it (" + inChest + ")");
		BlockPos c = farm.basinCenter();
		level.setBlock(KELP, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		int left = 0;
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-3, 0, -3), c.offset(3, KelpFarmBlockEntity.HEIGHT, 3))) {
			var s = level.getBlockState(p);
			if (ConstructionBlocks.isMachineBuilt(s) || s.is(Blocks.WATER) || s.is(Blocks.KELP) || s.is(Blocks.KELP_PLANT)) {
				left++;
			}
		}

		SelfTest.check(left == 0, "kelp farm: breaking it takes the basin, water and kelp down (" + left + " left)");
	}
}
