package com.solarquarry.quarry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MiningPatternTest {
	private static Set<String> allCells(MiningPattern pattern) {
		Set<String> cells = new HashSet<>();
		for (long step = 0; step < pattern.total(); step++) {
			MiningPattern.Cell cell = pattern.cell(step);
			assertTrue(cells.add(cell.lateral() + "," + cell.dy() + "," + cell.forward()), "cell visited twice at step " + step);
		}

		return cells;
	}

	@Test
	void stripMineVisitsEveryCellOnceLayerByLayer() {
		MiningPattern pattern = MiningPattern.of(QuarryMode.STRIP_MINE, 5, 3, 1, 100, 97, 319);
		assertEquals(5 * 3 * 4, pattern.total());
		Set<String> cells = allCells(pattern);
		assertEquals(60, cells.size());
		for (long step = 0; step < pattern.total(); step++) {
			MiningPattern.Cell cell = pattern.cell(step);
			assertEquals(-(int) (step / 15), cell.dy(), "layers go strictly downward");
			assertTrue(cell.lateral() >= 0 && cell.lateral() < 5);
			assertTrue(cell.forward() >= 1 && cell.forward() <= 3);
		}
	}

	@Test
	void serpentineOrderNeverJumpsMoreThanOneCellWithinALayer() {
		MiningPattern pattern = MiningPattern.of(QuarryMode.STRIP_MINE, 16, 16, 1, 64, 60, 319);
		for (long step = 1; step < pattern.total(); step++) {
			MiningPattern.Cell a = pattern.cell(step - 1);
			MiningPattern.Cell b = pattern.cell(step);
			int distance = Math.abs(a.lateral() - b.lateral()) + Math.abs(a.forward() - b.forward()) + Math.abs(a.dy() - b.dy());
			assertEquals(1, distance, "head moves one block per step (step " + step + ")");
		}
	}

	@Test
	void tunnelBoreStaysWithinWidthHeightAndLength() {
		MiningPattern pattern = MiningPattern.of(QuarryMode.TUNNEL_BORE, 3, 5, 2, 64, -64, 319);
		assertEquals(30, pattern.total());
		Set<String> cells = allCells(pattern);
		assertEquals(30, cells.size());
		for (long step = 0; step < pattern.total(); step++) {
			MiningPattern.Cell cell = pattern.cell(step);
			assertTrue(cell.dy() >= 0 && cell.dy() <= 1);
			assertTrue(cell.forward() >= 1 && cell.forward() <= 5);
		}

		assertEquals(1, pattern.cell(0).forward(), "tunnels start right behind the machine");
		assertEquals(1, pattern.cell(0).dy(), "each slice is cleared from the top");
	}

	@Test
	void areaClearWorksTopDownAboveTheQuarry() {
		MiningPattern pattern = MiningPattern.of(QuarryMode.AREA_CLEAR, 3, 3, 4, 64, -64, 319);
		assertEquals(36, pattern.total());
		assertEquals(3, pattern.cell(0).dy());
		assertEquals(0, pattern.cell(pattern.total() - 1).dy());
		assertEquals(0, pattern.minDy());
		assertEquals(3, pattern.maxDy());
	}

	@Test
	void upwardModesAreClampedToTheBuildLimit() {
		MiningPattern pattern = MiningPattern.of(QuarryMode.AREA_CLEAR, 2, 2, 128, 315, -64, 319);
		assertEquals(5, pattern.height());
		MiningPattern tunnel = MiningPattern.of(QuarryMode.TUNNEL_BORE, 2, 2, 999, 0, -64, 319);
		assertEquals(MiningPattern.MAX_TUNNEL_HEIGHT, tunnel.height());
	}

	@Test
	void degenerateInputsAreSafe() {
		MiningPattern pattern = MiningPattern.of(QuarryMode.STRIP_MINE, -4, 0, 0, 10, 20, 319);
		assertEquals(1, pattern.width());
		assertEquals(1, pattern.length());
		assertEquals(0, pattern.total(), "target above the quarry means nothing to mine");
		assertTrue(pattern.isComplete(0));
		assertEquals(1000, pattern.permille(0));
		assertEquals(pattern.cell(0), pattern.cell(-5));
	}

	@Test
	void progressIsMonotonicAndBounded() {
		MiningPattern pattern = MiningPattern.of(QuarryMode.STRIP_MINE, 7, 9, 1, 50, 40, 319);
		int last = -1;
		for (long step = 0; step <= pattern.total(); step++) {
			int p = pattern.permille(step);
			assertTrue(p >= last && p <= 1000);
			last = p;
		}

		assertEquals(1000, last);
		assertFalse(pattern.isComplete(pattern.total() - 1));
	}
}
