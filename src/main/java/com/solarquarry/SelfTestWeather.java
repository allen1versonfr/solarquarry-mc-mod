package com.solarquarry;

import com.solarquarry.registry.ModBlocks;
import com.solarquarry.weather.WeatherMachineBlockEntity;
import com.solarquarry.weather.WeatherOperation;
import com.solarquarry.weather.WeatherStage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueOutput;

/** Self-tests for the Weather Control Machine: real weather and time changes, exact energy, power loss, persistence. */
final class SelfTestWeather {
	static final BlockPos MACHINE = new BlockPos(450, 201, 450);
	private static long energyBefore;
	private static long gameTimeBefore;
	private static int todBefore;

	private SelfTestWeather() {
	}

	private static WeatherMachineBlockEntity machine(ServerLevel level) {
		return level.getBlockEntity(MACHINE) instanceof WeatherMachineBlockEntity m ? m : null;
	}

	private static int timeOfDay(ServerLevel level) {
		return (int) Math.floorMod(level.getServer().clockManager().getTotalTicks(WeatherMachineBlockEntity.clock(level)), 24_000L);
	}

	static void setupThunder(ServerLevel level) {
		level.setChunkForced(MACHINE.getX() >> 4, MACHINE.getZ() >> 4, true);
		level.setBlock(MACHINE, ModBlocks.WEATHER_MACHINE.defaultBlockState(), Block.UPDATE_ALL);
		WeatherMachineBlockEntity m = machine(level);
		level.getServer().setWeatherParameters(6_000, 0, false, false);
		SelfTest.check(m.startWeather(level, WeatherOperation.RAIN) == WeatherMachineBlockEntity.Error.NO_ENERGY && m.getStage() == WeatherStage.STANDBY
			&& m.getEnergy() == 0, "weather: without enough energy nothing starts and nothing is taken");
		ServerLevel end = level.getServer().getLevel(Level.END);
		SelfTest.check(end == null || !WeatherMachineBlockEntity.hasWeather(end), "weather: refuses dimensions without weather (the End)");
		m.restoreEnergy(WeatherMachineBlockEntity.CAPACITY);
		energyBefore = m.getEnergy();
		var first = m.startWeather(level, WeatherOperation.THUNDER);
		var second = m.startWeather(level, WeatherOperation.THUNDER);
		SelfTest.check(first == WeatherMachineBlockEntity.Error.NONE && second == WeatherMachineBlockEntity.Error.BUSY
			&& m.getEnergy() == energyBefore - WeatherOperation.THUNDER.startCost(), "weather: a thunderstorm costs " + WeatherOperation.THUNDER.startCost()
			+ " E once; a second request while busy is refused and costs nothing");
		SelfTest.check(m.getStage() == WeatherStage.POWER_INTAKE && !level.getWeatherData().isThundering(), "weather: the startup sequence runs before the sky changes");
	}

	static void verifyThunder(ServerLevel level) {
		WeatherMachineBlockEntity m = machine(level);
		var data = level.getWeatherData();
		SelfTest.check(data.isThundering() && data.isRaining() && m.getOperationsCompleted() == 1, "weather: a real thunderstorm started (server weather data)");
		SelfTest.check(m.getStage() == WeatherStage.STANDBY, "weather: a one-time change shuts the machine down afterwards (" + m.getStage() + ")");
		SelfTest.check(m.startCost(level, WeatherOperation.CLEAR) == WeatherOperation.CLEAR.startCost() * 2, "weather: clearing a thunderstorm costs double");
	}

	static void setupSustainedClear(ServerLevel level) {
		WeatherMachineBlockEntity m = machine(level);
		m.restoreEnergy(WeatherMachineBlockEntity.CAPACITY);
		m.setSustained(true);
		m.setDurationIndex(0);
		SelfTest.check(m.startWeather(level, WeatherOperation.CLEAR) == WeatherMachineBlockEntity.Error.NONE, "weather: a finished machine accepts the next operation");
	}

