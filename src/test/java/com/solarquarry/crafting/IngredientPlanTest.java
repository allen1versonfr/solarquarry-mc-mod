package com.solarquarry.crafting;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class IngredientPlanTest {
	@Test
	void takesFromMatchingSlotsInOrder() {
		int[] take = IngredientPlan.plan(new int[] {3, 5, 2}, new boolean[][] {{true, false, true}}, new int[] {4});
		assertArrayEquals(new int[] {3, 0, 1}, take);
	}

	@Test
	void notEnoughMeansNothingIsTaken() {
		assertNull(IngredientPlan.plan(new int[] {1, 1}, new boolean[][] {{true, true}}, new int[] {3}));
	}

	@Test
	void overlappingIngredientsCannotShareTheSameItems() {
		// Two requirements both accept slot 0 (2 items); each needs 2: only one can be paid from it.
		boolean[][] matches = {{true, false}, {true, true}};
		assertArrayEquals(new int[] {2, 2}, IngredientPlan.plan(new int[] {2, 2}, matches, new int[] {2, 2}));
		assertNull(IngredientPlan.plan(new int[] {2, 1}, matches, new int[] {2, 2}));
	}
}
