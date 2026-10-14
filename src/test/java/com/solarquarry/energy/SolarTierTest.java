package com.solarquarry.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SolarTierTest {
	@Test
	void noSkyNoPower() {
		for (SolarTier tier : SolarTier.values()) {
			assertEquals(0, tier.generation(1.0F, false, false, false));
		}
	}

	@Test
	void peakAtNoonAndHigherTiersProduceMore() {
		assertEquals(8, SolarTier.BASIC.generation(1.0F, true, false, false));
		assertEquals(40, SolarTier.ADVANCED.generation(1.0F, true, false, false));
		assertEquals(180, SolarTier.ELITE.generation(1.0F, true, false, false));
	}

	@Test
	void onlyEliteKeepsANightTrickle() {
		assertEquals(0, SolarTier.BASIC.generation(0.0F, true, false, false));
		assertEquals(0, SolarTier.ADVANCED.generation(0.0F, true, false, false));
		int night = SolarTier.ELITE.generation(0.0F, true, false, false);
		assertTrue(night > 0 && night < 180 / 5);
	}

	@Test
	void weatherHurtsLowerTiersMore() {
		float basicRain = SolarTier.BASIC.generation(1.0F, true, true, false) / 8.0F;
		float eliteRain = SolarTier.ELITE.generation(1.0F, true, true, false) / 180.0F;
		assertTrue(eliteRain > basicRain);
		assertTrue(SolarTier.ADVANCED.generation(1.0F, true, true, true) < SolarTier.ADVANCED.generation(1.0F, true, true, false), "storms are worse than rain");
	}

	@Test
	void outputRisesWithTheSun() {
		int last = -1;
		for (int i = 1; i <= 20; i++) {
			int now = SolarTier.ADVANCED.generation(i / 20.0F, true, false, false);
			assertTrue(now >= last);
			last = now;
		}
	}
}