	static void verifySustainedClear(ServerLevel level) {
		WeatherMachineBlockEntity m = machine(level);
		SelfTest.check(m.getStage() == WeatherStage.STABILIZATION && !level.getWeatherData().isRaining() && !level.getWeatherData().isThundering(),
			"weather: sustained clear skies are being held (" + m.getStage() + ")");
		energyBefore = m.getEnergy();
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		m.saveWithFullMetadata(out);
		BlockEntity copy = BlockEntity.loadStatic(MACHINE, level.getBlockState(MACHINE), out.buildResult(), level.registryAccess());
		SelfTest.check(copy instanceof WeatherMachineBlockEntity c && c.getStage() == WeatherStage.STABILIZATION && c.getOperation() == WeatherOperation.CLEAR
			&& c.getRemaining() == m.getRemaining() && c.getEnergy() == m.getEnergy(), "weather: the held operation survives save/load");
	}

	static void verifyUpkeep(ServerLevel level) {
		WeatherMachineBlockEntity m = machine(level);
		long used = energyBefore - m.getEnergy();
		long upkeep = WeatherOperation.CLEAR.upkeep();
		SelfTest.check(used >= 39 * upkeep && used <= 41 * upkeep && used % upkeep == 0, "weather: holding the weather costs " + upkeep + " E every tick (" + used + " E in 40 ticks)");
		m.setEnergyForTest(upkeep * 2);
	}

	static void verifyPowerLoss(ServerLevel level) {
		WeatherMachineBlockEntity m = machine(level);
		SelfTest.check((m.getStage() == WeatherStage.SHUTDOWN || m.getStage() == WeatherStage.STANDBY) && m.getError() == WeatherMachineBlockEntity.Error.POWER_LOST
			&& m.getEnergy() >= 0, "weather: out of energy it shuts down in a controlled way and says why");
	}

	static void setupTime(ServerLevel level) {
		WeatherMachineBlockEntity m = machine(level);
		m.restoreEnergy(WeatherMachineBlockEntity.CAPACITY);
		energyBefore = m.getEnergy();
		gameTimeBefore = level.getGameTime();
		todBefore = timeOfDay(level);
		m.startTime(level, 6_000, false);
	}

	static void verifyTime(ServerLevel level) {
		WeatherMachineBlockEntity m = machine(level);
		int tod = timeOfDay(level);
		long elapsed = level.getGameTime() - gameTimeBefore;
		SelfTest.check(tod >= 6_000 && tod <= 6_000 + 200, "weather: the noon preset moved the world clock to noon (" + tod + ", was " + todBefore + ")");
		SelfTest.check(elapsed > 0 && elapsed < 400, "weather: total game time is untouched (" + elapsed + " ticks passed)");
		SelfTest.check(energyBefore - m.getEnergy() == WeatherOperation.TIME_PRESET.startCost(), "weather: a time preset costs exactly " + WeatherOperation.TIME_PRESET.startCost() + " E");
	}

	static void setupGradual(ServerLevel level) {
		WeatherMachineBlockEntity m = machine(level);
		m.restoreEnergy(WeatherMachineBlockEntity.CAPACITY);
		energyBefore = m.getEnergy();
		m.startTime(level, 18_000, true);
	}

	static void verifyGradual(ServerLevel level) {
		WeatherMachineBlockEntity m = machine(level);
		int tod = timeOfDay(level);
		long used = energyBefore - m.getEnergy();
		long upkeep = WeatherOperation.TIME_GRADUAL.upkeep();
		long steps = (used - WeatherOperation.TIME_GRADUAL.startCost()) / upkeep;
		SelfTest.check(tod >= 18_000 && tod <= 18_000 + 300 && m.getOperationsCompleted() >= 3, "weather: the gradual change reached midnight step by step (" + tod + ")");
		SelfTest.check(steps >= 200 && steps <= 260 && (used - WeatherOperation.TIME_GRADUAL.startCost()) % upkeep == 0,
			"weather: the gradual change paid " + upkeep + " E per step (" + steps + " steps)");
		level.removeBlock(MACHINE, false);
		level.setChunkForced(MACHINE.getX() >> 4, MACHINE.getZ() >> 4, false);
		level.getServer().setWeatherParameters(6_000, 0, false, false);
	}
}
