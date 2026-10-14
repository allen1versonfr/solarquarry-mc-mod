package com.solarquarry;

import com.solarquarry.block.QuarryBlock;
import com.solarquarry.blockentity.BatteryBlockEntity;
import com.solarquarry.blockentity.QuarryBlockEntity;
import com.solarquarry.quarry.MiningPattern;
import com.solarquarry.quarry.QuarryMode;
import com.solarquarry.quarry.QuarryStatus;
import com.solarquarry.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Self-test part 2: mining modes, rectangular areas, skip rules, configuration validation, state machine
 * transitions and save-data migration. Runs on its own forced chunks after {@link SelfTest}'s checks.
 */
final class SelfTestModes {
	/** Quarry faces north, so its work volume extends south (+Z); lateral +1 is west (-X). */
	private static final BlockPos Q = new BlockPos(100, 200, 100);
	private static final BlockPos BATTERY = Q.east(2);

	private record Scenario(String name, Consumer<ServerLevel> setup, Predicate<QuarryBlockEntity> done, int timeout, Consumer<ServerLevel> verify, boolean timed) {
		Scenario(String name, Consumer<ServerLevel> setup, Predicate<QuarryBlockEntity> done, int timeout, Consumer<ServerLevel> verify) {
			this(name, setup, done, timeout, verify, false);
		}

		/** Runs for exactly {@code ticks} ticks, then verifies. */
		static Scenario timed(String name, Consumer<ServerLevel> setup, int ticks, Consumer<ServerLevel> verify) {
			return new Scenario(name, setup, q -> false, ticks, verify, true);
		}
	}

	private final List<Scenario> scenarios = new ArrayList<>();
	private int index = -1;
	private int stage;
	private int wait;

	SelfTestModes() {
		this.scenarios.add(new Scenario("rectangle + skip rules", this::setupRectangle, QuarryBlockEntity::isFinished, 2000, this::verifyRectangle));
		this.scenarios.add(new Scenario("tunnel bore", this::setupTunnel, QuarryBlockEntity::isFinished, 2000, this::verifyTunnel));
		this.scenarios.add(new Scenario("area clear", this::setupClear, QuarryBlockEntity::isFinished, 2000, this::verifyClear));
		this.scenarios.add(new Scenario("ore priority (ores only)", this::setupOres, QuarryBlockEntity::isFinished, 3000, this::verifyOres));
		this.scenarios.add(new Scenario("ore list narrows targets", this::setupOreList, QuarryBlockEntity::isFinished, 3000, this::verifyOreList));
		this.scenarios.add(new Scenario("waits at unloaded chunk", this::setupUnloaded,
			q -> q.getStatus() == QuarryStatus.WAITING_FOR_CHUNK, 400, this::verifyUnloaded));
		this.scenarios.add(new Scenario("buffer full blocks output", this::setupFull,
			q -> q.getStatus() == QuarryStatus.OUTPUT_BLOCKED, 400, this::verifyFull));
		this.scenarios.add(new Scenario("no power waits", this::setupNoPower,
			q -> q.getStatus() == QuarryStatus.WAITING_FOR_POWER, 400, this::verifyNoPower));
		this.scenarios.add(Scenario.timed("energy: two consumers share one battery", this::setupSharing, 100, this::verifySharing));
		this.scenarios.add(Scenario.timed("energy: full battery stays at capacity", this::setupFullBattery, 100, this::verifyFullBattery));
		this.scenarios.add(Scenario.timed("energy: disconnected cable stops transfer", this::setupDisconnected, 60, this::verifyDisconnected));
		this.scenarios.add(Scenario.timed("energy: solar tiers at night", this::setupNight, 2, this::verifyNight));
		this.scenarios.add(Scenario.timed("pipes: tag filter routes by item tag", this::setupTagRouting, 200, this::verifyTagRouting));
		this.scenarios.add(Scenario.timed("pipes: priority fills the preferred chest first", this::setupPriority, 300, this::verifyPriority));
		this.scenarios.add(Scenario.timed("pipes: recover when a destination is removed", this::setupRemoval, 15, this::removeDestination));
		this.scenarios.add(Scenario.timed("pipes: rerouted items arrive elsewhere", s -> { }, 300, this::verifyRemoval));
		this.scenarios.add(Scenario.timed("pipes: contents and filter settings persist", this::setupPipePersistence, 1, this::verifyPipePersistence));
		this.scenarios.add(Scenario.timed("presets: copy and apply quarry settings", s -> { }, 1, this::verifyQuarryPreset));
		this.scenarios.add(Scenario.timed("presets: filter settings round trip", s -> { }, 1, this::verifyFilterPreset));
		this.scenarios.add(Scenario.timed("terminal: finds quarries on its cable network", this::setupTerminal, 5, this::verifyTerminal));
		this.scenarios.add(Scenario.timed("storage scan: counts connected inventories", this::setupStorage, 2, this::verifyStorage));
		this.scenarios.add(Scenario.timed("phase 1: frame collision and measured mining rate", this::setupFrame, 200, this::verifyFrame));
		this.scenarios.add(Scenario.timed("phase 1: upgrade limits and creative tab", s -> { }, 1, this::verifyUpgradeLimits));
		this.scenarios.add(new Scenario("B2: torches light the pit walls, XP is collected", this::setupTorches, QuarryBlockEntity::isFinished, 1200, this::verifyTorches));
		this.scenarios.add(Scenario.timed("B2: legs reach the ground, the machine gets a support", this::setupLegs, 5, this::verifyLegs));
		this.scenarios.add(new Scenario("phase 2: liquid remover pumps water into a tank", this::setupLiquid, q -> q.isFinished() && q.getPumpAmount() == 0, 1600, this::verifyLiquid));
		this.scenarios.add(new Scenario("phase 2: liquid remover with a void upgrade deletes liquid", this::setupLiquidVoid, QuarryBlockEntity::isFinished, 1600, this::verifyLiquidVoid));
		this.scenarios.add(Scenario.timed("phase 2: storage crates hold pages of items", s -> { }, 1, this::verifyCrates));
		this.scenarios.add(Scenario.timed("phase 3: super smelter runs three lanes at 4x speed", this::setupSmelter, 120, this::verifySmelterRun));
		this.scenarios.add(Scenario.timed("phase 3: super smelter pauses without power", this::starveSmelter, 30, this::verifySmelterStarved));
		this.scenarios.add(Scenario.timed("phase 3: super smelter stops when the output is full", this::fillSmelterOutput, 30, this::verifySmelterFull));
		this.scenarios.add(new Scenario("phase 3: auto-smelter upgrade smelts mined ores", this::setupAutoSmelter, QuarryBlockEntity::isFinished, 1200, this::verifyAutoSmelter));
		this.scenarios.add(Scenario.timed("phase 3: fabrication bench catalogue and crafting", s -> { }, 1, this::verifyBench));
		this.scenarios.add(Scenario.timed("phase 2: pipe tiers move items at different speeds", this::setupPipeTiers, 30, this::verifyPipeTiersEarly));
		this.scenarios.add(Scenario.timed("phase 2: basic pipe delivers too, just slower", s -> { }, 80, this::verifyPipeTiersLate));
		this.scenarios.add(Scenario.timed("phase 2: sorting terminal routes by rules", this::setupSorting, 2, this::verifySortingRoutes));
		this.scenarios.add(Scenario.timed("phase 2: sorting terminal delivers end to end", this::startSortingDelivery, 120, this::verifySortingDelivery));
		this.scenarios.add(Scenario.timed("phase 2: sorting terminal menu, persistence and recovery", s -> { }, 1, this::verifySortingMenuAndRecovery));
		this.scenarios.add(new Scenario("protection: claimed blocks are skipped (owner offline)", this::setupProtection, QuarryBlockEntity::isFinished, 2000, this::verifyProtection));
		this.scenarios.add(Scenario.timed("prospector: exact ore count of the remaining volume", this::setupProspector, 1, this::verifyProspector));
		this.scenarios.add(Scenario.timed("config: command, sync codec and multipliers", s -> { }, 1, this::verifyConfig));
		this.scenarios.add(Scenario.timed("chunk loader: set up a quarry far from everything", this::setupChunkLoader, 40, this::releaseForcedChunk));
		this.scenarios.add(Scenario.timed("chunk loader: keeps its own chunks running", s -> { }, 120, this::verifyChunkLoaderRunning));
		this.scenarios.add(Scenario.timed("chunk loader: loads the chunks it mines", s -> { }, 600, this::verifyChunkLoaderFinished));
		this.scenarios.add(Scenario.timed("chunk loader: chunks unload after it stops", s -> { }, 500, this::verifyChunkLoaderUnloaded));
		this.scenarios.add(Scenario.timed("chunk loader: remembered quarries get their chunks back", this::restoreChunkLoader, 80, this::verifyChunkLoaderRestored));
		this.scenarios.add(Scenario.timed("chunk loader: an unpowered quarry keeps only its own chunks", this::setupChunkLoaderNoPower, 40, this::releaseForcedChunk));
		this.scenarios.add(Scenario.timed("chunk loader: waits for power without losing its chunks", s -> { }, 160, this::verifyChunkLoaderNoPower));
	}

	static void forceChunks(ServerLevel level) {
		for (int cx = 5; cx <= 9; cx++) {
			for (int cz = 5; cz <= 7; cz++) {
				level.setChunkForced(cx, cz, true);
			}
		}
	}

	static boolean chunksReady(ServerLevel level) {
		return level.isLoaded(new BlockPos(88, 200, 88)) && level.isLoaded(new BlockPos(120, 200, 120)) && level.isLoaded(new BlockPos(150, 200, 110));
	}

	/** @return true when every scenario has finished */
	boolean tick(MinecraftServer server, ServerLevel level) {
		if (this.index < 0) {
			this.verifyConfigAndMigration(level);
			this.index = 0;
			this.stage = 0;
		}

		if (this.index >= this.scenarios.size()) {
			return true;
		}

		this.watchFarQuarry(level);

		Scenario scenario = this.scenarios.get(this.index);
		switch (this.stage) {
			case 0 -> {
				SolarQuarry.LOGGER.info("[SelfTest] scenario: {}", scenario.name());
				this.resetSite(level);
				scenario.setup().accept(level);
				this.stage = 1;
				this.wait = 0;
			}
			case 1 -> {
				if (scenario.timed()) {
					if (++this.wait >= scenario.timeout()) {
						scenario.verify().accept(level);
						this.index++;
						this.stage = 0;
					}
				} else if (scenario.done().test(quarry(level)) || ++this.wait > scenario.timeout()) {
					QuarryBlockEntity quarry = quarry(level);
					SelfTest.check(this.wait <= scenario.timeout(), scenario.name() + ": reached expected state (" + this.wait + " ticks, status " + quarry.getStatus() + ")");
					scenario.verify().accept(level);
					this.index++;
					this.stage = 0;
				}
			}
			default -> {
			}
		}

		return false;
	}

	// ---------------------------------------------------------------------------------------------
	// Site helpers
	// ---------------------------------------------------------------------------------------------

