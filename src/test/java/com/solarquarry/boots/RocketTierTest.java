package com.solarquarry.boots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RocketTierTest {
	@Test
	void everyTierJumpsHigherHoldsMoreAndProtectsMore() {
		RocketTier[] tiers = RocketTier.values();
		double vanilla = RocketTier.jumpHeight(0.42);
		for (int i = 0; i < tiers.length; i++) {
			assertTrue(RocketTier.jumpHeight(tiers[i].launch()) > vanilla * 2, tiers[i] + " jumps well above a normal jump");
			if (i > 0) {
				assertTrue(tiers[i].launch() > tiers[i - 1].launch());
				assertTrue(tiers[i].capacity() > tiers[i - 1].capacity());
				assertTrue(tiers[i].fallProtection() > tiers[i - 1].fallProtection());
			}
		}
	}

	@Test
	void fallProtectionIsLimitedByEnergy() {
		assertEquals(4, RocketTier.absorbedDamage(10, 0.4, 1_000_000));
		assertEquals(10, RocketTier.absorbedDamage(10, 1.0, 1_000_000));
		assertEquals(3, RocketTier.absorbedDamage(10, 1.0, 3 * RocketTier.FALL_COST_PER_POINT + 50));
		assertEquals(0, RocketTier.absorbedDamage(10, 1.0, 0));
	}
}
