package com.solarquarry;

import com.solarquarry.block.QuarryBlock;
import com.solarquarry.blockentity.BatteryBlockEntity;
import com.solarquarry.blockentity.ItemPipeBlockEntity;
import com.solarquarry.blockentity.QuarryBlockEntity;
import com.solarquarry.blockentity.SolarPanelBlockEntity;
import com.solarquarry.energy.SolarTier;
import com.solarquarry.item.WrenchItem;
import com.solarquarry.registry.ModBlocks;
import com.solarquarry.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Development self-test, enabled only with -Dsolarquarry.selftest=true (set by the runServer task).
 * Builds a small test site on a dedicated server, runs the quarry, pipes, filters, batteries,
 * persistence, wrench pickup, void upgrade and solar checks, logs the results and stops the server.
 */
public final class SelfTest {
	private static final BlockPos QUARRY = new BlockPos(8, 200, 8);
	private static final BlockPos BATTERY = new BlockPos(5, 200, 8);
	private static final BlockPos PIPE_1 = new BlockPos(9, 200, 8);
	private static final BlockPos PIPE_2 = new BlockPos(10, 200, 8);
	private static final BlockPos FILTER = new BlockPos(11, 200, 8);
	private static final BlockPos CHEST_ORE = new BlockPos(12, 200, 8);
	private static final BlockPos PIPE_BRANCH = new BlockPos(10, 200, 7);
	private static final BlockPos CHEST_DEFAULT = new BlockPos(10, 200, 6);
	private static final BlockPos ORE = new BlockPos(3, 199, 12);
	private static final BlockPos BEDROCK = new BlockPos(4, 199, 12);
	private static final BlockPos SOLAR = new BlockPos(20, 200, 4);
	private static final int AREA_MIN_X = 1;
	private static final int AREA_MAX_X = 16;
	private static int noonGeneration;

	private static int stage;
	private static SelfTestModes modes;
	private static int wait;
	private static long batteryStart;
	private static final List<String> failures = new ArrayList<>();

	private SelfTest() {
	}

	public static void register() {
		SolarQuarry.LOGGER.info("[SelfTest] enabled");
		ServerTickEvents.END_SERVER_TICK.register(SelfTest::tick);
		SelfTestModes.registerProtectionProbe();
	}

	static void check(boolean condition, String message) {
		if (condition) {
			SolarQuarry.LOGGER.info("[SelfTest] OK   {}", message);
		} else {
			SolarQuarry.LOGGER.error("[SelfTest] FAIL {}", message);
			failures.add(message);
		}
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		try {
			switch (stage) {
				case 0 -> {
					for (int cx = -1; cx <= 2; cx++) {
						for (int cz = -1; cz <= 2; cz++) {
							level.setChunkForced(cx, cz, true);
						}
					}

					SelfTestModes.forceChunks(level);

					stage = 1;
				}
				case 1 -> {
					if (wait++ == 0) {
						verifyData(server);
					}

					if (level.isLoaded(new BlockPos(0, 200, 0)) && level.isLoaded(new BlockPos(24, 200, 26)) && level.isLoaded(SOLAR)) {
						build(level);
						stage = 2;
						wait = 0;
					}
				}
				case 2 -> {
					QuarryBlockEntity quarry = quarry(level);
					wait++;
					if (quarry.isFinished() || wait > 6000) {
						check(quarry.isFinished(), "quarry finished mining the test area (" + wait + " ticks)");
						stage = 3;
						wait = 0;
					}
				}
				case 3 -> {
					if (++wait >= 300) {
						verifyMining(level);
						verifyPersistence(level);
						verifyWrench(level);
						startVoidTest(level);
						stage = 4;
						wait = 0;
					}
				}
				case 4 -> {
					QuarryBlockEntity quarry = quarry(level);
					wait++;
					if ((quarry.isFinished() && wait > 40) || wait > 4000) {
						stage = 5;
						wait = 0;
					}
				}
				case 5 -> {
					if (++wait >= 200) {
						verifyVoid(level);
						startSilkTest(level);
						stage = 50;
						wait = 0;
					}
				}
				case 50 -> {
					QuarryBlockEntity quarry = quarry(level);
					wait++;
					if ((quarry.isFinished() && wait > 40) || wait > 4000) {
						stage = 51;
						wait = 0;
					}
				}
				case 51 -> {
					if (++wait >= 200) {
						verifySilk(level);
						quarry(level).setItem(QuarryBlockEntity.BUFFER_SLOTS + 3, new ItemStack(ModItems.RANGE_UPGRADE_2));
						stage = 52;
						wait = 0;
					}
				}
				case 52 -> {
					if (++wait >= 3) {
						QuarryBlockEntity quarry = quarry(level);
						check(quarry.getWidth() == 64 && quarry.getLength() == 64, "range upgrade II expands the area to 64x64 (" + quarry.getWidth() + "x" + quarry.getLength() + ")");
						quarry.removeItemNoUpdate(QuarryBlockEntity.BUFFER_SLOTS + 3);
						quarry.setItem(QuarryBlockEntity.BUFFER_SLOTS + 3, new ItemStack(ModItems.RANGE_UPGRADE));
						stage = 53;
						wait = 0;
					}
				}
				case 53 -> {
					if (++wait >= 3) {
						QuarryBlockEntity quarry = quarry(level);
						check(quarry.getWidth() == 32 && quarry.getLength() == 32, "range upgrade I expands the area to 32x32 (" + quarry.getWidth() + "x" + quarry.getLength() + ")");
						quarry.removeItemNoUpdate(QuarryBlockEntity.BUFFER_SLOTS + 3);
						quarry.pause();
						startSolar(server, level);
						stage = 6;
						wait = 0;
					}
				}
				case 6 -> {
					// Give the light engine time to update after the panel is covered.
					if (++wait >= 60) {
						verifySolarCovered(level);
						modes = new SelfTestModes();
						stage = 8;
						wait = 0;
					}
				}
				case 8 -> {
					if (SelfTestModes.chunksReady(level) && modes.tick(server, level)) {
						finish(server);
						stage = 7;
					} else if (++wait > 40_000) {
						check(false, "mode tests timed out");
						finish(server);
						stage = 7;
					}
				}
				default -> {
				}
			}
		} catch (Exception e) {
			SolarQuarry.LOGGER.error("[SelfTest] crashed", e);
			failures.add("exception: " + e);
			finish(server);
			stage = 7;
		}
	}