	private void resetSite(ServerLevel level) {
		for (int x = 90; x <= 112; x++) {
			for (int z = 96; z <= 112; z++) {
				for (int y = 195; y <= 210; y++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (!level.getBlockState(pos).isAir()) {
						if (level.getBlockEntity(pos) instanceof Container container) {
							container.clearContent();
						}

						level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
		}

		level.setBlock(Q, ModBlocks.QUARRY.defaultBlockState().setValue(QuarryBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
		level.setBlock(Q.east(), ModBlocks.ENERGY_CABLE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(BATTERY, ModBlocks.BATTERY_ELITE.defaultBlockState(), Block.UPDATE_ALL);
		((BatteryBlockEntity) level.getBlockEntity(BATTERY)).restoreEnergy(5_000_000L);
		// Output: a chest on top of the quarry collects everything.
		level.setBlock(Q.above(), Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
	}

	private static QuarryBlockEntity quarry(ServerLevel level) {
		BlockEntity be = level.getBlockEntity(Q);
		if (!(be instanceof QuarryBlockEntity quarry)) {
			throw new IllegalStateException("mode-test quarry missing");
		}

		return quarry;
	}

	/** World position of a cell: lateral offset (west positive), dy, forward (south). */
	private static BlockPos cell(int lateralOffset, int dy, int forward) {
		return Q.west(lateralOffset).above(dy).south(forward);
	}

	private static void fill(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	private static int countBlocks(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
		int n = 0;
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					if (level.getBlockState(new BlockPos(x, y, z)).is(block)) {
						n++;
					}
				}
			}
		}

		return n;
	}

	private static void configure(QuarryBlockEntity quarry, QuarryMode mode, int width, int length, int height, int targetY) {
		quarry.setQuarryMode(mode);
		quarry.setWidth(width);
		quarry.setLength(length);
		quarry.setHeight(height);
		quarry.setTargetY(targetY);
	}

	// ---------------------------------------------------------------------------------------------
	// Scenarios
	// ---------------------------------------------------------------------------------------------

	private void setupRectangle(ServerLevel level) {
		// 5 wide (x 98..102), 3 long (z 101..103), two layers (y 199..200), plus a stone ring outside the rectangle.
		fill(level, 97, 199, 101, 103, 200, 104, Blocks.STONE.defaultBlockState());
		level.setBlock(cell(0, 0, 2), Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		((Container) level.getBlockEntity(cell(0, 0, 2))).setItem(0, new ItemStack(Items.DIAMOND, 3));
		level.setBlock(cell(1, -1, 1), Blocks.SPAWNER.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(cell(-1, -1, 3), Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
		QuarryBlockEntity quarry = quarry(level);
		configure(quarry, QuarryMode.STRIP_MINE, 5, 3, 1, 199);
		quarry.getSkipFilter().setItem(0, new ItemStack(Items.DIRT));
		quarry.start();
	}

	private void verifyRectangle(ServerLevel level) {
		int stoneInside = countBlocks(level, 98, 199, 101, 102, 200, 103, Blocks.STONE);
		int stoneRing = countBlocks(level, 97, 199, 101, 103, 200, 104, Blocks.STONE) - stoneInside;
		SelfTest.check(stoneInside == 0, "rectangle: every stone block inside the 5x3 area was mined (" + stoneInside + " left)");
		SelfTest.check(stoneRing == 2 * (7 * 4 - 5 * 3), "rectangle: blocks outside the area were left alone (" + stoneRing + ")");
		BlockState chest = level.getBlockState(cell(0, 0, 2));
		SelfTest.check(chest.is(Blocks.CHEST) && ((Container) level.getBlockEntity(cell(0, 0, 2))).getItem(0).getCount() == 3,
			"rectangle: containers are skipped and keep their contents");
		SelfTest.check(level.getBlockState(cell(1, -1, 1)).is(Blocks.SPAWNER), "rectangle: data-pack ignored blocks (spawner) are skipped");
		SelfTest.check(level.getBlockState(cell(-1, -1, 3)).is(Blocks.DIRT), "rectangle: skip-list blocks are left in place");
		SelfTest.check(quarry(level).getBlocksMined() == 27, "rectangle: mined exactly 27 blocks (" + quarry(level).getBlocksMined() + ")");
		quarry(level).getSkipFilter().clearContent();
	}

	private void setupTunnel(ServerLevel level) {
		fill(level, 98, 200, 101, 102, 203, 107, Blocks.STONE.defaultBlockState());
		QuarryBlockEntity quarry = quarry(level);
		configure(quarry, QuarryMode.TUNNEL_BORE, 3, 5, 2, 199);
		quarry.start();
	}

	private void verifyTunnel(ServerLevel level) {
		int bored = 15 * 2 - countBlocks(level, 99, 200, 101, 101, 201, 105, Blocks.STONE);
		SelfTest.check(bored == 30, "tunnel: 3 wide x 2 high x 5 long tunnel was bored (" + bored + "/30)");
		SelfTest.check(countBlocks(level, 99, 202, 101, 101, 202, 105, Blocks.STONE) == 15, "tunnel: roof above the tunnel height is intact");
		SelfTest.check(countBlocks(level, 99, 200, 106, 101, 201, 106, Blocks.STONE) == 6, "tunnel: stops at the configured length");
		SelfTest.check(level.getBlockState(new BlockPos(98, 200, 103)).is(Blocks.STONE), "tunnel: side walls are intact");
	}

	private void setupClear(ServerLevel level) {
		fill(level, 99, 200, 101, 101, 205, 103, Blocks.STONE.defaultBlockState());
		QuarryBlockEntity quarry = quarry(level);
		configure(quarry, QuarryMode.AREA_CLEAR, 3, 3, 3, 199);
		quarry.start();
	}

	private void verifyClear(ServerLevel level) {
		SelfTest.check(countBlocks(level, 99, 200, 101, 101, 202, 103, Blocks.STONE) == 0, "area clear: the 3x3x3 volume above the quarry level was cleared");
		SelfTest.check(countBlocks(level, 99, 203, 101, 101, 205, 103, Blocks.STONE) == 27, "area clear: blocks above the clear height remain");
	}

	private void setupOres(ServerLevel level) {
		fill(level, 99, 197, 101, 102, 200, 104, Blocks.STONE.defaultBlockState());
		level.setBlock(new BlockPos(100, 200, 102), Blocks.IRON_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(new BlockPos(101, 198, 104), Blocks.IRON_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(new BlockPos(99, 197, 101), Blocks.DEEPSLATE_IRON_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(new BlockPos(102, 199, 103), Blocks.COAL_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
		QuarryBlockEntity quarry = quarry(level);
		configure(quarry, QuarryMode.ORE_PRIORITY, 4, 4, 1, 197);
		quarry.setOresOnly(true);
		quarry.getOreFilter().clearContent();
		quarry.start();
	}

	private void verifyOres(ServerLevel level) {
		int ores = countBlocks(level, 99, 197, 101, 102, 200, 104, Blocks.IRON_ORE)
			+ countBlocks(level, 99, 197, 101, 102, 200, 104, Blocks.DEEPSLATE_IRON_ORE)
			+ countBlocks(level, 99, 197, 101, 102, 200, 104, Blocks.COAL_ORE);
		int stone = countBlocks(level, 99, 197, 101, 102, 200, 104, Blocks.STONE);
		SelfTest.check(ores == 0, "ore priority: all 4 ores in the volume were mined (" + ores + " left)");
		SelfTest.check(stone == 4 * 4 * 4 - 4, "ore priority (ores only): surrounding stone was left in place (" + stone + ")");
		Container chest = (Container) level.getBlockEntity(Q.above());
		int rawIron = 0;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (chest.getItem(i).is(Items.RAW_IRON)) {
				rawIron += chest.getItem(i).getCount();
			}
		}

		SelfTest.check(rawIron >= 3, "ore priority: ore drops reached the output chest (" + rawIron + " raw iron)");
	}

	private void setupOreList(ServerLevel level) {
		fill(level, 99, 199, 101, 102, 200, 104, Blocks.STONE.defaultBlockState());
		level.setBlock(new BlockPos(100, 200, 102), Blocks.IRON_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(new BlockPos(102, 199, 103), Blocks.COAL_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
		QuarryBlockEntity quarry = quarry(level);
		configure(quarry, QuarryMode.ORE_PRIORITY, 4, 4, 1, 199);
		quarry.setOresOnly(true);
		quarry.getOreFilter().setItem(0, new ItemStack(Items.COAL_ORE));
		quarry.resetProgress();
		quarry.start();
	}

	private void verifyOreList(ServerLevel level) {
		SelfTest.check(level.getBlockState(new BlockPos(102, 199, 103)).isAir(), "ore list: the listed ore (coal) was mined");
		SelfTest.check(level.getBlockState(new BlockPos(100, 200, 102)).is(Blocks.IRON_ORE), "ore list: unlisted ores (iron) were left alone");
		quarry(level).getOreFilter().clearContent();
		quarry(level).setOresOnly(false);
	}

	private void setupUnloaded(ServerLevel level) {
		// A 4x-long tunnel runs south into chunks that are not loaded (z >= 128 is outside the forced 3x3 chunks).
		QuarryBlockEntity quarry = quarry(level);
		configure(quarry, QuarryMode.TUNNEL_BORE, 1, 64, 1, 199);
		quarry.start();
	}

	private void verifyUnloaded(ServerLevel level) {
		QuarryBlockEntity quarry = quarry(level);
		SelfTest.check(quarry.getStatus() == QuarryStatus.WAITING_FOR_CHUNK && !quarry.isFinished(),
			"unloaded chunk: quarry waits instead of skipping or loading chunks (" + quarry.getStatus() + ")");
		quarry.pause();
	}

	private void setupFull(ServerLevel level) {
		level.setBlock(Q.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		fill(level, 99, 200, 101, 101, 200, 103, Blocks.STONE.defaultBlockState());
		QuarryBlockEntity quarry = quarry(level);
		for (int i = 0; i < QuarryBlockEntity.BUFFER_SLOTS; i++) {
			quarry.setItem(i, new ItemStack(Items.DIRT, 64));
		}

		configure(quarry, QuarryMode.STRIP_MINE, 3, 3, 1, 200);
		quarry.resetProgress();
		quarry.start();
	}

	private void verifyFull(ServerLevel level) {
		QuarryBlockEntity quarry = quarry(level);
		SelfTest.check(quarry.getStatus() == QuarryStatus.OUTPUT_BLOCKED, "full buffer: status is Output Full");
		SelfTest.check(countBlocks(level, 99, 200, 101, 101, 200, 103, Blocks.STONE) == 9, "full buffer: nothing was mined (drops would be lost)");
		for (int i = 0; i < QuarryBlockEntity.BUFFER_SLOTS; i++) {
			quarry.setItem(i, ItemStack.EMPTY);
		}

		quarry.pause();
	}

	private void setupNoPower(ServerLevel level) {
		level.setBlock(Q.east(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		fill(level, 99, 200, 101, 101, 200, 103, Blocks.STONE.defaultBlockState());
		QuarryBlockEntity quarry = quarry(level);
		// Drain whatever is stored: rebuild the block entity from scratch.
		level.setBlock(Q, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(Q, ModBlocks.QUARRY.defaultBlockState().setValue(QuarryBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
		quarry = quarry(level);
		configure(quarry, QuarryMode.STRIP_MINE, 3, 3, 1, 200);
		quarry.start();
	}

	private void verifyNoPower(ServerLevel level) {
		QuarryBlockEntity quarry = quarry(level);
		SelfTest.check(quarry.getStatus() == QuarryStatus.WAITING_FOR_POWER && quarry.getEnergy() < 100, "no power: status is No Power");
		SelfTest.check(countBlocks(level, 99, 200, 101, 101, 200, 103, Blocks.STONE) == 9, "no power: nothing was mined");
		quarry.pause();
	}

	// ---------------------------------------------------------------------------------------------
	// Energy
	// ---------------------------------------------------------------------------------------------

	private static final BlockPos Q2 = Q.east(4);
	private static final BlockPos PANEL = Q.east().north();
	private long sharedStart;

	private void setupSharing(ServerLevel level) {
		// Quarry - cable - BASIC battery (256 E/t) - cable - quarry 2. Both quarries are idle and empty.
		level.setBlock(BATTERY, ModBlocks.BATTERY_BASIC.defaultBlockState(), Block.UPDATE_ALL);
		BatteryBlockEntity battery = (BatteryBlockEntity) level.getBlockEntity(BATTERY);
		battery.restoreEnergy(200_000L);
		this.sharedStart = battery.getEnergy();
		level.setBlock(Q.east(3), ModBlocks.ENERGY_CABLE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(Q2, ModBlocks.QUARRY.defaultBlockState().setValue(QuarryBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
	}

	private void verifySharing(ServerLevel level) {
		BatteryBlockEntity battery = (BatteryBlockEntity) level.getBlockEntity(BATTERY);
		long a = quarry(level).getEnergy();
		long b = ((QuarryBlockEntity) level.getBlockEntity(Q2)).getEnergy();
		long total = a + b + battery.getEnergy();
		SolarQuarry.LOGGER.info("[SelfTest] sharing: quarry A={} quarry B={} battery={} (start {})", a, b, battery.getEnergy(), this.sharedStart);
		SelfTest.check(a > 0 && b > 0, "energy: both consumers on one network receive power (" + a + " / " + b + ")");
		SelfTest.check(Math.abs(a - b) <= Math.max(a, b) / 4, "energy: power is shared fairly between consumers");
		SelfTest.check(a + b <= 256L * 101, "energy: battery output respects its 256 E/t transfer limit (" + (a + b) + " in 100 ticks)");
		SelfTest.check(total == this.sharedStart, "energy: nothing created or lost while transferring (" + total + " vs " + this.sharedStart + ")");
		level.setBlock(Q2, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
	}

	private void setupFullBattery(ServerLevel level) {
		BatteryBlockEntity battery = (BatteryBlockEntity) level.getBlockEntity(BATTERY);
		battery.restoreEnergy(battery.getEnergyCapacity());
		// Disconnect the quarry so only the panel and the battery share the network.
		level.setBlock(Q, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(PANEL, ModBlocks.SOLAR_PANEL_ELITE.defaultBlockState(), Block.UPDATE_ALL);
		level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(), "time set noon");
	}

	private void verifyFullBattery(ServerLevel level) {
		BatteryBlockEntity battery = (BatteryBlockEntity) level.getBlockEntity(BATTERY);
		var panel = (com.solarquarry.blockentity.SolarPanelBlockEntity) level.getBlockEntity(PANEL);
		SelfTest.check(battery.getEnergy() == battery.getEnergyCapacity(), "energy: a full battery never exceeds its capacity (" + battery.getEnergy() + ")");
		SelfTest.check(panel.getEnergy() <= panel.getEnergyCapacity() && panel.getEnergy() > 0,
			"energy: the panel's own buffer fills and caps when nothing can take power (" + panel.getEnergy() + "/" + panel.getEnergyCapacity() + ")");
		level.setBlock(Q, ModBlocks.QUARRY.defaultBlockState().setValue(QuarryBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
	}

	private long disconnectedEnergy;
	private long disconnectedBattery;

	private void setupDisconnected(ServerLevel level) {
		level.setBlock(Q.east(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		quarry(level).restoreEnergy(5_000);
		this.disconnectedEnergy = quarry(level).getEnergy();
		this.disconnectedBattery = ((BatteryBlockEntity) level.getBlockEntity(BATTERY)).getEnergy();
	}

	private void verifyDisconnected(ServerLevel level) {
		long now = quarry(level).getEnergy();
		long battery = ((BatteryBlockEntity) level.getBlockEntity(BATTERY)).getEnergy();
		SelfTest.check(now == this.disconnectedEnergy && battery == this.disconnectedBattery,
			"energy: no power flows once the cable is removed (quarry " + this.disconnectedEnergy + " -> " + now + ", battery " + this.disconnectedBattery + " -> " + battery + ")");
	}

	private void setupNight(ServerLevel level) {
		level.setBlock(PANEL, ModBlocks.SOLAR_PANEL_ELITE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(PANEL.west(), ModBlocks.SOLAR_PANEL_BASIC.defaultBlockState(), Block.UPDATE_ALL);
	}

	private void verifyNight(ServerLevel level) {
		var commands = level.getServer().getCommands();
		var source = level.getServer().createCommandSourceStack();
		commands.performPrefixedCommand(source, "time set midnight");
		int elite = com.solarquarry.blockentity.SolarPanelBlockEntity.computeGeneration(level, PANEL, com.solarquarry.energy.SolarTier.ELITE);
		int basic = com.solarquarry.blockentity.SolarPanelBlockEntity.computeGeneration(level, PANEL.west(), com.solarquarry.energy.SolarTier.BASIC);
		commands.performPrefixedCommand(source, "time set noon");
		int eliteNoon = com.solarquarry.blockentity.SolarPanelBlockEntity.computeGeneration(level, PANEL, com.solarquarry.energy.SolarTier.ELITE);
		int basicNoon = com.solarquarry.blockentity.SolarPanelBlockEntity.computeGeneration(level, PANEL.west(), com.solarquarry.energy.SolarTier.BASIC);
		SolarQuarry.LOGGER.info("[SelfTest] solar: basic noon={} night={}, elite noon={} night={}", basicNoon, basic, eliteNoon, elite);
		SelfTest.check(basic == 0, "energy: basic panels produce nothing at night");
		SelfTest.check(elite > 0 && elite <= eliteNoon / 5, "energy: elite panels keep a small night output (" + elite + " of " + eliteNoon + ")");
		SelfTest.check(eliteNoon > basicNoon * 10, "energy: higher tiers generate far more (" + eliteNoon + " vs " + basicNoon + ")");
	}

	// ---------------------------------------------------------------------------------------------
	// Logistics (separate site so the quarry tests above are untouched)
	// ---------------------------------------------------------------------------------------------

	private static final BlockPos L = new BlockPos(140, 200, 110);
	private static final BlockPos L_PIPE1 = L.east();
	private static final BlockPos L_PIPE2 = L.east(2);
	private static final BlockPos L_FILTER = L.east(3);
	private static final BlockPos L_CHEST_A = L.east(4);
	private static final BlockPos L_BRANCH = L.east(2).north();
	private static final BlockPos L_CHEST_B = L.east(2).north(2);

	private QuarryBlockEntity logisticsQuarry(ServerLevel level) {
		return (QuarryBlockEntity) level.getBlockEntity(L);
	}

	private void resetLogistics(ServerLevel level) {
		for (int x = 136; x <= 148; x++) {
			for (int z = 104; z <= 116; z++) {
				for (int y = 199; y <= 202; y++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (!level.getBlockState(pos).isAir()) {
						if (level.getBlockEntity(pos) instanceof Container container) {
							container.clearContent();
						}

						level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
		}

		for (net.minecraft.world.entity.item.ItemEntity e : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(L).inflate(10))) {
			e.discard();
		}

		level.setBlock(L, ModBlocks.QUARRY.defaultBlockState().setValue(QuarryBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
		level.setBlock(L_PIPE1, ModBlocks.ITEM_PIPE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(L_PIPE2, ModBlocks.ITEM_PIPE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(L_FILTER, ModBlocks.FILTER_PIPE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(L_CHEST_A, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(L_BRANCH, ModBlocks.ITEM_PIPE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(L_CHEST_B, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
	}

	private static int countItem(ServerLevel level, BlockPos pos, net.minecraft.world.item.Item item) {
		int n = 0;
		if (level.getBlockEntity(pos) instanceof Container container) {
			for (int i = 0; i < container.getContainerSize(); i++) {
				ItemStack stack = container.getItem(i);
				if (item == null ? !stack.isEmpty() : stack.is(item)) {
					n += stack.getCount();
				}
			}
		}

		return n;
	}

	private int inTransit(ServerLevel level) {
		int n = 0;
		for (BlockPos pos : List.of(L_PIPE1, L_PIPE2, L_FILTER, L_BRANCH)) {
			if (level.getBlockEntity(pos) instanceof com.solarquarry.blockentity.ItemPipeBlockEntity pipe) {
				for (var item : pipe.getItems()) {
					n += item.stack.getCount();
				}
			}
		}

		return n;
	}

	private void setupTagRouting(ServerLevel level) {
		this.resetLogistics(level);
		var filter = (com.solarquarry.blockentity.ItemPipeBlockEntity) level.getBlockEntity(L_FILTER);
		filter.setWhitelist(true);
		SelfTest.check(filter.setTag(0, "c:raw_materials"), "pipes: a valid tag id is accepted");
		SelfTest.check(!filter.setTag(1, "Not A Tag!!"), "pipes: an invalid tag id is rejected");
		QuarryBlockEntity quarry = this.logisticsQuarry(level);
		quarry.setItem(0, new ItemStack(Items.RAW_IRON, 10));
		quarry.setItem(1, new ItemStack(Items.COBBLESTONE, 20));
		quarry.setItem(2, new ItemStack(Items.RAW_GOLD, 3));
	}

	private void verifyTagRouting(ServerLevel level) {
		int ironA = countItem(level, L_CHEST_A, Items.RAW_IRON);
		int goldA = countItem(level, L_CHEST_A, Items.RAW_GOLD);
		int cobbleB = countItem(level, L_CHEST_B, Items.COBBLESTONE);
		int totalA = countItem(level, L_CHEST_A, null);
		int totalB = countItem(level, L_CHEST_B, null);
		SolarQuarry.LOGGER.info("[SelfTest] tag routing: A iron={} gold={} total={}, B cobble={} total={}", ironA, goldA, totalA, cobbleB, totalB);
		SelfTest.check(ironA == 10 && goldA == 3 && totalA == 13, "pipes: items in the tag (raw iron, raw gold) went through the tag filter");
		SelfTest.check(cobbleB == 20 && totalB == 20, "pipes: other items took the plain branch");
	}

	private void setupPriority(ServerLevel level) {
		this.resetLogistics(level);
		var filter = (com.solarquarry.blockentity.ItemPipeBlockEntity) level.getBlockEntity(L_FILTER);
		filter.setWhitelist(false);
		filter.setPriority(3);
		Container chestA = (Container) level.getBlockEntity(L_CHEST_A);
		for (int i = 1; i < chestA.getContainerSize(); i++) {
			chestA.setItem(i, new ItemStack(Items.DIRT, 64));
		}

		QuarryBlockEntity quarry = this.logisticsQuarry(level);
		quarry.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		quarry.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
	}

	private void verifyPriority(ServerLevel level) {
		int a = countItem(level, L_CHEST_A, Items.COBBLESTONE);
		int b = countItem(level, L_CHEST_B, Items.COBBLESTONE);
		SolarQuarry.LOGGER.info("[SelfTest] priority: A={} B={} transit={}", a, b, this.inTransit(level));
		SelfTest.check(a == 64, "pipes: the priority +3 destination was filled first (" + a + ")");
		SelfTest.check(b == 64, "pipes: overflow went to the lower-priority destination (" + b + ")");
		SelfTest.check(this.inTransit(level) == 0 && this.logisticsQuarry(level).getUsedBufferSlots() == 0, "pipes: nothing left behind, nothing duplicated (128 in, 128 out)");
	}

	private void setupRemoval(ServerLevel level) {
		this.resetLogistics(level);
		var filter = (com.solarquarry.blockentity.ItemPipeBlockEntity) level.getBlockEntity(L_FILTER);
		filter.setWhitelist(false);
		filter.setPriority(3);
		QuarryBlockEntity quarry = this.logisticsQuarry(level);
		for (int i = 0; i < 4; i++) {
			quarry.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
	}

	private void removeDestination(ServerLevel level) {
		Container chestA = (Container) level.getBlockEntity(L_CHEST_A);
		int already = countItem(level, L_CHEST_A, Items.COBBLESTONE);
		this.removedEarly = already;
		chestA.clearContent();
		level.setBlock(L_CHEST_A, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		SolarQuarry.LOGGER.info("[SelfTest] removal: chest A held {} before removal, {} in transit", already, this.inTransit(level));
	}

	private int removedEarly;

	private void verifyRemoval(ServerLevel level) {
		int b = countItem(level, L_CHEST_B, Items.COBBLESTONE);
		int transit = this.inTransit(level);
		int buffered = 0;
		QuarryBlockEntity quarry = this.logisticsQuarry(level);
		for (int i = 0; i < QuarryBlockEntity.BUFFER_SLOTS; i++) {
			buffered += quarry.getItem(i).getCount();
		}

		int dropped = 0;
		for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(L).inflate(10))) {
			dropped += e.getItem().getCount();
		}

		SolarQuarry.LOGGER.info("[SelfTest] removal: B={} early={} transit={} buffer={} dropped={}", b, this.removedEarly, transit, buffered, dropped);
		SelfTest.check(b + this.removedEarly + transit + buffered == 256 && dropped == 0,
			"pipes: every item is accounted for after a destination vanished (" + (b + this.removedEarly + transit + buffered) + "/256, none dropped)");
		SelfTest.check(transit == 0 && buffered == 0, "pipes: in-flight items rerouted to the remaining chest");
	}

	private void setupPipePersistence(ServerLevel level) {
		this.resetLogistics(level);
		var filter = (com.solarquarry.blockentity.ItemPipeBlockEntity) level.getBlockEntity(L_FILTER);
		filter.setWhitelist(true);
		filter.setPriority(-2);
		filter.setTag(2, "minecraft:logs");
		var pipe = (com.solarquarry.blockentity.ItemPipeBlockEntity) level.getBlockEntity(L_PIPE1);
		pipe.acceptFromSource(new ItemStack(Items.COBBLESTONE, 5), Direction.WEST);
	}

	private void verifyPipePersistence(ServerLevel level) {
		var pipe = (com.solarquarry.blockentity.ItemPipeBlockEntity) level.getBlockEntity(L_PIPE1);
		CompoundTag tag = pipe.saveWithFullMetadata(level.registryAccess());
		BlockEntity copy = BlockEntity.loadStatic(L_PIPE1, level.getBlockState(L_PIPE1), tag, level.registryAccess());
		int saved = 0;
		if (copy instanceof com.solarquarry.blockentity.ItemPipeBlockEntity loaded) {
			for (var item : loaded.getItems()) {
				saved += item.stack.getCount();
			}
		}

		SelfTest.check(saved == 5, "pipes: items in transit survive save/load (" + saved + ")");
		var filter = (com.solarquarry.blockentity.ItemPipeBlockEntity) level.getBlockEntity(L_FILTER);
		CompoundTag ftag = filter.saveWithFullMetadata(level.registryAccess());
		BlockEntity fcopy = BlockEntity.loadStatic(L_FILTER, level.getBlockState(L_FILTER), ftag, level.registryAccess());
		SelfTest.check(fcopy instanceof com.solarquarry.blockentity.ItemPipeBlockEntity f && f.getTag(2).equals("minecraft:logs") && f.isWhitelist(),
			"pipes: filter mode and tags survive save/load");
		this.resetLogistics(level);
	}

	// ---------------------------------------------------------------------------------------------
	// Presets, terminal and storage view
	// ---------------------------------------------------------------------------------------------

	private void verifyQuarryPreset(ServerLevel level) {
		QuarryBlockEntity source = quarry(level);
		configure(source, QuarryMode.TUNNEL_BORE, 3, 40, 4, 180);
		source.getVoidFilter().setItem(0, new ItemStack(Items.COBBLESTONE));
		source.getSkipFilter().setItem(1, new ItemStack(Items.DIRT));
		CompoundTag preset = source.writePreset();

		level.setBlock(Q2, ModBlocks.QUARRY.defaultBlockState().setValue(QuarryBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
		QuarryBlockEntity target = (QuarryBlockEntity) level.getBlockEntity(Q2);
		target.setItem(5, new ItemStack(Items.DIAMOND, 9));
		target.setItem(QuarryBlockEntity.BUFFER_SLOTS, new ItemStack(com.solarquarry.registry.ModItems.SPEED_UPGRADE, 2));
		target.restoreEnergy(777);
		target.applyPreset(preset);
		SelfTest.check(target.getQuarryMode() == QuarryMode.TUNNEL_BORE && target.getWidth() == 3 && target.getLength() == 40 && target.getHeight() == 4,
			"presets: mode and size were applied");
		SelfTest.check(target.getVoidFilter().getItem(0).is(Items.COBBLESTONE) && target.getSkipFilter().getItem(1).is(Items.DIRT), "presets: void and skip lists were applied");
		SelfTest.check(target.getItem(5).getCount() == 9 && target.getItem(QuarryBlockEntity.BUFFER_SLOTS).getCount() == 2 && target.getEnergy() == 777,
			"presets: items, upgrades and energy were not touched");

		CompoundTag future = preset.copy();
		future.putInt("Version", 99);
		target.setWidth(5);
		target.applyPreset(future);
		SelfTest.check(target.getWidth() == 5, "presets: a card from a newer format is refused without changes");

		CompoundTag broken = preset.copy();
		broken.putString("QuarryMode", "plasma_cutter");
		broken.putInt("Width", 9999);
		net.minecraft.nbt.ListTag bad = new net.minecraft.nbt.ListTag();
		bad.add(net.minecraft.nbt.StringTag.valueOf("notamod:nothing"));
		broken.put("SkipList", bad);
		target.applyPreset(broken);
		SelfTest.check(target.getQuarryMode() == QuarryMode.STRIP_MINE && target.getWidth() == 16 && target.getSkipFilter().getItem(0).isEmpty(),
			"presets: invalid values fall back to safe defaults");
		level.setBlock(Q2, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
	}

	private void verifyFilterPreset(ServerLevel level) {
		this.resetLogistics(level);
		var filter = (com.solarquarry.blockentity.ItemPipeBlockEntity) level.getBlockEntity(L_FILTER);
		filter.setWhitelist(true);
		filter.setPriority(4);
		filter.setTag(1, "c:ingots");
		filter.getFilter().setItem(0, new ItemStack(Items.RAW_COPPER));
		CompoundTag preset = filter.writePreset(level.registryAccess());
		level.setBlock(L_PIPE2, ModBlocks.FILTER_PIPE.defaultBlockState(), Block.UPDATE_ALL);
		var other = (com.solarquarry.blockentity.ItemPipeBlockEntity) level.getBlockEntity(L_PIPE2);
		other.applyPreset(preset, level.registryAccess());
		SelfTest.check(other.isWhitelist() && other.getPriority() == 4 && other.getTag(1).equals("c:ingots") && other.getFilter().getItem(0).is(Items.RAW_COPPER),
			"presets: filter mode, priority, tags and templates copied");
		this.resetLogistics(level);
	}

	private static final BlockPos TERMINAL = Q.east().north(2);

	private void setupTerminal(ServerLevel level) {
		// One cable run links the terminal, quarry 1 and quarry 2 (machines don't pass the link through themselves).
		for (BlockPos cable : List.of(Q.east().north(), Q.east(2).north(), Q.east(3).north(), Q.east(3))) {
			level.setBlock(cable, ModBlocks.ENERGY_CABLE.defaultBlockState(), Block.UPDATE_ALL);
		}

		level.setBlock(TERMINAL, ModBlocks.REMOTE_TERMINAL.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(Q2, ModBlocks.QUARRY.defaultBlockState().setValue(QuarryBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
	}

	private void verifyTerminal(ServerLevel level) {
		var terminal = (com.solarquarry.blockentity.TerminalBlockEntity) level.getBlockEntity(TERMINAL);
		var found = terminal.findQuarries();
		SelfTest.check(found.size() == 2, "terminal: sees both quarries through the cables (" + found.size() + ")");
		SelfTest.check(terminal.findQuarry(Q) != null && terminal.findQuarry(new BlockPos(0, 64, 0)) == null, "terminal: only machines on its network can be addressed");
		level.setBlock(Q.east(3), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		com.solarquarry.energy.EnergyNetwork.markTopologyChanged();
		SelfTest.check(terminal.findQuarries().size() == 1, "terminal: a disconnected quarry drops off the list");
		// Reconnect quarry 2 only through a battery: the link passes through energy machines.
		level.setBlock(Q2.north(), ModBlocks.BATTERY_BASIC.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(Q2.north().west(), ModBlocks.ENERGY_CABLE.defaultBlockState(), Block.UPDATE_ALL);
		com.solarquarry.energy.EnergyNetwork.markTopologyChanged();
		SelfTest.check(terminal.findQuarries().size() == 2, "terminal: the link passes through a battery in the middle of the run");
		level.setBlock(Q2.north(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		com.solarquarry.energy.EnergyNetwork.markTopologyChanged();
		SelfTest.check(terminal.findQuarries().size() == 1, "terminal: removing that battery cuts the link again");
		level.setBlock(Q2.north().west(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(Q2, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
	}

	private void setupStorage(ServerLevel level) {
		this.resetLogistics(level);
		Container chestA = (Container) level.getBlockEntity(L_CHEST_A);
		chestA.setItem(0, new ItemStack(Items.STONE, 64));
		chestA.setItem(1, new ItemStack(Items.STONE, 10));
		Container chestB = (Container) level.getBlockEntity(L_CHEST_B);
		for (int i = 0; i < chestB.getContainerSize(); i++) {
			chestB.setItem(i, new ItemStack(Items.DIRT, 64));
		}
	}

	private void verifyStorage(ServerLevel level) {
		var result = com.solarquarry.pipe.StorageScan.scan(level, L);
		SolarQuarry.LOGGER.info("[SelfTest] storage scan: {}", result);
		SelfTest.check(result.inventories() == 2, "storage scan: both chests on the pipe network were found (" + result.inventories() + ")");
		SelfTest.check(result.items() == 74 + 27 * 64 && result.usedSlots() == 29 && result.totalSlots() == 54,
			"storage scan: item and slot totals are exact for chests");
		SelfTest.check(!result.lowSpace() && result.freePercent() == 46, "storage scan: free space is reported (" + result.freePercent() + "% free)");
		this.resetLogistics(level);
	}

	// ---------------------------------------------------------------------------------------------
	// Validation, persistence of the new settings and migration of version 1.0 data
	// ---------------------------------------------------------------------------------------------

	private void verifyConfigAndMigration(ServerLevel level) {
		this.resetSite(level);
		QuarryBlockEntity quarry = quarry(level);
		quarry.setWidth(1000);
		SelfTest.check(quarry.getWidth() == 16, "config: width is clamped to the range limit (" + quarry.getWidth() + ")");
		quarry.setWidth(-5);
		SelfTest.check(quarry.getWidth() == 1, "config: width is clamped to at least 1");
		quarry.setQuarryMode(QuarryMode.TUNNEL_BORE);
		quarry.setLength(100_000);
		SelfTest.check(quarry.getLength() == 64, "config: tunnel length is clamped to 4x the area limit (" + quarry.getLength() + ")");
		quarry.setHeight(99);
		SelfTest.check(quarry.getHeight() == 16, "config: tunnel height is clamped to 16 (" + quarry.getHeight() + ")");
		quarry.setTargetY(-10_000);
		SelfTest.check(quarry.effectiveTargetY() == level.getMinY(), "config: target depth is clamped to the world bottom");
		quarry.setOresOnly(true);
		quarry.getSkipFilter().setItem(4, new ItemStack(Items.GRAVEL));
		quarry.getOreFilter().setItem(2, new ItemStack(Items.GOLD_ORE));

		CompoundTag tag = quarry.saveWithFullMetadata(level.registryAccess());
		BlockEntity loaded = BlockEntity.loadStatic(Q, level.getBlockState(Q), tag, level.registryAccess());
		if (loaded instanceof QuarryBlockEntity copy) {
			copy.setLevel(level);
			SelfTest.check(copy.getQuarryMode() == QuarryMode.TUNNEL_BORE && copy.getLength() == 64 && copy.getHeight() == 16 && copy.isOresOnly(),
				"persistence: mode, size and options survive save/load");
			SelfTest.check(copy.getSkipFilter().getItem(4).is(Items.GRAVEL) && copy.getOreFilter().getItem(2).is(Items.GOLD_ORE),
				"persistence: skip and ore lists survive save/load");
		} else {
			SelfTest.check(false, "persistence: quarry reloads from saved data");
		}

		// Version 1.0 data: square area and a layer/row/col cursor.
		CompoundTag old = new CompoundTag();
		old.putString("id", "solarquarry:quarry");
		old.putInt("Area", 16);
		old.putInt("Layer", 3);
		old.putInt("Row", 5);
		old.putInt("Col", 2);
		old.putInt("Mode", 1);
		old.putLong("Energy", 4321);
		BlockEntity migrated = BlockEntity.loadStatic(Q, level.getBlockState(Q), old, level.registryAccess());
		if (migrated instanceof QuarryBlockEntity copy) {
			SelfTest.check(copy.getWidth() == 16 && copy.getLength() == 16 && copy.getStep() == 3L * 256 && copy.getQuarryMode() == QuarryMode.STRIP_MINE
				&& copy.getEnergy() == 4321 && copy.getMode() == QuarryBlockEntity.Mode.RUNNING,
				"migration: version 1.0 quarry data loads and resumes at its layer");
		} else {
			SelfTest.check(false, "migration: version 1.0 quarry data loads");
		}

		// Garbage values are rejected instead of crashing.
		CompoundTag bad = new CompoundTag();
		bad.putString("id", "solarquarry:quarry");
		bad.putInt("Width", -40);
		bad.putInt("Length", 9_999);
		bad.putLong("Step", -7);
		bad.putString("QuarryMode", "laser_beam");
		bad.putInt("Mode", 77);
		BlockEntity sanitized = BlockEntity.loadStatic(Q, level.getBlockState(Q), bad, level.registryAccess());
		if (sanitized instanceof QuarryBlockEntity copy) {
			SelfTest.check(copy.getWidth() == 1 && copy.getLength() == 16 && copy.getStep() == 0 && copy.getQuarryMode() == QuarryMode.STRIP_MINE
				&& copy.getMode() == QuarryBlockEntity.Mode.IDLE, "validation: invalid saved values are clamped to safe defaults");
		} else {
			SelfTest.check(false, "validation: invalid data still loads");
		}

		quarry.setOresOnly(false);
		quarry.getSkipFilter().clearContent();
		quarry.getOreFilter().clearContent();
	}

	// ---------------------------------------------------------------------------------------------
	// 2.1: protection, prospector, config, chunk loading
	// ---------------------------------------------------------------------------------------------

	private static final java.util.UUID TEST_OWNER = java.util.UUID.fromString("5a1a0000-0000-4000-8000-000000000001");
	private static final java.util.Set<BlockPos> PROTECTED = new java.util.HashSet<>();
	private static final List<String> PROBE_ACTORS = new ArrayList<>();

	/** A stand-in claim mod: refuses breaks at {@link #PROTECTED} positions through Fabric's standard break event. */
	static void registerProtectionProbe() {
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (PROTECTED.contains(pos)) {
				PROBE_ACTORS.add((player instanceof net.fabricmc.fabric.api.entity.FakePlayer ? "fake:" : "real:") + player.getUUID());
				return false;
			}

			return true;
		});
	}

	private void setupProtection(ServerLevel level) {
		fill(level, 99, 200, 101, 101, 200, 103, Blocks.STONE.defaultBlockState());
		PROTECTED.clear();
		PROBE_ACTORS.clear();
		PROTECTED.add(cell(0, 0, 2));
		QuarryBlockEntity quarry = quarry(level);
		quarry.setOwner(TEST_OWNER, "OfflineOwner");
		configure(quarry, QuarryMode.STRIP_MINE, 3, 3, 1, 200);
		quarry.resetProgress();
		quarry.start();
	}

	private void verifyProtection(ServerLevel level) {
		SelfTest.check(level.getBlockState(cell(0, 0, 2)).is(Blocks.STONE), "protection: the claimed block was not broken");
		SelfTest.check(countBlocks(level, 99, 200, 101, 101, 200, 103, Blocks.STONE) == 1, "protection: every unclaimed block was mined");
		SelfTest.check(level.getServer().getPlayerList().getPlayer(TEST_OWNER) == null, "protection: the owner is offline during the test");
		SelfTest.check(!PROBE_ACTORS.isEmpty() && PROBE_ACTORS.stream().allMatch(a -> a.equals("fake:" + TEST_OWNER)),
			"protection: the claim check saw a fake player carrying the owner's id " + PROBE_ACTORS);
		PROTECTED.clear();
	}

	private void setupProspector(ServerLevel level) {
		fill(level, 99, 197, 101, 102, 200, 104, Blocks.STONE.defaultBlockState());
		level.setBlock(new BlockPos(100, 200, 102), Blocks.IRON_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(new BlockPos(101, 198, 104), Blocks.IRON_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(new BlockPos(99, 197, 101), Blocks.DEEPSLATE_IRON_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(new BlockPos(102, 199, 103), Blocks.COAL_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(new BlockPos(102, 197, 104), Blocks.DIAMOND_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
		// Outside the area: must not be counted.
		level.setBlock(new BlockPos(103, 199, 102), Blocks.GOLD_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
		QuarryBlockEntity quarry = quarry(level);
		configure(quarry, QuarryMode.STRIP_MINE, 4, 4, 1, 197);
		quarry.resetProgress();
	}

	private void verifyProspector(ServerLevel level) {
		List<net.minecraft.network.chat.Component> lines = new ArrayList<>();
		long[] result = com.solarquarry.item.ProspectorScans.scanNow(level, quarry(level), lines);
		lines.forEach(line -> SolarQuarry.LOGGER.info("[SelfTest] prospector: {}", line.getString()));
		SelfTest.check(result[0] == 5, "prospector: counted exactly the 5 ores inside the quarry volume (" + result[0] + ")");
		String all = lines.stream().map(net.minecraft.network.chat.Component::getString).reduce("", (a, b) -> a + "|" + b);
		SelfTest.check(all.contains("Iron Ore x2") && all.contains("Deepslate Iron Ore x1") && all.contains("Coal Ore x1") && all.contains("Diamond Ore x1") && !all.contains("more"),
			"prospector: the report lists every ore kind, no '... more' cut-off");
		SelfTest.check(result[1] == 0, "prospector: nothing was in unloaded chunks (" + result[1] + ")");
		SelfTest.check(com.solarquarry.item.ProspectorScans.BLOCKS_PER_TICK * 25L >= 64L * 64 * 384,
			"prospector: a 64x64 quarry from build height to bedrock is scanned in about 25 ticks or less");
	}

	private void verifyConfig(ServerLevel level) {
		var dispatcher = level.getServer().getCommands().getDispatcher();
		var root = dispatcher.getRoot().getChild("solarquarry");
		SelfTest.check(root != null && root.getChild("config") != null && root.getChild("reload") != null, "config: /solarquarry config and reload are registered");
		var original = com.solarquarry.config.SolarQuarryConfig.get();
		var custom = new com.solarquarry.config.SolarQuarryConfig(1.5, 0.5, 1, false, 20, true, 300, 10, 0.5);
		io.netty.buffer.ByteBuf buf = io.netty.buffer.Unpooled.buffer();
		com.solarquarry.network.ConfigSyncPayload.CONFIG_CODEC.encode(buf, custom);
		SelfTest.check(com.solarquarry.network.ConfigSyncPayload.CONFIG_CODEC.decode(buf).equals(custom), "config: settings survive the network round trip");
		level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(), "time set noon");
		BlockPos sky = new BlockPos(95, 250, 95);
		long before = com.solarquarry.quarry.QuarryUpgrades.NONE.energyPerBlock(1.5F);
		com.solarquarry.config.SolarQuarryConfig.setActiveForTesting(custom);
		long after = com.solarquarry.quarry.QuarryUpgrades.NONE.energyPerBlock(1.5F);
		int panelBasic = com.solarquarry.blockentity.SolarPanelBlockEntity.computeGeneration(level, sky, com.solarquarry.energy.SolarTier.ADVANCED);
		com.solarquarry.config.SolarQuarryConfig.setActiveForTesting(original);
		int panelNormal = com.solarquarry.blockentity.SolarPanelBlockEntity.computeGeneration(level, sky, com.solarquarry.energy.SolarTier.ADVANCED);
		SolarQuarry.LOGGER.info("[SelfTest] config: energy per block {} -> {}, advanced panel {} -> {}", before, after, panelNormal, panelBasic);
		SelfTest.check(Math.abs(after - Math.round(before * 1.5)) <= 1, "config: energyCostMultiplier scales the energy per block");
		SelfTest.check(panelNormal > 0 && Math.abs(panelBasic - Math.round(panelNormal * 0.5)) <= 1, "config: solarOutputMultiplier scales panel output");
		SelfTest.check(com.solarquarry.config.SolarQuarryConfig.get().equals(original), "config: the active settings are restored afterwards");
	}

	private static final BlockPos FAR = new BlockPos(1000, 200, 1000);

	private boolean farFinished;
	private long farMined = -1;
	private boolean farLoadingAfterFinish;
	private boolean farReachedFarChunk;

	/** Records what the far quarry does every tick (it may finish and unload between checks). */
	private void watchFarQuarry(ServerLevel level) {
		QuarryBlockEntity quarry = farQuarry(level);
		if (quarry == null) {
			return;
		}

		if (this.farFinished) {
			// Keep watching after completion: chunk loading must switch off on the following tick.
			this.farLoadingAfterFinish = quarry.isChunkLoadingActive();
			return;
		}

		this.farMined = quarry.getBlocksMined();
		MiningPattern.Cell cursor = quarry.cursorCell();
		if (cursor.forward() >= 40 || quarry.isFinished()) {
			this.farReachedFarChunk |= level.isLoaded(new BlockPos(FAR.getX(), FAR.getY(), FAR.getZ() + 40));
		}

		if (quarry.isFinished()) {
			this.farFinished = true;
			this.farLoadingAfterFinish = true;
		}
	}

	/** Looks at the far quarry without loading its chunk (Level#getBlockEntity would add a short loading ticket every call). */
	private static QuarryBlockEntity farQuarry(ServerLevel level) {
		net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunkNow(FAR.getX() >> 4, FAR.getZ() >> 4);
		return chunk != null && chunk.getBlockEntity(FAR) instanceof QuarryBlockEntity q ? q : null;
	}

	private void setupChunkLoader(ServerLevel level) {
		net.minecraft.world.level.ChunkPos chunk = net.minecraft.world.level.ChunkPos.containing(FAR);
		level.setChunkForced(chunk.x(), chunk.z(), true);
		level.getChunk(chunk.x(), chunk.z());
		level.setBlock(FAR, ModBlocks.QUARRY.defaultBlockState().setValue(QuarryBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
		// 3 wide, 1 high, 40 long tunnel south: 21 stone blocks in this chunk, then air in chunks nobody has loaded.
		for (int z = FAR.getZ() + 1; z <= 1007; z++) {
			for (int x = FAR.getX() - 1; x <= FAR.getX() + 1; x++) {
				level.setBlock(new BlockPos(x, FAR.getY(), z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}

		QuarryBlockEntity quarry = farQuarry(level);
		quarry.setItem(QuarryBlockEntity.BUFFER_SLOTS, new ItemStack(com.solarquarry.registry.ModItems.CHUNK_LOADER_UPGRADE));
		quarry.restoreEnergy(60_000L);

		configure(quarry, QuarryMode.TUNNEL_BORE, 3, 40, 1, 199);
		quarry.start();
	}

	private void releaseForcedChunk(ServerLevel level) {
		QuarryBlockEntity quarry = farQuarry(level);
		SelfTest.check(quarry != null && quarry.isChunkLoadingActive(), "chunk loader: active while running with the upgrade and power");
		net.minecraft.world.level.ChunkPos chunk = net.minecraft.world.level.ChunkPos.containing(FAR);
		level.setChunkForced(chunk.x(), chunk.z(), false);
		SelfTest.check(level.getServer().getPlayerList().getPlayerCount() == 0, "chunk loader: no player is online to keep chunks loaded");
	}

	private void verifyChunkLoaderRunning(ServerLevel level) {
		QuarryBlockEntity quarry = farQuarry(level);
		SelfTest.check(quarry != null, "chunk loader: the quarry's chunk is still loaded 120 ticks after the forced chunk was released");
		if (quarry != null) {
			SolarQuarry.LOGGER.info("[SelfTest] chunk loader: status {}, mined {}", quarry.getStatus(), quarry.getBlocksMined());
			SelfTest.check(quarry.getStatus().isWorking() && quarry.getBlocksMined() >= 10, "chunk loader: the quarry kept mining (" + quarry.getBlocksMined() + " blocks)");
			SelfTest.check(com.solarquarry.quarry.QuarryChunkLoading.isRegistered(level, FAR), "chunk loader: the dimension remembers the loading quarry");
		}
	}

	private void verifyChunkLoaderFinished(ServerLevel level) {
		SelfTest.check(this.farFinished, "chunk loader: the 40-block tunnel was finished with no player anywhere near");
		SelfTest.check(this.farReachedFarChunk, "chunk loader: the last chunk of the tunnel (3 chunks away, outside the quarry's own area) was loaded for mining");
		SelfTest.check(this.farMined == 21, "chunk loader: mined the 21 stone blocks (" + this.farMined + ")");
		SelfTest.check(this.farFinished && !this.farLoadingAfterFinish, "chunk loader: switches itself off when the work is complete");
		SelfTest.check(!com.solarquarry.quarry.QuarryChunkLoading.isRegistered(level, FAR), "chunk loader: forgotten by the dimension once it stops");
	}

	private void verifyChunkLoaderUnloaded(ServerLevel level) {
		SelfTest.check(!level.isLoaded(FAR), "chunk loader: its chunks unloaded on their own after it stopped");
		SelfTest.check(!level.isLoaded(new BlockPos(FAR.getX(), FAR.getY(), FAR.getZ() + 40)), "chunk loader: the mined chunks unloaded too");
	}

	private void restoreChunkLoader(ServerLevel level) {
		com.solarquarry.quarry.QuarryChunkLoading.register(level, FAR);
		com.solarquarry.quarry.QuarryChunkLoading.restoreAll(level.getServer());
	}

	private void verifyChunkLoaderRestored(ServerLevel level) {
		SelfTest.check(level.isLoaded(FAR) && farQuarry(level) != null, "chunk loader: a remembered quarry's chunk is loaded again at server start");
	}

	private void setupChunkLoaderNoPower(ServerLevel level) {
		net.minecraft.world.level.ChunkPos chunk = net.minecraft.world.level.ChunkPos.containing(FAR);
		level.setChunkForced(chunk.x(), chunk.z(), true);
		level.getChunk(chunk.x(), chunk.z());
		level.setBlock(FAR, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(FAR, ModBlocks.QUARRY.defaultBlockState().setValue(QuarryBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
		level.setBlock(new BlockPos(FAR.getX(), FAR.getY(), FAR.getZ() + 1), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		QuarryBlockEntity quarry = farQuarry(level);
		quarry.setItem(QuarryBlockEntity.BUFFER_SLOTS, new ItemStack(com.solarquarry.registry.ModItems.CHUNK_LOADER_UPGRADE));
		configure(quarry, QuarryMode.TUNNEL_BORE, 1, 40, 1, 199);
		quarry.start();
	}

	private void verifyChunkLoaderNoPower(ServerLevel level) {
		QuarryBlockEntity quarry = farQuarry(level);
		SelfTest.check(quarry != null && quarry.getStatus() == QuarryStatus.WAITING_FOR_POWER,
			"chunk loader: without power the quarry still has its own chunks loaded and waits for power ("
			+ (quarry == null ? "unloaded" : quarry.getStatus()) + ")");
		SelfTest.check(!level.isLoaded(new BlockPos(FAR.getX(), FAR.getY(), FAR.getZ() + 40)), "chunk loader: without upkeep it does not load the chunks it would mine");
		if (quarry != null) {
			quarry.pause();
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Phase 1: frame, mining rate, upgrade limits, creative tab
	// ---------------------------------------------------------------------------------------------

	private void setupFrame(ServerLevel level) {
		fill(level, 98, 199, 101, 102, 200, 105, Blocks.STONE.defaultBlockState());
		QuarryBlockEntity quarry = quarry(level);
		configure(quarry, QuarryMode.STRIP_MINE, 5, 5, 1, 199);
		quarry.resetProgress();
		quarry.start();
	}

	private void verifyFrame(ServerLevel level) {
		QuarryBlockEntity quarry = quarry(level);
		// The work area runs south and lateral is west, so the first post stands at x = 103, z = 101.
		net.minecraft.world.phys.AABB atPost = new net.minecraft.world.phys.AABB(102.9, 201.0, 100.9, 103.1, 202.0, 101.1);
		SelfTest.check(!com.solarquarry.quarry.QuarryFrames.boxesIntersecting(level, atPost).isEmpty(), "frame: the corner post is registered as solid");
		SelfTest.check(com.solarquarry.quarry.QuarryFrames.boxesIntersecting(level, new net.minecraft.world.phys.AABB(100.2, 201.0, 102.2, 100.8, 202.0, 102.8)).isEmpty(),
			"frame: the open work area has no collision");

		net.minecraft.world.entity.decoration.ArmorStand stand = net.minecraft.world.entity.EntityTypes.ARMOR_STAND.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		stand.setNoGravity(true);
		stand.snapTo(104.5, 201.2, 101.0, 0.0F, 0.0F);
		level.addFreshEntity(stand);
		stand.move(net.minecraft.world.entity.MoverType.SELF, new net.minecraft.world.phys.Vec3(-2.0, 0.0, 0.0));
		double stoppedAt = stand.getX();
		stand.discard();
		SolarQuarry.LOGGER.info("[SelfTest] frame: entity moving into the post stopped at x={}", stoppedAt);
		SelfTest.check(stoppedAt > 103.5 && stoppedAt < 103.61, "frame: a moving entity is stopped by the post (stopped at x=" + String.format(java.util.Locale.ROOT, "%.3f", stoppedAt) + ")");

		double rate = quarry.measuredBlocksPerSecond();
		double max = quarry.getUpgrades().blocksPerMinute() / 60.0;
		SolarQuarry.LOGGER.info("[SelfTest] mining rate: {} blocks/s measured, {} max", rate, max);
		SelfTest.check(rate > 0.5 && rate <= max + 0.001, "GUI: measured blocks per second is real and within the limit (" + rate + " of " + max + ")");

		level.setBlock(Q, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		SelfTest.check(com.solarquarry.quarry.QuarryFrames.boxesIntersecting(level, atPost).isEmpty(), "frame: collision disappears with the quarry");
	}

	private void verifyUpgradeLimits(ServerLevel level) {
		QuarryBlockEntity quarry = quarry(level);
		int first = QuarryBlockEntity.BUFFER_SLOTS;
		var slot = new com.solarquarry.menu.UpgradeSlot(quarry, first, 0, 0);
		SelfTest.check(slot.getMaxStackSize(new ItemStack(com.solarquarry.registry.ModItems.ENERGY_EFFICIENCY_UPGRADE, 64)) == 4, "upgrades: efficiency stacks to 4");
		SelfTest.check(slot.getMaxStackSize(new ItemStack(com.solarquarry.registry.ModItems.SPEED_UPGRADE, 64)) == 4, "upgrades: speed stacks to 4");
		SelfTest.check(slot.getMaxStackSize(new ItemStack(com.solarquarry.registry.ModItems.VOID_UPGRADE, 64)) == 1, "upgrades: void is limited to 1");
		quarry.setItem(first, new ItemStack(com.solarquarry.registry.ModItems.SPEED_UPGRADE, 2));
		var second = new com.solarquarry.menu.UpgradeSlot(quarry, first + 1, 0, 0);
		SelfTest.check(!second.mayPlace(new ItemStack(com.solarquarry.registry.ModItems.SPEED_UPGRADE)), "upgrades: a second slot of the same type is refused");
		SelfTest.check(!second.mayPlace(new ItemStack(Items.DIAMOND)), "upgrades: non-upgrade items are refused");
		long inserted;
		try (var tx = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
			var storage = net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(level, Q, Direction.UP);
			inserted = storage == null ? 0 : storage.insert(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(com.solarquarry.registry.ModItems.FORTUNE_UPGRADE), 1, tx);
		}
		SelfTest.check(inserted == 0, "upgrades: automation (hoppers, pipes) cannot insert upgrades or anything else");
		quarry.setItem(first, ItemStack.EMPTY);

		List<net.minecraft.world.item.Item> tab = com.solarquarry.registry.ModCreativeTab.orderedItems();
		java.util.Set<net.minecraft.world.item.Item> unique = new java.util.HashSet<>(tab);
		int registered = com.solarquarry.registry.ModBlocks.BLOCK_ITEMS.size() + com.solarquarry.registry.ModItems.ITEMS.size();
		SelfTest.check(unique.size() == tab.size() && tab.size() == registered, "creative tab: every item exactly once (" + tab.size() + " of " + registered + ")");
		SelfTest.check(tab.get(0) == ModBlocks.QUARRY.asItem(), "creative tab: grouped, starting with the quarry");
	}

	// ---------------------------------------------------------------------------------------------
	// Phase 2: pipe tiers and the Sorting Terminal (site at x 138..150, z 118..128)
	// ---------------------------------------------------------------------------------------------

	private static final BlockPos S_TERMINAL = new BlockPos(140, 200, 124);
	private static final BlockPos S_START = new BlockPos(140, 200, 125);
	private static final BlockPos S_CHEST_A = new BlockPos(141, 200, 126);
	private static final BlockPos S_CHEST_B = new BlockPos(143, 200, 126);
	private static final BlockPos S_CHEST_C = new BlockPos(145, 200, 126);
	private static final BlockPos S_CHEST_D = new BlockPos(147, 200, 125);

	private void resetPhase2Site(ServerLevel level) {
		for (int x = 138; x <= 150; x++) {
			for (int z = 118; z <= 128; z++) {
				for (int y = 199; y <= 202; y++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (!level.getBlockState(pos).isAir()) {
						if (level.getBlockEntity(pos) instanceof Container container) {
							container.clearContent();
						}

						level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
		}
	}

	private void setupPipeTiers(ServerLevel level) {
		this.resetPhase2Site(level);
		for (int x = 140; x <= 147; x++) {
			level.setBlock(new BlockPos(x, 200, 120), ModBlocks.ITEM_PIPE.defaultBlockState(), Block.UPDATE_ALL);
			level.setBlock(new BlockPos(x, 200, 122), ModBlocks.ADVANCED_PIPE.defaultBlockState(), Block.UPDATE_ALL);
		}

		level.setBlock(new BlockPos(148, 200, 120), Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(new BlockPos(148, 200, 122), Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		com.solarquarry.pipe.PipeRouter.invalidateCache();
		boolean basic = ((com.solarquarry.blockentity.ItemPipeBlockEntity) level.getBlockEntity(new BlockPos(140, 200, 120))).acceptFromSource(new ItemStack(Items.COBBLESTONE, 16), Direction.WEST);
		boolean advanced = ((com.solarquarry.blockentity.ItemPipeBlockEntity) level.getBlockEntity(new BlockPos(140, 200, 122))).acceptFromSource(new ItemStack(Items.COBBLESTONE, 16), Direction.WEST);
		SelfTest.check(basic && advanced, "pipe tiers: both lines accept a stack");
	}

	private void verifyPipeTiersEarly(ServerLevel level) {
		int basic = countItem(level, new BlockPos(148, 200, 120), Items.COBBLESTONE);
		int advanced = countItem(level, new BlockPos(148, 200, 122), Items.COBBLESTONE);
		SelfTest.check(advanced == 16 && basic == 0, "pipe tiers: after 30 ticks the advanced line delivered (" + advanced + ") and the basic line not yet (" + basic + ")");
		var pipe = (com.solarquarry.blockentity.ItemPipeBlockEntity) level.getBlockEntity(new BlockPos(141, 200, 122));
		SelfTest.check(pipe.tier() == com.solarquarry.pipe.PipeTier.ADVANCED && pipe.tier().batch() == 64 && pipe.tier().capacity() == 16, "pipe tiers: advanced pipe batch 64, capacity 16");
	}

	private void verifyPipeTiersLate(ServerLevel level) {
		int basic = countItem(level, new BlockPos(148, 200, 120), Items.COBBLESTONE);
		SelfTest.check(basic == 16, "pipe tiers: the basic line delivered all 16 later (" + basic + ")");
	}

	private com.solarquarry.blockentity.SortingTerminalBlockEntity sortingTerminal(ServerLevel level) {
		return (com.solarquarry.blockentity.SortingTerminalBlockEntity) level.getBlockEntity(S_TERMINAL);
	}

	private void setupSorting(ServerLevel level) {
		this.resetPhase2Site(level);
		for (int x = 140; x <= 146; x++) {
			level.setBlock(new BlockPos(x, 200, 125), ModBlocks.REINFORCED_PIPE.defaultBlockState(), Block.UPDATE_ALL);
		}

		for (BlockPos chest : List.of(S_CHEST_A, S_CHEST_B, S_CHEST_C, S_CHEST_D)) {
			level.setBlock(chest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		}

		level.setBlock(S_TERMINAL, ModBlocks.SORTING_TERMINAL.defaultBlockState(), Block.UPDATE_ALL);
		var terminal = this.sortingTerminal(level);
		terminal.register(S_CHEST_A);
		terminal.getRule(S_CHEST_A).setItem(0, "minecraft:raw_iron");
		terminal.register(S_CHEST_B);
		terminal.getRule(S_CHEST_B).setTag(0, "minecraft:logs");
		terminal.register(S_CHEST_C);
		terminal.getRule(S_CHEST_C).setMode(com.solarquarry.pipe.SortRule.Mode.FALLBACK);
		terminal.changed();
	}

	/** Follows a route from the start pipe and returns the inventory it ends in (or null). */
	private static BlockPos routeTarget(ServerLevel level, ItemStack stack) {
		List<Direction> route = com.solarquarry.pipe.PipeRouter.findRoute(level, S_START, stack, false);
		if (route == null) {
			return null;
		}

		BlockPos pos = S_START;
		for (Direction dir : route) {
			pos = pos.relative(dir);
		}

		return pos;
	}

	private void verifySortingRoutes(ServerLevel level) {
		var terminal = this.sortingTerminal(level);
		SelfTest.check(terminal.discover().containsAll(List.of(S_CHEST_A, S_CHEST_B, S_CHEST_C, S_CHEST_D)), "sorting: the terminal discovers all 4 chests on its network");
		SelfTest.check(S_CHEST_A.equals(routeTarget(level, new ItemStack(Items.RAW_IRON, 8))), "sorting: raw iron goes to its listed chest (A)");
		SelfTest.check(S_CHEST_B.equals(routeTarget(level, new ItemStack(Items.OAK_LOG, 8))), "sorting: logs go to the chest with the #minecraft:logs tag (B)");
		SelfTest.check(S_CHEST_C.equals(routeTarget(level, new ItemStack(Items.COBBLESTONE, 8))), "sorting: unmatched items go to the fallback chest (C)");
		SelfTest.check(!S_CHEST_D.equals(routeTarget(level, new ItemStack(Items.DIRT, 8))), "sorting: an unregistered chest (D) receives nothing");

		// Overflow: when A is full, raw iron overflows to the fallback.
		Container a = (Container) level.getBlockEntity(S_CHEST_A);
		for (int i = 0; i < a.getContainerSize(); i++) {
			a.setItem(i, new ItemStack(Items.STONE, 64));
		}

		SelfTest.check(S_CHEST_C.equals(routeTarget(level, new ItemStack(Items.RAW_IRON, 8))), "sorting: a full destination overflows to the fallback");
		a.clearContent();

		// Without a fallback, unmatched items have no route and stay in the source.
		terminal.getRule(S_CHEST_C).setMode(com.solarquarry.pipe.SortRule.Mode.ALLOW);
		terminal.changed();
		SelfTest.check(routeTarget(level, new ItemStack(Items.COBBLESTONE, 8)) == null, "sorting: without a fallback, unmatched items stay in the machine");

		// Everything-except destinations rank after listed ones and before fallbacks.
		terminal.getRule(S_CHEST_C).setMode(com.solarquarry.pipe.SortRule.Mode.BLOCK);
		terminal.getRule(S_CHEST_C).setItem(0, "minecraft:cobblestone");
		terminal.changed();
		SelfTest.check(routeTarget(level, new ItemStack(Items.COBBLESTONE, 8)) == null && S_CHEST_C.equals(routeTarget(level, new ItemStack(Items.DIRT, 8))),
			"sorting: an 'everything except' chest refuses its listed items and takes the rest");
		SelfTest.check(S_CHEST_A.equals(routeTarget(level, new ItemStack(Items.RAW_IRON, 8))), "sorting: listed destinations still win over 'everything except' ones");

		// Priority among listed destinations.
		terminal.getRule(S_CHEST_B).setItem(1, "minecraft:raw_iron");
		terminal.getRule(S_CHEST_B).setPriority(3);
		terminal.changed();
		SelfTest.check(S_CHEST_B.equals(routeTarget(level, new ItemStack(Items.RAW_IRON, 8))), "sorting: higher priority wins between two listed destinations");
		terminal.getRule(S_CHEST_B).setItem(1, "");
		terminal.getRule(S_CHEST_B).setPriority(0);
		terminal.getRule(S_CHEST_C).setMode(com.solarquarry.pipe.SortRule.Mode.FALLBACK);
		terminal.getRule(S_CHEST_C).setItem(0, "");
		terminal.changed();
	}

	private void startSortingDelivery(ServerLevel level) {
		var first = (com.solarquarry.blockentity.ItemPipeBlockEntity) level.getBlockEntity(new BlockPos(146, 200, 125));
		boolean iron = first.acceptFromSource(new ItemStack(Items.RAW_IRON, 20), Direction.EAST);
		boolean logs = first.acceptFromSource(new ItemStack(Items.OAK_LOG, 10), Direction.EAST);
		boolean cobble = first.acceptFromSource(new ItemStack(Items.COBBLESTONE, 30), Direction.EAST);
		SelfTest.check(iron && logs && cobble, "sorting: the network accepts three kinds of items");
	}

	private void verifySortingDelivery(ServerLevel level) {
		SelfTest.check(countItem(level, S_CHEST_A, Items.RAW_IRON) == 20 && countItem(level, S_CHEST_B, Items.OAK_LOG) == 10
			&& countItem(level, S_CHEST_C, Items.COBBLESTONE) == 30 && countItem(level, S_CHEST_D, null) == 0,
			"sorting: every item reached its chest (A " + countItem(level, S_CHEST_A, null) + ", B " + countItem(level, S_CHEST_B, null)
				+ ", C " + countItem(level, S_CHEST_C, null) + ", D " + countItem(level, S_CHEST_D, null) + ")");
	}

	private void verifySortingMenuAndRecovery(ServerLevel level) {
		var terminal = this.sortingTerminal(level);
		var fake = net.fabricmc.fabric.api.entity.FakePlayer.get(level);
		fake.snapTo(S_TERMINAL.getX() + 0.5, S_TERMINAL.getY() + 1, S_TERMINAL.getZ() + 0.5);
		var menu = new com.solarquarry.menu.SortingTerminalMenu(0, fake.getInventory(), terminal);
		boolean registered = com.solarquarry.network.SortingActionPayload.apply(terminal, menu, com.solarquarry.network.SortingActionPayload.REGISTER, S_CHEST_D, 0, "");
		SelfTest.check(registered && terminal.getRule(S_CHEST_D) != null, "sorting menu: registering a discovered chest works");
		boolean far = com.solarquarry.network.SortingActionPayload.apply(terminal, menu, com.solarquarry.network.SortingActionPayload.REGISTER, new BlockPos(0, 64, 0), 0, "");
		SelfTest.check(!far, "sorting menu: positions that are not on the network are refused");
		com.solarquarry.network.SortingActionPayload.apply(terminal, menu, com.solarquarry.network.SortingActionPayload.SELECT, S_CHEST_D, 0, "");
		menu.slots.get(0).set(new ItemStack(Items.DIAMOND));
		menu.slots.get(0).setChanged();
		SelfTest.check("minecraft:diamond".equals(terminal.getRule(S_CHEST_D).items().get(0)), "sorting menu: template slots write the rule");
		boolean badTag = com.solarquarry.network.SortingActionPayload.apply(terminal, menu, com.solarquarry.network.SortingActionPayload.TAG, S_CHEST_D, 0, "Not A Tag!");
		boolean goodTag = com.solarquarry.network.SortingActionPayload.apply(terminal, menu, com.solarquarry.network.SortingActionPayload.TAG, S_CHEST_D, 1, "#c:gems");
		SelfTest.check(!badTag && goodTag && "c:gems".equals(terminal.getRule(S_CHEST_D).tags().get(1)), "sorting menu: invalid tags are refused, valid ones stored");
		for (int i = 0; i < 20; i++) {
			com.solarquarry.network.SortingActionPayload.apply(terminal, menu, com.solarquarry.network.SortingActionPayload.PRIORITY, S_CHEST_D, 1, "");
		}

		SelfTest.check(terminal.getRule(S_CHEST_D).priority() == com.solarquarry.pipe.SortRule.MAX_PRIORITY, "sorting menu: priority is clamped to +5");

		// Save and load.
		net.minecraft.nbt.CompoundTag saved = terminal.saveWithFullMetadata(level.registryAccess());
		var copy = new com.solarquarry.blockentity.SortingTerminalBlockEntity(S_TERMINAL, terminal.getBlockState());
		try (var reporter = new net.minecraft.util.ProblemReporter.ScopedCollector(SolarQuarry.LOGGER)) {
			copy.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(reporter, level.registryAccess(), saved));
		}

		SelfTest.check(copy.getRules().size() == 4 && "c:gems".equals(copy.getRule(S_CHEST_D).tags().get(1)) && copy.getRule(S_CHEST_C).mode() == com.solarquarry.pipe.SortRule.Mode.FALLBACK,
			"sorting: destinations and rules survive save/load");

		// A broken destination keeps its rule; items fall back meanwhile; a new chest in the same place is used again.
		level.setBlock(S_CHEST_A, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		SelfTest.check(terminal.getRule(S_CHEST_A) != null && S_CHEST_C.equals(routeTarget(level, new ItemStack(Items.RAW_IRON, 8))),
			"sorting: a broken chest keeps its rule and its items fall back");
		level.setBlock(S_CHEST_A, Blocks.BARREL.defaultBlockState(), Block.UPDATE_ALL);
		SelfTest.check(S_CHEST_A.equals(routeTarget(level, new ItemStack(Items.RAW_IRON, 8))), "sorting: a replacement inventory at the same spot is used again");

		// Wrench pickup keeps the rules.
		ItemStack picked = com.solarquarry.item.WrenchItem.dismantle(level, S_TERMINAL);
		SelfTest.check(level.getBlockState(S_TERMINAL).isAir() && picked.has(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA),
			"sorting: the wrench picks the terminal up with its rules");
		this.resetPhase2Site(level);
	}

	// ---------------------------------------------------------------------------------------------
	// Checkpoint B2: torches, experience, legs
	// ---------------------------------------------------------------------------------------------

	private void setupTorches(ServerLevel level) {
		// A 5x5 stone column with the 3x3 work area in the middle (x 99..101, z 101..103), 8 layers deep.
		fill(level, 98, 192, 100, 102, 199, 104, Blocks.STONE.defaultBlockState());
		level.setBlock(new BlockPos(100, 198, 102), Blocks.COAL_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(new BlockPos(99, 196, 101), Blocks.DIAMOND_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(new BlockPos(101, 194, 103), Blocks.LAPIS_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
		QuarryBlockEntity quarry = quarry(level);
		quarry.setItem(QuarryBlockEntity.BUFFER_SLOTS, new ItemStack(com.solarquarry.registry.ModItems.SPEED_UPGRADE, 4));
		quarry.setItem(QuarryBlockEntity.TORCH_SLOT, new ItemStack(Items.TORCH, 64));
		configure(quarry, QuarryMode.STRIP_MINE, 3, 3, 1, 192);
		quarry.resetProgress();
		quarry.start();
	}

	private void verifyTorches(ServerLevel level) {
		QuarryBlockEntity quarry = quarry(level);
		int torches = countBlocks(level, 98, 192, 100, 102, 200, 104, Blocks.WALL_TORCH);
		int left = quarry.getItem(QuarryBlockEntity.TORCH_SLOT).getCount();
		SelfTest.check(torches == 8 && left == 56, "torches: 4 wall torches every 4 layers (" + torches + " placed, " + left + " left in the slot)");
		SelfTest.check(level.getBlockState(new BlockPos(101, 197, 102)).is(Blocks.WALL_TORCH) && level.getBlockState(new BlockPos(102, 197, 102)).is(Blocks.STONE),
			"torches: placed in the dug cell against the wall outside the area");
		SelfTest.check(countBlocks(level, 99, 192, 101, 101, 199, 103, Blocks.STONE) == 0, "torches: the work area itself was still mined completely");
		int xp = quarry.getStoredXp();
		SolarQuarry.LOGGER.info("[SelfTest] stored experience after mining coal, diamond and lapis ore: {}", xp);
		SelfTest.check(xp >= 3, "experience: ore experience was captured instead of dropped (" + xp + ")");
		SelfTest.check(level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, new net.minecraft.world.phys.AABB(98, 190, 99, 103, 201, 105)).isEmpty(),
			"experience: no orbs were left lying in the pit");
		var fake = net.fabricmc.fabric.api.entity.FakePlayer.get(level);
		int before = fake.totalExperience;
		int given = quarry.collectXp(fake);
		SelfTest.check(given == xp && quarry.getStoredXp() == 0 && fake.totalExperience - before == xp, "experience: collecting gives it all to the player and empties the store");
		quarry.setItem(QuarryBlockEntity.BUFFER_SLOTS, ItemStack.EMPTY);
		quarry.setItem(QuarryBlockEntity.TORCH_SLOT, ItemStack.EMPTY);
	}

	private void setupLegs(ServerLevel level) {
		// The quarry floats at y 200; ground for one leg at y 195, for the machine at y 197.
		level.setBlock(new BlockPos(103, 195, 100), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(new BlockPos(100, 197, 100), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
		QuarryBlockEntity quarry = quarry(level);
		configure(quarry, QuarryMode.STRIP_MINE, 5, 5, 1, 199);
		quarry.resetProgress();
		quarry.start();
	}

	private void verifyLegs(ServerLevel level) {
		boolean legDown = !com.solarquarry.quarry.QuarryFrames.boxesIntersecting(level, new net.minecraft.world.phys.AABB(103.05, 196.1, 100.75, 103.25, 196.4, 100.95)).isEmpty();
		boolean notBelow = com.solarquarry.quarry.QuarryFrames.boxesIntersecting(level, new net.minecraft.world.phys.AABB(103.05, 195.1, 100.75, 103.25, 195.9, 100.95)).isEmpty();
		SelfTest.check(legDown && notBelow, "legs: the leg reaches down to the block under it and stops there");
		boolean support = !com.solarquarry.quarry.QuarryFrames.boxesIntersecting(level, new net.minecraft.world.phys.AABB(100.4, 198.2, 100.4, 100.6, 199.8, 100.6)).isEmpty();
		SelfTest.check(support, "legs: a floating machine gets a support column down to the ground");
		float[] legs = quarry(level).getLegBottoms();
		SelfTest.check(legs.length == 5 && legs[0] == -4.0F && legs[4] == -2.0F, "legs: leg and support lengths match the ground (" + java.util.Arrays.toString(legs) + ")");
		quarry(level).pause();
	}

	// ---------------------------------------------------------------------------------------------
	// Phase 2: liquid remover, fluid pipe, liquid tank, storage crates
	// ---------------------------------------------------------------------------------------------

	private static final BlockPos TANK = new BlockPos(100, 200, 97);

	private void waterBasin(ServerLevel level) {
		// Stone basin around the 3x3 work area (x 99..101, z 101..103); the top two layers are water.
		fill(level, 98, 195, 100, 102, 199, 104, Blocks.STONE.defaultBlockState());
		fill(level, 99, 198, 101, 101, 199, 103, Blocks.WATER.defaultBlockState());
	}

	private void setupLiquid(ServerLevel level) {
		this.waterBasin(level);
		level.setBlock(new BlockPos(100, 200, 99), com.solarquarry.registry.ModBlocks.FLUID_PIPE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(new BlockPos(100, 200, 98), com.solarquarry.registry.ModBlocks.FLUID_PIPE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(TANK, com.solarquarry.registry.ModBlocks.LIQUID_TANK.defaultBlockState(), Block.UPDATE_ALL);
		QuarryBlockEntity quarry = quarry(level);
		quarry.setItem(QuarryBlockEntity.BUFFER_SLOTS, new ItemStack(com.solarquarry.registry.ModItems.SPEED_UPGRADE, 4));
		quarry.setItem(QuarryBlockEntity.BUFFER_SLOTS + 1, new ItemStack(com.solarquarry.registry.ModItems.LIQUID_REMOVER_UPGRADE));
		configure(quarry, QuarryMode.STRIP_MINE, 3, 3, 1, 196);
		quarry.resetProgress();
		quarry.start();
	}

	private void verifyLiquid(ServerLevel level) {
		QuarryBlockEntity quarry = quarry(level);
		int water = countBlocks(level, 99, 196, 101, 101, 199, 103, Blocks.WATER);
		SelfTest.check(quarry.isFinished() && water == 0, "liquid: the flooded area was drained and mined (" + water + " water left)");
		var tank = level.getBlockEntity(TANK) instanceof com.solarquarry.fluid.LiquidTankBlockEntity t ? t : null;
		long bucket = net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BUCKET;
		long inTank = tank != null ? tank.amount() : 0;
		long total = inTank + quarry.getPumpAmount();
		SolarQuarry.LOGGER.info("[SelfTest] liquid: pumped {} buckets, {} in the tank, {} still in the quarry", quarry.getFluidPumped() / bucket, inTank / bucket, quarry.getPumpAmount() / bucket);
		SelfTest.check(quarry.getFluidPumped() >= 18 * bucket, "liquid: every water source was collected (" + quarry.getFluidPumped() / bucket + " buckets)");
		SelfTest.check(total == quarry.getFluidPumped(), "liquid: nothing was lost or duplicated between the pump and the tank");
		SelfTest.check(countBlocks(level, 99, 196, 101, 101, 199, 103, Blocks.STONE) == 0, "liquid: the stone under the water was still mined");
		SelfTest.check(tank != null && tank.fluid().getFluid() == net.minecraft.world.level.material.Fluids.WATER && quarry.getPumpAmount() == 0
			&& inTank == quarry.getFluidPumped(), "liquid: all of it went through the fluid pipe into the tank (" + inTank / bucket + " buckets)");

		// Buckets empty the tank, one bucket at a time.
		var fake = net.fabricmc.fabric.api.entity.FakePlayer.get(level);
		fake.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
		long before = tank != null ? tank.amount() : 0;
		boolean used = tank != null && net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorageUtil.interactWithFluidStorage(tank.storage, fake, net.minecraft.world.InteractionHand.MAIN_HAND);
		boolean filled = fake.getMainHandItem().is(Items.WATER_BUCKET) || fake.getInventory().contains(new ItemStack(Items.WATER_BUCKET));
		SelfTest.check(used && filled && tank.amount() == before - bucket, "liquid: an empty bucket takes one bucket of water out of the tank");
		fake.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
	}

	private void setupLiquidVoid(ServerLevel level) {
		this.setupLiquid(level);
		QuarryBlockEntity quarry = quarry(level);
		quarry.setItem(QuarryBlockEntity.BUFFER_SLOTS + 2, new ItemStack(com.solarquarry.registry.ModItems.VOID_UPGRADE));
		configure(quarry, QuarryMode.STRIP_MINE, 3, 3, 1, 196);
		quarry.resetProgress();
		quarry.start();
	}

	private void verifyLiquidVoid(ServerLevel level) {
		QuarryBlockEntity quarry = quarry(level);
		int water = countBlocks(level, 99, 196, 101, 101, 199, 103, Blocks.WATER);
		long inTank = level.getBlockEntity(TANK) instanceof com.solarquarry.fluid.LiquidTankBlockEntity t ? t.amount() : -1;
		SelfTest.check(quarry.isFinished() && water == 0, "liquid + void: the flooded area was drained (" + water + " water left)");
		SelfTest.check(inTank == 0 && quarry.getPumpAmount() == 0 && quarry.getFluidPumped() == 0, "liquid + void: the liquid was deleted, nothing reached the tank");
		for (int i = 0; i < 3; i++) {
			quarry.setItem(QuarryBlockEntity.BUFFER_SLOTS + i, ItemStack.EMPTY);
		}

		quarry.pause();
		level.removeBlock(TANK, false);
		level.removeBlock(new BlockPos(100, 200, 98), false);
		level.removeBlock(new BlockPos(100, 200, 99), false);
	}

	private void verifyCrates(ServerLevel level) {
		BlockPos pos = new BlockPos(110, 200, 110);
		level.setBlock(pos, com.solarquarry.registry.ModBlocks.CRATE_ADVANCED.defaultBlockState(), Block.UPDATE_ALL);
		var crate = level.getBlockEntity(pos) instanceof com.solarquarry.storage.CrateBlockEntity c ? c : null;
		SelfTest.check(crate != null && crate.getContainerSize() == 162, "crates: the advanced crate has 162 slots");
		if (crate == null) {
			return;
		}

		var fake = net.fabricmc.fabric.api.entity.FakePlayer.get(level);
		var menu = new com.solarquarry.storage.CrateMenu(1, fake.getInventory(), crate, crate.tier());
		menu.clickMenuButton(fake, com.solarquarry.storage.CrateMenu.BUTTON_NEXT);
		menu.clickMenuButton(fake, com.solarquarry.storage.CrateMenu.BUTTON_NEXT);
		menu.slots.get(0).set(new ItemStack(Items.DIAMOND, 5));
		SelfTest.check(menu.page() == 2 && crate.getItem(108).is(Items.DIAMOND) && crate.getItem(0).isEmpty(), "crates: page buttons show the third page of the crate");
		var hopper = net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(level, pos, null);
		long inserted;
		try (var tx = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
			inserted = hopper == null ? 0 : hopper.insert(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(Items.COBBLESTONE), 64 * 160, tx);
			tx.commit();
		}

		SelfTest.check(inserted == 64L * 160, "crates: automation can fill every slot (" + inserted + " items)");
		menu.removed(fake);
		crate.clearContent();
		level.removeBlock(pos, false);
	}

	// ---------------------------------------------------------------------------------------------
	// Phase 3: Super Smelter, Auto-Smelter Upgrade
	// ---------------------------------------------------------------------------------------------

	private static final BlockPos SMELTER = new BlockPos(120, 200, 120);
	private long smelterEnergyStart;

	private com.solarquarry.smelting.SuperSmelterBlockEntity smelter(ServerLevel level) {
		return level.getBlockEntity(SMELTER) instanceof com.solarquarry.smelting.SuperSmelterBlockEntity s ? s : null;
	}

	private void setupSmelter(ServerLevel level) {
		level.setBlock(SMELTER, com.solarquarry.registry.ModBlocks.SUPER_SMELTER.defaultBlockState(), Block.UPDATE_ALL);
		var smelter = this.smelter(level);
		smelter.restoreEnergy(com.solarquarry.smelting.SuperSmelterBlockEntity.CAPACITY);
		smelter.setItem(0, new ItemStack(Items.RAW_IRON, 8));
		smelter.setItem(1, new ItemStack(Items.COBBLESTONE, 8));
		this.smelterEnergyStart = smelter.getEnergy();
	}

	private void verifySmelterRun(ServerLevel level) {
		var smelter = this.smelter(level);
		int cook = com.solarquarry.smelting.SuperSmelterBlockEntity.COOK_TICKS;
		ItemStack ingots = smelter.getItem(3);
		ItemStack stone = smelter.getItem(4);
		SelfTest.check(ingots.is(Items.IRON_INGOT) && stone.is(Items.STONE) && ingots.getCount() == 2 && stone.getCount() == 2,
			"smelter: furnace recipes, two lanes in parallel, one item per " + cook + " ticks (" + ingots + ", " + stone + ")");
		SelfTest.check(smelter.getItem(0).getCount() == 6 && smelter.getItem(1).getCount() == 6, "smelter: one input used per result");
		long worked = (long) ingots.getCount() * cook + smelter.getProgress(0) + (long) stone.getCount() * cook + smelter.getProgress(1);
		long used = this.smelterEnergyStart - smelter.getEnergy();
		SelfTest.check(used == worked * com.solarquarry.smelting.SuperSmelterBlockEntity.LANE_POWER,
			"smelter: energy matches the work exactly (" + used + " E for " + worked + " lane-ticks), the idle lane draws nothing");
		SelfTest.check(smelter.getStatus() == com.solarquarry.smelting.SuperSmelterBlockEntity.Status.RUNNING
			&& level.getBlockState(SMELTER).getValue(com.solarquarry.smelting.SuperSmelterBlock.LIT), "smelter: status Smelting, front lit");
		SelfTest.check(!smelter.canPlaceItem(0, new ItemStack(Items.DIRT)) && smelter.canPlaceItem(2, new ItemStack(Items.RAW_GOLD))
			&& !smelter.canPlaceItem(3, new ItemStack(Items.RAW_GOLD)), "smelter: inputs accept only smeltable items, results cannot be inserted");
		SelfTest.check(smelter.canPlaceItemThroughFace(2, new ItemStack(Items.RAW_GOLD), net.minecraft.core.Direction.UP)
			&& !smelter.canPlaceItemThroughFace(2, new ItemStack(Items.RAW_GOLD), net.minecraft.core.Direction.DOWN)
			&& smelter.canTakeItemThroughFace(3, ingots, net.minecraft.core.Direction.DOWN)
			&& !smelter.canTakeItemThroughFace(0, smelter.getItem(0), net.minecraft.core.Direction.DOWN)
			&& !smelter.canTakeItemThroughFace(3, ingots, net.minecraft.core.Direction.NORTH), "smelter: sided automation (inputs from top/sides, results from the bottom)");
		var storage = net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(level, SMELTER, net.minecraft.core.Direction.UP);
		long inserted;
		try (var tx = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
			inserted = storage == null ? 0 : storage.insert(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(Items.DIRT), 4, tx);
		}

		SelfTest.check(inserted == 0, "smelter: a pipe or hopper cannot push unsmeltable items in");

		CompoundTag tag = smelter.saveWithFullMetadata(level.registryAccess());
		BlockEntity copy = BlockEntity.loadStatic(SMELTER, level.getBlockState(SMELTER), tag, level.registryAccess());
		SelfTest.check(copy instanceof com.solarquarry.smelting.SuperSmelterBlockEntity c && c.getEnergy() == smelter.getEnergy()
			&& c.getProgress(0) == smelter.getProgress(0) && ItemStack.matches(c.getItem(3), ingots) && c.getItem(0).getCount() == 6,
			"smelter: energy, progress and items survive save/load");
	}

	private void starveSmelter(ServerLevel level) {
		var smelter = this.smelter(level);
		smelter.extractForPickup(level.registryAccess());
		smelter.setItem(0, new ItemStack(Items.RAW_IRON, 8));
		smelter.restoreEnergy(com.solarquarry.smelting.SuperSmelterBlockEntity.LANE_POWER * 10);
	}

	private void verifySmelterStarved(ServerLevel level) {
		var smelter = this.smelter(level);
		SelfTest.check(smelter.getStatus() == com.solarquarry.smelting.SuperSmelterBlockEntity.Status.NO_POWER && smelter.getEnergy() == 0
			&& smelter.getProgress(0) == 10 && smelter.getItem(3).isEmpty(), "smelter: stops at no power and keeps its progress (" + smelter.getProgress(0) + ")");
	}

	private void fillSmelterOutput(ServerLevel level) {
		var smelter = this.smelter(level);
		smelter.setItem(3, new ItemStack(Items.IRON_INGOT, 64));
		smelter.restoreEnergy(10_000);
		this.smelterEnergyStart = smelter.getEnergy();
	}

	private void verifySmelterFull(ServerLevel level) {
		var smelter = this.smelter(level);
		SelfTest.check(smelter.getStatus() == com.solarquarry.smelting.SuperSmelterBlockEntity.Status.OUTPUT_FULL
			&& smelter.getEnergy() == this.smelterEnergyStart && smelter.getItem(0).getCount() == 8 && smelter.getItem(3).getCount() == 64,
			"smelter: a full result slot stops the lane without using energy or input");
		smelter.extractForPickup(level.registryAccess());
		level.removeBlock(SMELTER, false);
	}

	private void setupAutoSmelter(ServerLevel level) {
		// 3x3x2 of iron ore over a layer of stone, inside a stone column.
		fill(level, 98, 196, 100, 102, 199, 104, Blocks.STONE.defaultBlockState());
		fill(level, 99, 198, 101, 101, 199, 103, Blocks.IRON_ORE.defaultBlockState());
		QuarryBlockEntity quarry = quarry(level);
		for (int i = 0; i < QuarryBlockEntity.UPGRADE_SLOTS; i++) {
			quarry.setItem(QuarryBlockEntity.BUFFER_SLOTS + i, ItemStack.EMPTY);
		}

		for (int i = 0; i < QuarryBlockEntity.BUFFER_SLOTS; i++) {
			quarry.setItem(i, ItemStack.EMPTY);
		}

		quarry.setItem(QuarryBlockEntity.BUFFER_SLOTS, new ItemStack(com.solarquarry.registry.ModItems.AUTO_SMELTER_UPGRADE));
		quarry.setItem(QuarryBlockEntity.BUFFER_SLOTS + 1, new ItemStack(com.solarquarry.registry.ModItems.SPEED_UPGRADE, 2));
		quarry.collectXp(net.fabricmc.fabric.api.entity.FakePlayer.get(level));
		configure(quarry, QuarryMode.STRIP_MINE, 3, 3, 1, 197);
		quarry.resetProgress();
		quarry.restoreEnergy(quarry.getEnergyCapacity());
		quarry.start();
	}

	private void verifyAutoSmelter(ServerLevel level) {
		QuarryBlockEntity quarry = quarry(level);
		int ingots = 0;
		int raw = 0;
		int cobble = 0;
		List<ItemStack> collected = new ArrayList<>();
		for (int i = 0; i < QuarryBlockEntity.BUFFER_SLOTS; i++) {
			collected.add(quarry.getItem(i));
		}

		if (level.getBlockEntity(Q.above()) instanceof Container chest) {
			for (int i = 0; i < chest.getContainerSize(); i++) {
				collected.add(chest.getItem(i));
			}
		}

		for (ItemStack stack : collected) {
			if (stack.is(Items.IRON_INGOT)) {
				ingots += stack.getCount();
			} else if (stack.is(Items.RAW_IRON)) {
				raw += stack.getCount();
			} else if (stack.is(Items.COBBLESTONE)) {
				cobble += stack.getCount();
			}
		}

		SelfTest.check(ingots == 18 && raw == 0, "auto-smelter: 18 iron ore became 18 iron ingots (" + ingots + " ingots, " + raw + " raw)");
		SelfTest.check(cobble == 9, "auto-smelter: stone is not smelted (" + cobble + " cobblestone)");
		SelfTest.check(quarry.getStoredXp() >= 12, "auto-smelter: smelting experience is stored (" + quarry.getStoredXp() + ")");
		for (int i = 0; i < QuarryBlockEntity.BUFFER_SLOTS + QuarryBlockEntity.UPGRADE_SLOTS; i++) {
			quarry.setItem(i, ItemStack.EMPTY);
		}

		quarry.collectXp(net.fabricmc.fabric.api.entity.FakePlayer.get(level));
		quarry.pause();
	}

	private void verifyBench(ServerLevel level) {
		var catalogue = com.solarquarry.crafting.BenchRecipes.catalogue(level);
		long modRecipes = level.recipeAccess().getRecipes().stream().filter(h -> h.id().identifier().getNamespace().equals(SolarQuarry.MOD_ID)).count();
		SelfTest.check(catalogue.size() == modRecipes && catalogue.stream().allMatch(e -> !e.result().isEmpty() && !e.needs().isEmpty()),
			"bench: the catalogue lists every mod recipe with its ingredients (" + catalogue.size() + "/" + modRecipes + ")");
		SelfTest.check(catalogue.getFirst().result().is(com.solarquarry.registry.ModBlocks.QUARRY.asItem()), "bench: catalogue follows the creative tab order");

		var fake = net.fabricmc.fabric.api.entity.FakePlayer.get(level);
		var inventory = fake.getInventory();
		inventory.clearContent();
		inventory.setItem(0, new ItemStack(Items.COPPER_INGOT, 5));
		inventory.setItem(1, new ItemStack(Items.GLASS_PANE, 3));
		int crafted = com.solarquarry.crafting.BenchRecipes.craft(fake, "solarquarry:fluid_pipe", 64);
		int pipes = inventory.countItem(com.solarquarry.registry.ModBlocks.FLUID_PIPE.asItem());
		SelfTest.check(crafted == 2 && pipes == 8 && inventory.countItem(Items.COPPER_INGOT) == 1 && inventory.countItem(Items.GLASS_PANE) == 1,
			"bench: 'craft all' stops when an ingredient runs out and uses exactly the recipe's items (" + crafted + " crafts, " + pipes + " pipes)");
		inventory.clearContent();
		inventory.setItem(3, new ItemStack(Items.IRON_INGOT, 4));
		inventory.setItem(7, new ItemStack(Items.BIRCH_PLANKS, 4));
		inventory.setItem(20, new ItemStack(Items.CHEST, 1));
		crafted = com.solarquarry.crafting.BenchRecipes.craft(fake, "solarquarry:crate_basic", 1);
		SelfTest.check(crafted == 1 && inventory.countItem(com.solarquarry.registry.ModBlocks.CRATE_BASIC.asItem()) == 1 && inventory.isEmpty() == false
			&& inventory.countItem(Items.BIRCH_PLANKS) == 0, "bench: tag ingredients (any planks) are accepted");
		SelfTest.check(com.solarquarry.crafting.BenchRecipes.craft(fake, "solarquarry:quarry", 1) == 0, "bench: nothing is crafted without the ingredients");
		SelfTest.check(com.solarquarry.crafting.BenchRecipes.craft(fake, "minecraft:chest", 1) == 0 && com.solarquarry.crafting.BenchRecipes.craft(fake, "solarquarry:nope", 1) == 0,
			"bench: only Solar Quarry recipes can be crafted through it");
		inventory.clearContent();
	}
}
