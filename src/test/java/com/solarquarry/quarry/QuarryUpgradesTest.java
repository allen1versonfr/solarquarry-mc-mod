package com.solarquarry.quarry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QuarryUpgradesTest {
	@Test
	void rangeTiersGiveSixteenThirtyTwoSixtyFour() {
		assertEquals(16, new QuarryUpgrades(0, 0, 0, 0, false, 0, false, false).maxAreaSize());
		assertEquals(32, new QuarryUpgrades(0, 0, 0, 0, false, 1, false, false).maxAreaSize());
		assertEquals(64, new QuarryUpgrades(0, 0, 0, 0, false, 2, false, false).maxAreaSize());
		assertEquals(64, new QuarryUpgrades(0, 0, 0, 0, false, 9, false, false).maxAreaSize(), "out-of-range tiers are clamped");
	}

	@Test
	void moreSpeedMeansMoreBlocksAndMorePowerPerBlock() {
		long lastDraw = 0;
		long lastCost = 0;
		int lastRate = 0;
		for (int speed = 0; speed <= 4; speed++) {
			QuarryUpgrades u = new QuarryUpgrades(speed, 0, 0, 0, false, 0, false, false);
			assertTrue(u.blocksPerMinute() > lastRate, "speed " + speed + " is faster");
			assertTrue(u.energyPerBlock(1.5F) > lastCost, "speed " + speed + " costs more per block");
			assertTrue(u.typicalPowerDraw() > lastDraw, "speed " + speed + " draws more power");
			lastRate = u.blocksPerMinute();
			lastCost = u.energyPerBlock(1.5F);
			lastDraw = u.typicalPowerDraw();
		}
	}

	@Test
	void efficiencyLowersCostAndCapacityRaisesStorage() {
		QuarryUpgrades base = QuarryUpgrades.NONE;
		QuarryUpgrades efficient = new QuarryUpgrades(0, 0, 3, 0, false, 0, false, false);
		assertTrue(efficient.energyPerBlock(1.5F) < base.energyPerBlock(1.5F));
		assertEquals(QuarryUpgrades.BASE_CAPACITY * 5, new QuarryUpgrades(0, 4, 0, 0, false, 0, false, false).energyCapacity());
	}

	@Test
	void costIsAlwaysPositiveAndBounded() {
		QuarryUpgrades max = new QuarryUpgrades(4, 4, 3, 3, true, 2, true, false);
		assertTrue(max.energyPerBlock(-1.0F) >= 1, "unbreakable/negative hardness never makes energy free");
		assertTrue(max.energyPerBlock(Float.MAX_VALUE) < 10_000, "hardness is capped, no overflow");
		assertTrue(max.ticksPerOperation() >= 1);
		assertTrue(max.blocksPerOperation() <= 2);
	}

	@Test
	void autoSmelterCostDropsWithEfficiencyButNeverReachesZero() {
		long base = new QuarryUpgrades(0, 0, 0, 0, false, 0, false, false).smeltCostPerItem();
		long efficient = new QuarryUpgrades(0, 0, 4, 0, false, 0, false, false).smeltCostPerItem();
		assertEquals(com.solarquarry.smelting.Smelting.AUTO_SMELT_COST, base);
		org.junit.jupiter.api.Assertions.assertTrue(efficient < base && efficient > 0, "efficiency lowers the smelting cost: " + efficient);
		assertEquals(base, new QuarryUpgrades(4, 0, 0, 3, false, 0, false, false).smeltCostPerItem(), "speed and fortune do not change it");
	}
}
