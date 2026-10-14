package com.solarquarry.crafting;

/**
 * Decides which inventory slots pay for one craft. Pure logic (counts and a match table), so it is unit tested
 * without a running game. Each requirement takes from the slots it matches, earlier slots first; a slot used by one
 * requirement is not counted again for the next, so overlapping ingredients cannot be paid twice from the same items.
 */
public final class IngredientPlan {
	private IngredientPlan() {
	}

	/**
	 * @param available items in each inventory slot
	 * @param matches matches[need][slot]: whether that slot's item satisfies the requirement
	 * @param needed how many items each requirement takes
	 * @return items to remove from each slot, or null if the inventory cannot pay for one craft
	 */
	public static int[] plan(int[] available, boolean[][] matches, int[] needed) {
		int[] left = available.clone();
		int[] take = new int[available.length];
		for (int need = 0; need < needed.length; need++) {
			int missing = needed[need];
			for (int slot = 0; slot < left.length && missing > 0; slot++) {
				if (matches[need][slot] && left[slot] > 0) {
					int n = Math.min(left[slot], missing);
					left[slot] -= n;
					take[slot] += n;
					missing -= n;
				}
			}

			if (missing > 0) {
				return null;
			}
		}

		return take;
	}
}
