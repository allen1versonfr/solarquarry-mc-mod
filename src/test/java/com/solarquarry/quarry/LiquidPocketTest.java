package com.solarquarry.quarry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

class LiquidPocketTest {
	/** A fake grid: liquid at the given positions (the state itself is not needed for mapping order checks). */
	private static LiquidPocket mapped(Set<BlockPos> water, BoundingBox bounds, BlockPos seed) {
		LiquidPocket pocket = new LiquidPocket();
		pocket.start(seed, bounds, true);
		int rounds = 0;
		while (!pocket.map(p -> water.contains(p) ? LiquidPocket.SOURCE : LiquidPocket.NONE, 100)) {
			rounds++;
		}

		assertTrue(rounds >= 0);
		return pocket;
	}

	@Test
	void mapsTheConnectedPocketInsideTheBoundsTopFirst() {
		Set<BlockPos> water = new HashSet<>();
		for (int x = 0; x < 10; x++) {
			for (int z = 0; z < 10; z++) {
				for (int y = 0; y < 3; y++) {
					water.add(new BlockPos(x, y, z));
				}
			}
		}

		water.add(new BlockPos(20, 0, 0)); // not connected
		water.add(new BlockPos(10, 0, 0)); // connected but outside the bounds
		LiquidPocket pocket = mapped(water, new BoundingBox(0, 0, 0, 9, 5, 9), new BlockPos(5, 1, 5));
		assertEquals(LiquidPocket.Phase.DRAINING, pocket.phase());
		assertEquals(300, pocket.total());
		assertEquals(2, pocket.peek().getY(), "top layer first");
		int lastY = 99;
		while (pocket.peek() != null) {
			assertTrue(pocket.peek().getY() <= lastY);
			lastY = pocket.peek().getY();
			pocket.advance(true);
		}

		assertNull(pocket.peek());
		assertEquals(300, pocket.removed());
	}

	@Test
	void stopsAfterTheCellLimitAndAfterRepeatedInflow() {
		Set<BlockPos> water = new HashSet<>();
		for (int x = 0; x < 64; x++) {
			for (int z = 0; z < 64; z++) {
				for (int y = 0; y < 3; y++) {
					water.add(new BlockPos(x, y, z));
				}
			}
		}

		LiquidPocket pocket = mapped(water, new BoundingBox(0, 0, 0, 63, 5, 63), new BlockPos(0, 0, 0));
		assertEquals(LiquidPocket.MAX_CELLS, pocket.total());
		assertTrue(pocket.truncated());
		for (int pass = 1; pass <= LiquidPocket.MAX_PASSES; pass++) {
			pocket.advance(true);
			boolean again = pocket.finishPass();
			if (pass < LiquidPocket.MAX_PASSES) {
				assertTrue(again);
				pocket.start(new BlockPos(0, 0, 0), new BoundingBox(0, 0, 0, 63, 5, 63), false);
				pocket.map(p -> water.contains(p) ? LiquidPocket.SOURCE : LiquidPocket.NONE, 100_000);
			} else {
				assertFalse(again);
				assertTrue(pocket.inflow(), "gives up after repeated passes that keep finding liquid");
			}
		}
	}

	@Test
	void aDryPassEndsTheJob() {
		LiquidPocket pocket = new LiquidPocket();
		pocket.start(BlockPos.ZERO, new BoundingBox(0, 0, 0, 3, 3, 3), true);
		assertTrue(pocket.map(p -> LiquidPocket.NONE, 10));
		assertEquals(LiquidPocket.Phase.IDLE, pocket.phase());
		assertFalse(pocket.finishPass());
		assertFalse(pocket.inflow());
	}
}
