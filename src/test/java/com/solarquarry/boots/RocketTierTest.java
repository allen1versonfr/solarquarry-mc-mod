package com.solarquarry.boots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RocketTierTest {
	@Test
	void everyTierClimbsFasterHoldsMoreFliesLongerAndProtectsMore() {
		RocketTier[] tiers = RocketTier.values();
		for (int i = 0; i < tiers.length; i++) {
			assertTrue(tiers[i].climb() > 0.2, tiers[i] + " climbs");
			if (i > 0) {
				assertTrue(tiers[i].climb() > tiers[i - 1].climb());
				assertTrue(tiers[i].capacity() > tiers[i - 1].capacity());
				assertTrue(tiers[i].flightSeconds() > tiers[i - 1].flightSeconds());
				assertTrue(tiers[i].fallProtection() > tiers[i - 1].fallProtection());
			}
		}
	}

	@Test
	void thrustAcceleratesUpToTheClimbSpeedOnly() {
		assertEquals(0.16, RocketTier.thrust(0.0, 0.5), 1e-9);
		assertEquals(0.5, RocketTier.thrust(0.45, 0.5), 1e-9);
		assertEquals(0.8, RocketTier.thrust(0.8, 0.5), 1e-9, "never slows a faster rise");
		assertEquals(-0.34, RocketTier.thrust(-0.5, 0.5), 1e-9, "brakes a fall gradually");
	}

	@Test
	void fallProtectionIsLimitedByEnergy() {
		assertEquals(4, RocketTier.absorbedDamage(10, 0.4, 1_000_000));
		assertEquals(10, RocketTier.absorbedDamage(10, 1.0, 1_000_000));
		assertEquals(3, RocketTier.absorbedDamage(10, 1.0, 3 * RocketTier.FALL_COST_PER_POINT + 50));
		assertEquals(0, RocketTier.absorbedDamage(10, 1.0, 0));
	}
}
