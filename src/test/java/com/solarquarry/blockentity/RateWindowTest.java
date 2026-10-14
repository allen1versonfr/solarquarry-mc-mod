package com.solarquarry.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RateWindowTest {
	@Test
	void zeroBeforeTheFirstSecondCompletes() {
		RateWindow window = new RateWindow(10);
		window.record(5);
		assertEquals(0.0, window.perSecond());
	}

	@Test
	void averagesCompletedSecondsOnly() {
		RateWindow window = new RateWindow(10);
		window.record(4);
		window.advance();
		window.record(2);
		window.advance();
		window.record(100); // current, incomplete second is not counted
		assertEquals(3.0, window.perSecond(), 1e-9);
	}

	@Test
	void oldSecondsRollOff() {
		RateWindow window = new RateWindow(3);
		for (int i = 0; i < 3; i++) {
			window.record(10);
			window.advance();
		}

		for (int i = 0; i < 3; i++) {
			window.advance();
		}

		assertEquals(0.0, window.perSecond());
	}

	@Test
	void negativeCountsAreIgnored() {
		RateWindow window = new RateWindow(2);
		window.record(-5);
		window.record(2);
		window.advance();
		assertEquals(2.0, window.perSecond());
	}
}
