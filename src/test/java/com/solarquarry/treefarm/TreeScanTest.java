package com.solarquarry.treefarm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TreeScanTest {
	private static final class MapGrid implements TreeScan.Grid {
		final Map<TreeScan.Pos, TreeScan.Kind> cells = new HashMap<>();

		void set(int x, int y, int z, TreeScan.Kind kind) {
			this.cells.put(new TreeScan.Pos(x, y, z), kind);
		}

		@Override
		public TreeScan.Kind kind(int x, int y, int z) {
			if (y < 0) {
				return TreeScan.Kind.NATURAL;
			}

			return this.cells.getOrDefault(new TreeScan.Pos(x, y, z), TreeScan.Kind.AIR);
		}
	}

	/** A small oak: 5 logs and a 3x3x2 leaf crown around the top. */
	private static MapGrid oak(TreeScan.Kind leaves) {
		MapGrid grid = new MapGrid();
		for (int y = 0; y < 5; y++) {
			grid.set(0, y, 0, TreeScan.Kind.LOG);
		}

		for (int y = 3; y <= 5; y++) {
			for (int x = -1; x <= 1; x++) {
				for (int z = -1; z <= 1; z++) {
					if (!(x == 0 && z == 0 && y < 5)) {
						grid.set(x, y, z, leaves);
					}
				}
			}
		}

		return grid;
	}

	@Test
	void findsANaturalTree() {
		TreeScan.Tree tree = TreeScan.find(oak(TreeScan.Kind.NATURAL_LEAVES), 0, 0, 0);
		assertNotNull(tree);
		assertEquals(5, tree.logs().size());
		assertEquals(25, tree.leaves().size());
		assertEquals(new TreeScan.Pos(0, 0, 0), tree.logs().getFirst());
	}

	@Test
	void placedLeavesAreNotATree() {
		assertNull(TreeScan.find(oak(TreeScan.Kind.PLACED_LEAVES), 0, 0, 0));
	}

	@Test
	void aBarePillarIsNotATree() {
		MapGrid grid = new MapGrid();
		for (int y = 0; y < 6; y++) {
			grid.set(0, y, 0, TreeScan.Kind.LOG);
		}

		assertNull(TreeScan.find(grid, 0, 0, 0));
	}

	@Test
	void logsTouchingABuildingAreLeftAlone() {
		MapGrid grid = oak(TreeScan.Kind.NATURAL_LEAVES);
		grid.set(1, 1, 0, TreeScan.Kind.BUILT); // a plank wall against the trunk
		assertNull(TreeScan.find(grid, 0, 0, 0));
	}

	@Test
	void diagonalBranchesAreFollowed() {
		MapGrid grid = oak(TreeScan.Kind.NATURAL_LEAVES);
		grid.set(1, 2, 1, TreeScan.Kind.LOG); // acacia-style branch
		grid.set(2, 3, 2, TreeScan.Kind.LOG);
		TreeScan.Tree tree = TreeScan.find(grid, 0, 0, 0);
		assertNotNull(tree);
		assertEquals(7, tree.logs().size());
	}

	@Test
	void aHugeLogMassIsAStructure() {
		MapGrid grid = new MapGrid();
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 9; z++) {
				for (int y = 0; y < 8; y++) {
					grid.set(x, y, z, TreeScan.Kind.LOG);
				}
			}
		}

		assertNull(TreeScan.find(grid, 0, 0, 0));
	}
}
