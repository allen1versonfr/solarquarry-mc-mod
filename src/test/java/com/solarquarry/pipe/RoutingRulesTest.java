package com.solarquarry.pipe;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RoutingRulesTest {
	@Test
	void anyDestinationBeatsNone() {
		assertTrue(PipeRouter.better(false, -5, false, false, 0));
	}

	@Test
	void allowListMatchesBeatPriority() {
		assertTrue(PipeRouter.better(true, -5, true, false, 5));
		assertFalse(PipeRouter.better(false, 5, true, true, -5));
	}

	@Test
	void higherPriorityWinsAndTiesKeepTheNearerOne() {
		assertTrue(PipeRouter.better(false, 3, true, false, 0));
		assertFalse(PipeRouter.better(false, 0, true, false, 3));
		assertFalse(PipeRouter.better(false, 2, true, false, 2), "equal rank keeps the first (nearest) destination");
	}
}
