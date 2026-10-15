package com.solarquarry;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tier 4 (high-end) recipes need Helionite, so the ore has a purpose beyond its own gear (docs/PROGRESSION.md). */
class ProgressionTest {
	/** Tier 4 items; new high-end items are added here as they are created. */
	static final List<String> TIER_4 = List.of("solar_panel_elite", "battery_elite", "helionite_rocket_boots", "laser_turret_elite", "weather_control_machine");

	@Test
	void tierFourRecipesNeedHelionite() throws IOException {
		List<String> missing = new ArrayList<>();
		for (String id : TIER_4) {
			Path file = Path.of("src/main/resources/data/solarquarry/recipe/" + id + ".json");
			assertTrue(Files.exists(file), "no recipe for " + id);
			if (!Files.readString(file).contains("solarquarry:helionite_ingot") && !Files.readString(file).contains("solarquarry:helionite_block")) {
				missing.add(id);
			}
		}

		assertTrue(missing.isEmpty(), "Tier 4 recipes without Helionite: " + missing);
	}
}
