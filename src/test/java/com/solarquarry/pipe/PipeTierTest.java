package com.solarquarry.pipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PipeTierTest {
	@Test
	void eachTierIsFasterAndCarriesMore() {
		PipeTier[] tiers = PipeTier.values();
		for (int i = 1; i < tiers.length; i++) {
			assertTrue(tiers[i].blocksPerSecond() > tiers[i - 1].blocksPerSecond(), tiers[i] + " speed");
			assertTrue(tiers[i].batch() > tiers[i - 1].batch(), tiers[i] + " batch");
			assertTrue(tiers[i].capacity() > tiers[i - 1].capacity(), tiers[i] + " capacity");
		}
	}

	@Test
	void ticksPerBlock() {
		assertEquals(10, PipeTier.BASIC.ticksPerBlock());
		assertEquals(5, PipeTier.REINFORCED.ticksPerBlock());
		assertEquals(2, PipeTier.ADVANCED.ticksPerBlock());
	}
}