	private static void build(ServerLevel level) {
		for (int x = -2; x <= 24; x++) {
			for (int z = 2; z <= 28; z++) {
				for (int y = 195; y <= 204; y++) {
					level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}

		// Two layers of stone in the 16x16 area behind a north-facing quarry (x 1..16, z 9..24).
		for (int x = AREA_MIN_X; x <= AREA_MAX_X; x++) {
			for (int z = 9; z < 25; z++) {
				level.setBlock(new BlockPos(x, 199, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
				level.setBlock(new BlockPos(x, 198, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}

		level.setBlock(new BlockPos(AREA_MIN_X, 197, 9), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(ORE, Blocks.IRON_ORE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(BEDROCK, Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL);

		level.setBlock(QUARRY, ModBlocks.QUARRY.defaultBlockState().setValue(QuarryBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
		level.setBlock(BATTERY, ModBlocks.BATTERY_ELITE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(BATTERY.east(), ModBlocks.ENERGY_CABLE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(BATTERY.east(2), ModBlocks.ENERGY_CABLE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(CHEST_ORE, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(CHEST_DEFAULT, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(PIPE_1, ModBlocks.ITEM_PIPE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(PIPE_2, ModBlocks.ITEM_PIPE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(FILTER, ModBlocks.FILTER_PIPE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(PIPE_BRANCH, ModBlocks.ITEM_PIPE.defaultBlockState(), Block.UPDATE_ALL);

		ItemPipeBlockEntity filter = (ItemPipeBlockEntity) level.getBlockEntity(FILTER);
		filter.toggleWhitelist();
		filter.getFilter().setItem(0, new ItemStack(Items.RAW_IRON));

		BatteryBlockEntity battery = (BatteryBlockEntity) level.getBlockEntity(BATTERY);
		battery.restoreEnergy(2_000_000L);
		batteryStart = battery.getEnergy();

		QuarryBlockEntity quarry = quarry(level);
		quarry.setItem(QuarryBlockEntity.BUFFER_SLOTS, new ItemStack(ModItems.SPEED_UPGRADE, 4));
		quarry.setItem(QuarryBlockEntity.BUFFER_SLOTS + 1, new ItemStack(ModItems.ENERGY_CAPACITY_UPGRADE, 2));
		quarry.setTargetY(198);
		quarry.start();
		SolarQuarry.LOGGER.info("[SelfTest] test site built, quarry started");
	}

	private static QuarryBlockEntity quarry(ServerLevel level) {
		BlockEntity be = level.getBlockEntity(QUARRY);
		if (!(be instanceof QuarryBlockEntity quarry)) {
			throw new IllegalStateException("quarry block entity missing at " + QUARRY);
		}

		return quarry;
	}

	private static int count(ServerLevel level, BlockPos pos, Item item) {
		int total = 0;
		if (level.getBlockEntity(pos) instanceof Container container) {
			for (int i = 0; i < container.getContainerSize(); i++) {
				ItemStack stack = container.getItem(i);
				if (item == null ? !stack.isEmpty() : stack.is(item)) {
					total += stack.getCount();
				}
			}
		}

		return total;
	}

	private static void verifyMining(ServerLevel level) {
		int remaining = 0;
		for (int x = AREA_MIN_X; x <= AREA_MAX_X; x++) {
			for (int z = 9; z < 25; z++) {
				for (int y = 198; y <= 199; y++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (!pos.equals(BEDROCK) && !level.getBlockState(pos).isAir()) {
						remaining++;
					}
				}
			}
		}

		check(remaining == 0, "all 511 breakable blocks in the area were mined (remaining=" + remaining + ")");
		check(level.getBlockState(BEDROCK).is(Blocks.BEDROCK), "bedrock was skipped");
		check(level.getBlockState(new BlockPos(AREA_MIN_X, 197, 9)).is(Blocks.STONE), "nothing was mined below the target depth");

		int oreChest = count(level, CHEST_ORE, Items.RAW_IRON);
		int oreChestTotal = count(level, CHEST_ORE, null);
		int cobble = count(level, CHEST_DEFAULT, Items.COBBLESTONE);
		int defaultTotal = count(level, CHEST_DEFAULT, null);
		QuarryBlockEntity quarry = quarry(level);
		int buffered = 0;
		for (int i = 0; i < QuarryBlockEntity.BUFFER_SLOTS; i++) {
			buffered += quarry.getItem(i).getCount();
		}

		int inPipes = 0;
		for (BlockPos pos : List.of(PIPE_1, PIPE_2, FILTER, PIPE_BRANCH)) {
			if (level.getBlockEntity(pos) instanceof ItemPipeBlockEntity pipe) {
				inPipes += pipe.getItems().size();
			}
		}

		SolarQuarry.LOGGER.info("[SelfTest] ore chest={} (total {}), default chest cobble={} (total {}), buffer={}, in pipes={}",
			oreChest, oreChestTotal, cobble, defaultTotal, buffered, inPipes);
		check(oreChest == 1 && oreChestTotal == 1, "filter routed the raw iron (and only it) to the ore chest");
		check(cobble == 510 && defaultTotal == 510, "510 cobblestone arrived in the default chest");
		check(buffered == 0 && inPipes == 0, "buffer and pipes drained, nothing lost or duplicated");

		BatteryBlockEntity battery = (BatteryBlockEntity) level.getBlockEntity(BATTERY);
		long used = batteryStart - battery.getEnergy();
		check(used > 0, "battery powered the quarry through cables (used " + used + " E, quarry holds " + quarry.getEnergy() + " E)");
		check(quarry.getBlocksMined() == 511, "quarry counted 511 mined blocks (" + quarry.getBlocksMined() + ")");
	}

	private static void verifyPersistence(ServerLevel level) {
		QuarryBlockEntity quarry = quarry(level);
		CompoundTag tag = quarry.saveWithFullMetadata(level.registryAccess());
		BlockEntity loaded = BlockEntity.loadStatic(QUARRY, level.getBlockState(QUARRY), tag, level.registryAccess());
		check(loaded instanceof QuarryBlockEntity, "quarry block entity reloads from saved data");
		if (loaded instanceof QuarryBlockEntity copy) {
			check(copy.getEnergy() == quarry.getEnergy(), "energy persisted");
			check(copy.getStep() == quarry.getStep() && copy.getWidth() == quarry.getWidth() && copy.getLength() == quarry.getLength() && copy.getQuarryMode() == quarry.getQuarryMode(), "mining cursor persisted");
			check(copy.getBlocksMined() == quarry.getBlocksMined(), "mined counter persisted");
			check(copy.isFinished() == quarry.isFinished() && copy.getMode() == quarry.getMode(), "run state persisted");
			check(ItemStack.matches(copy.getItem(QuarryBlockEntity.BUFFER_SLOTS), quarry.getItem(QuarryBlockEntity.BUFFER_SLOTS)), "upgrades persisted");
		}
	}

	private static void verifyWrench(ServerLevel level) {
		QuarryBlockEntity quarry = quarry(level);
		quarry.restoreEnergy(1234);
		quarry.setItem(3, new ItemStack(Items.DIAMOND, 7));
		quarry.getVoidFilter().setItem(0, new ItemStack(Items.COBBLESTONE));
		long energy = quarry.getEnergy();
		long mined = quarry.getBlocksMined();
		long stepBefore = quarry.getStep();
		QuarryBlockEntity.Mode mode = quarry.getMode();

		ItemStack carried = WrenchItem.dismantle(level, QUARRY);
		check(level.getBlockState(QUARRY).isAir(), "wrench removed the quarry");
		check(carried.is(ModBlocks.QUARRY.asItem()) && carried.getCount() == 1, "wrench returned exactly one quarry item");
		List<ItemEntity> spilled = level.getEntitiesOfClass(ItemEntity.class, new AABB(QUARRY).inflate(3));
		check(spilled.isEmpty(), "nothing spilled when picking the quarry up (" + spilled.size() + " item entities)");

		level.setBlock(QUARRY, ModBlocks.QUARRY.defaultBlockState().setValue(QuarryBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
		BlockItem.updateCustomBlockEntityTag(level, null, QUARRY, carried);
		QuarryBlockEntity placed = quarry(level);
		check(placed.getEnergy() == energy, "energy restored after replacing (" + placed.getEnergy() + ")");
		check(placed.getBlocksMined() == mined && placed.getStep() == stepBefore, "progress restored after replacing");
		check(placed.getMode() == mode, "running state restored after replacing");
		check(placed.getItem(3).is(Items.DIAMOND) && placed.getItem(3).getCount() == 7, "buffer contents restored after replacing");
		check(placed.getItem(QuarryBlockEntity.BUFFER_SLOTS).is(ModItems.SPEED_UPGRADE), "upgrades restored after replacing");
		check(placed.getVoidFilter().getItem(0).is(Items.COBBLESTONE), "void filter restored after replacing");
		placed.removeItemNoUpdate(3);
	}

	private static void startVoidTest(ServerLevel level) {
		for (int x = AREA_MIN_X; x <= AREA_MAX_X; x++) {
			for (int z = 9; z < 25; z++) {
				level.setBlock(new BlockPos(x, 199, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}

		level.setBlock(BEDROCK, Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL);
		QuarryBlockEntity quarry = quarry(level);
		quarry.setItem(QuarryBlockEntity.BUFFER_SLOTS + 2, new ItemStack(ModItems.VOID_UPGRADE));
		quarry.getVoidFilter().setItem(0, new ItemStack(Items.COBBLESTONE));
		quarry.setTargetY(199);
		quarry.resetProgress();
		quarry.start();
	}

	private static void verifyVoid(ServerLevel level) {
		QuarryBlockEntity quarry = quarry(level);
		check(quarry.isFinished(), "void test run finished");
		int cobble = count(level, CHEST_DEFAULT, Items.COBBLESTONE);
		int buffered = 0;
		for (int i = 0; i < QuarryBlockEntity.BUFFER_SLOTS; i++) {
			buffered += quarry.getItem(i).getCount();
		}

		check(cobble == 510 && buffered == 0, "void upgrade deleted only the filtered cobblestone (chest still " + cobble + ", buffer " + buffered + ")");
	}

	private static void verifyData(MinecraftServer server) {
		long recipes = server.getRecipeManager().getRecipes().stream()
			.filter(holder -> holder.id().identifier().getNamespace().equals(SolarQuarry.MOD_ID))
			.count();
		long modItems = net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet().stream()
			.filter(id -> id.getNamespace().equals(SolarQuarry.MOD_ID))
			.count();
		check(recipes == modItems, "one crafting recipe per item loaded (" + recipes + " recipes, " + modItems + " items)");
		java.util.Set<String> recipeIds = new java.util.HashSet<>();
		server.getRecipeManager().getRecipes().forEach(holder -> recipeIds.add(holder.id().identifier().toString()));
		int uncraftable = 0;
		int noUnlock = 0;
		List<Item> all = new ArrayList<>(ModBlocks.BLOCK_ITEMS);
		all.addAll(ModItems.ITEMS);
		for (Item item : all) {
			String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
			if (!recipeIds.contains(id)) {
				uncraftable++;
				SolarQuarry.LOGGER.error("[SelfTest] no crafting recipe for {}", id);
			}
		}

		for (String id : recipeIds) {
			if (!id.startsWith(SolarQuarry.MOD_ID + ":")) {
				continue;
			}

			String path = id.substring(SolarQuarry.MOD_ID.length() + 1);
			boolean found = false;
			for (String category : List.of("redstone", "equipment", "misc")) {
				if (server.getAdvancements().get(SolarQuarry.id("recipes/" + category + "/" + path)) != null) {
					found = true;
				}
			}

			if (!found) {
				noUnlock++;
				SolarQuarry.LOGGER.error("[SelfTest] no recipe-book unlock for {}", id);
			}
		}

		check(uncraftable == 0, "every item and block (" + all.size() + ") has a crafting recipe");
		check(noUnlock == 0, "every recipe unlocks in the recipe book");
		int lootTables = 0;
		for (Item item : ModBlocks.BLOCK_ITEMS) {
			Block block = ((BlockItem) item).getBlock();
			var key = block.getLootTable();
			if (key.isPresent() && server.reloadableRegistries().getLootTable(key.get()) != net.minecraft.world.level.storage.loot.LootTable.EMPTY) {
				lootTables++;
			}

			check(block.defaultBlockState().is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE), "pickaxe tag includes " + block);
		}

		check(lootTables == ModBlocks.BLOCK_ITEMS.size(), "loot tables loaded for every block (" + lootTables + ")");
	}

	private static void startSilkTest(ServerLevel level) {
		for (int x = AREA_MIN_X; x <= AREA_MAX_X; x++) {
			for (int z = 9; z < 25; z++) {
				level.setBlock(new BlockPos(x, 199, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}

		level.setBlock(BEDROCK, Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL);
		QuarryBlockEntity quarry = quarry(level);
		quarry.removeItemNoUpdate(QuarryBlockEntity.BUFFER_SLOTS + 2);
		quarry.setItem(QuarryBlockEntity.BUFFER_SLOTS + 2, new ItemStack(ModItems.SILK_TOUCH_UPGRADE));
		check(!quarry.canInstallUpgrade(QuarryBlockEntity.BUFFER_SLOTS + 4, new ItemStack(ModItems.FORTUNE_UPGRADE)), "fortune is rejected while silk touch is installed");
		check(!quarry.canInstallUpgrade(QuarryBlockEntity.BUFFER_SLOTS + 4, new ItemStack(ModItems.SPEED_UPGRADE)), "duplicate upgrade types are rejected");
		quarry.resetProgress();
		quarry.start();
	}

	private static void verifySilk(ServerLevel level) {
		int stone = count(level, CHEST_DEFAULT, Items.STONE);
		check(stone == 255, "silk touch upgrade mines stone as stone (" + stone + ")");
	}

	private static void startSolar(MinecraftServer server, ServerLevel level) {
		level.setBlock(SOLAR, ModBlocks.SOLAR_PANEL_ELITE.defaultBlockState(), Block.UPDATE_ALL);
		check(level.getBlockState(SOLAR).is(ModBlocks.SOLAR_PANEL_ELITE), "solar panel placed");
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "weather clear");
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set noon");
		noonGeneration = SolarPanelBlockEntity.computeGeneration(level, SOLAR, SolarTier.ELITE);
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set midnight");
		int night = SolarPanelBlockEntity.computeGeneration(level, SOLAR, SolarTier.ELITE);
		SolarQuarry.LOGGER.info("[SelfTest] elite solar: noon={} midnight={}", noonGeneration, night);
		check(noonGeneration > 0, "solar panel generates at noon");
		check(night > 0 && night <= noonGeneration / 5, "elite solar panel drops to its low night output (" + night + ")");
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set noon");
		level.setBlock(SOLAR.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
	}

	private static void verifySolarCovered(ServerLevel level) {
		int covered = SolarPanelBlockEntity.computeGeneration(level, SOLAR, SolarTier.ELITE);
		SolarQuarry.LOGGER.info("[SelfTest] elite solar covered at noon={}", covered);
		check(covered == 0, "solar panel needs sky exposure");
		BlockEntity be = level.getBlockEntity(SOLAR);
		check(be instanceof SolarPanelBlockEntity, "solar panel block entity present");
	}

	private static void finish(MinecraftServer server) {
		if (failures.isEmpty()) {
			SolarQuarry.LOGGER.info("[SelfTest] ALL CHECKS PASSED");
		} else {
			SolarQuarry.LOGGER.error("[SelfTest] {} CHECK(S) FAILED: {}", failures.size(), failures);
		}

		server.halt(false);
	}
}
