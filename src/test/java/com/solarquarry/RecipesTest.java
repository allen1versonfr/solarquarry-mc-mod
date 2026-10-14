package com.solarquarry;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Every recipe must be craftable from Overworld and End materials only. */
class RecipesTest {
	/** Items that only come from the Nether, or need a Nether item to craft (quartz in comparators and observers). */
	private static final List<String> NETHER = List.of(
		"quartz", "netherite", "nether_", "blaze_", "ghast_", "magma", "glowstone", "soul_", "netherrack", "ancient_debris",
		"crimson", "warped", "basalt", "blackstone", "shroomlight", "wither", "comparator", "observer", "daylight_detector",
		"piglin", "respawn_anchor", "lodestone", "fire_charge"
	);

	@Test
	void noRecipeUsesNetherMaterials() throws IOException {
		Path dir = Path.of("src/main/resources/data/solarquarry/recipe");
		List<String> offenders = new ArrayList<>();
		int checked = 0;
		try (Stream<Path> files = Files.list(dir)) {
			for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
				checked++;
				String json = Files.readString(file);
				int keyStart = json.indexOf("\"key\"");
				int resultStart = json.indexOf("\"result\"");
				String ingredients = keyStart >= 0 && resultStart > keyStart ? json.substring(keyStart, resultStart) : json;
				for (String word : NETHER) {
					if (ingredients.contains("minecraft:" + word) || ingredients.contains("#minecraft:" + word)) {
						offenders.add(file.getFileName() + " uses " + word);
					}
				}
			}
		}

		assertTrue(checked > 0, "no recipes found in " + dir.toAbsolutePath());
		assertTrue(offenders.isEmpty(), "Nether materials in recipes: " + offenders);
	}
}
