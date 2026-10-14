package com.solarquarry;

import com.solarquarry.block.QuarryBlock;
import com.solarquarry.blockentity.BatteryBlockEntity;
import com.solarquarry.blockentity.QuarryBlockEntity;
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
	}

	static void forceChunks(ServerLevel level) {
		for (int cx = 5; cx <= 7; cx++) {
			for (int cz = 5; cz <= 9; cz++) {
				level.setChunkForced(cx, cz, true);
			}
		}
	}

	static boolean chunksReady(ServerLevel level) {
		return level.isLoaded(new BlockPos(88, 200, 88)) && level.isLoaded(new BlockPos(120, 200, 120)) && level.isLoaded(new BlockPos(110, 200, 150));
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
				QuarryBlockEntity quarry = quarry(level);
				if (scenario.timed()) {
					if (++this.wait >= scenario.timeout()) {
						scenario.verify().accept(level);
						this.index++;
						this.stage = 0;
					}
				} else if (scenario.done().test(quarry) || ++this.wait > scenario.timeout()) {
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

	private static final BlockPos L = new BlockPos(100, 200, 140);
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
		for (int x = 96; x <= 108; x++) {
			for (int z = 134; z <= 146; z++) {
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
}
