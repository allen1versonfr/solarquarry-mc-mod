package com.solarquarry.splitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class SplitterConfigTest {
	@Test
	void orientUsesThePlayersView() {
		SplitterConfig c = new SplitterConfig();
		c.orient(Direction.SOUTH);
		assertEquals(Direction.SOUTH, c.matchFace());
		assertEquals(Direction.WEST, c.otherFace());
		assertEquals(Direction.NORTH, c.inputFace());
	}

	@Test
	void whitelistAndBlacklistPickTheSide() {
		SplitterConfig c = new SplitterConfig();
		c.orient(Direction.NORTH);
		assertEquals(Direction.NORTH, c.outputFor(true));
		assertEquals(Direction.EAST, c.outputFor(false));
		c.setWhitelist(false);
		assertEquals(Direction.EAST, c.outputFor(true));
		assertEquals(Direction.NORTH, c.outputFor(false));
	}

	@Test
	void outputsNeverAcceptItemsAndNeverCoincide() {
		SplitterConfig c = new SplitterConfig();
		c.orient(Direction.NORTH);
		for (int i = 0; i < 20; i++) {
			c.cycleMatch();
			assertNotEquals(c.matchFace(), c.otherFace());
			assertNotEquals(c.matchFace(), c.inputFace());
			c.cycleOther();
			assertNotEquals(c.matchFace(), c.otherFace());
			assertNotEquals(c.otherFace(), c.inputFace());
			c.cycleInput();
			assertFalse(c.acceptsFrom(c.matchFace()));
			assertFalse(c.acceptsFrom(c.otherFace()));
		}
	}

	@Test
	void inputCyclesThroughFreeFacesAndAny() {
		SplitterConfig c = new SplitterConfig();
		c.set(Direction.NORTH, Direction.EAST, null);
		assertTrue(c.acceptsFrom(Direction.WEST));
		assertTrue(c.acceptsFrom(Direction.UP));
		int seen = 0;
		do {
			c.cycleInput();
			seen++;
		} while (c.inputFace() != null && seen < 10);
		assertEquals(5, seen); // 4 free faces, then back to "any"
		c.set(Direction.NORTH, Direction.NORTH, Direction.NORTH);
		assertNotEquals(c.matchFace(), c.otherFace());
		assertNull(c.inputFace());
	}
}
