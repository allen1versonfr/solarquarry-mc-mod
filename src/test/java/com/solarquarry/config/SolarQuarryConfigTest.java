package com.solarquarry.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.solarquarry.quarry.QuarryUpgrades;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SolarQuarryConfigTest {
	@AfterEach
	void reset() {
		SolarQuarryConfig.setActiveForTesting(SolarQuarryConfig.DEFAULTS);
	}

	@Test
	void missingEntriesUseDefaults() {
		assertEquals(SolarQuarryConfig.DEFAULTS, SolarQuarryConfig.fromJson(new JsonObject()));
	}

	@Test
	void outOfRangeAndWrongTypesAreClampedOrIgnored() {
		JsonObject json = JsonParser.parseString("""
			{"energyCostMultiplier": 1000, "solarOutputMultiplier": -5, "maxRangeTier": 7, "allowChunkLoading": "yes",
			 "chunkLoaderUpkeep": -1, "prospectorCooldownTicks": 1, "terminalMaxQuarries": 500, "machineSoundVolume": 3}
			""").getAsJsonObject();
		SolarQuarryConfig config = SolarQuarryConfig.fromJson(json);
		assertEquals(10.0, config.energyCostMultiplier());
		assertEquals(0.1, config.solarOutputMultiplier());
		assertEquals(2, config.maxRangeTier());
		assertTrue(config.allowChunkLoading(), "a non-boolean keeps the default");
		assertEquals(0, config.chunkLoaderUpkeep());
		assertEquals(20, config.prospectorCooldownTicks());
		assertEquals(64, config.terminalMaxQuarries());
		assertEquals(1.0, config.machineSoundVolume());
	}

	@Test
	void notANumberFallsBackToDefault() {
		SolarQuarryConfig config = new SolarQuarryConfig(Double.NaN, Double.POSITIVE_INFINITY, 2, true, 8, true, 100, 32, Double.NaN).validated();
		assertEquals(1.0, config.energyCostMultiplier());
		assertEquals(1.0, config.solarOutputMultiplier());
		assertEquals(1.0, config.machineSoundVolume());
	}

	@Test
	void jsonRoundTrip() {
		SolarQuarryConfig config = new SolarQuarryConfig(2.5, 0.5, 1, false, 32, false, 200, 12, 0.25);
		assertEquals(config, SolarQuarryConfig.fromJson(SolarQuarryConfig.toJson(config)));
	}

	@Test
	void loadCreatesTheFileAndKeepsValidUserValues(@TempDir Path dir) throws Exception {
		SolarQuarryConfig created = SolarQuarryConfig.load(dir);
		assertEquals(SolarQuarryConfig.DEFAULTS, created);
		Path file = dir.resolve(SolarQuarryConfig.FILE_NAME);
		assertTrue(Files.exists(file));

		Files.writeString(file, "{\"energyCostMultiplier\": 2.0, \"maxRangeTier\": 1}");
		SolarQuarryConfig loaded = SolarQuarryConfig.load(dir);
		assertEquals(2.0, loaded.energyCostMultiplier());
		assertEquals(1, loaded.maxRangeTier());
		assertEquals(loaded, SolarQuarryConfig.get());
		assertTrue(Files.readString(file).contains("chunkLoaderUpkeep"), "missing keys are written back");
	}

	@Test
	void brokenFileIsLeftAloneAndDefaultsApply(@TempDir Path dir) throws Exception {
		Path file = dir.resolve(SolarQuarryConfig.FILE_NAME);
		Files.writeString(file, "{ this is not json");
		assertEquals(SolarQuarryConfig.DEFAULTS, SolarQuarryConfig.load(dir));
		assertEquals("{ this is not json", Files.readString(file), "a file the user is editing is never overwritten");
	}

	@Test
	void configScalesEnergyAndCapsTheArea() {
		QuarryUpgrades range2 = new QuarryUpgrades(0, 0, 0, 0, false, 2, false, false);
		long base = range2.energyPerBlock(1.5F);
		SolarQuarryConfig.setActiveForTesting(new SolarQuarryConfig(2.0, 1.0, 1, true, 8, true, 100, 32, 1.0));
		assertEquals(base * 2, range2.energyPerBlock(1.5F), 1);
		assertEquals(32, range2.maxAreaSize(), "maxRangeTier 1 caps a Range II quarry at 32 x 32");
		SolarQuarryConfig.setActiveForTesting(SolarQuarryConfig.DEFAULTS);
		assertEquals(64, range2.maxAreaSize());
		assertFalse(SolarQuarryConfig.DEFAULTS.equals(new SolarQuarryConfig(2.0, 1.0, 1, true, 8, true, 100, 32, 1.0)));
	}
}
