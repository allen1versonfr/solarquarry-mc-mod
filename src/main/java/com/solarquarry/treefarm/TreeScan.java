package com.solarquarry.treefarm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bounded tree detection on an abstract grid (unit tested without a world). From a base log it collects connected
 * logs (including diagonal branches), then the natural leaves around them. A cluster only counts as a tree when it has
 * enough natural (non-player-placed) leaves and no log touches a built block, so log cabins, beams and decorated
 * trees next to buildings are never cut.
 */
public final class TreeScan {
	public static final int MAX_LOGS = 512;
	public static final int MAX_LEAVES = 2048;
	public static final int MAX_HORIZONTAL = 8;
	public static final int MAX_HEIGHT = 40;
	public static final int LEAF_REACH = 6;
	public static final int MIN_LEAVES = 4;

	/** What a cell holds, as far as tree detection cares. */
	public enum Kind {
		AIR,
		LOG,
		NATURAL_LEAVES,
		/** Leaves a player placed (persistent): not part of a tree and not cut. */
		PLACED_LEAVES,
		/** Ground, plants, snow, vines...: natural surroundings. */
		NATURAL,
		/** Anything else (planks, glass, machines...): a log touching one is part of a structure. */
		BUILT
	}

	public interface Grid {
		Kind kind(int x, int y, int z);
	}

	public record Pos(int x, int y, int z) {
	}

	/** A detected tree: its logs (bottom first) and natural leaves. */
	public record Tree(List<Pos> logs, List<Pos> leaves) {
	}

	private TreeScan() {
	}

	/** Returns the tree whose lowest log is at (x, y, z), or null if there is no natural tree there. */
	public static Tree find(Grid grid, int bx, int by, int bz) {
		if (grid.kind(bx, by, bz) != Kind.LOG) {
			return null;
		}

		List<Pos> logs = new ArrayList<>();
		Set<Pos> seen = new HashSet<>();
		ArrayDeque<Pos> queue = new ArrayDeque<>();
		Pos base = new Pos(bx, by, bz);
		queue.add(base);
		seen.add(base);
		while (!queue.isEmpty()) {
			Pos p = queue.poll();
			logs.add(p);
			if (logs.size() > MAX_LOGS) {
				return null; // far bigger than any tree: a structure
			}

			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = 0; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx == 0 && dy == 0 && dz == 0) {
							continue;
						}

						Pos n = new Pos(p.x() + dx, p.y() + dy, p.z() + dz);
						if (Math.abs(n.x() - bx) > MAX_HORIZONTAL || Math.abs(n.z() - bz) > MAX_HORIZONTAL || n.y() - by > MAX_HEIGHT) {
							continue;
						}

						if (!seen.contains(n) && grid.kind(n.x(), n.y(), n.z()) == Kind.LOG) {
							seen.add(n);
							queue.add(n);
						}
					}
				}
			}

			// A log touching a built block (on any face) belongs to a structure.
			for (int[] d : FACES) {
				if (grid.kind(p.x() + d[0], p.y() + d[1], p.z() + d[2]) == Kind.BUILT) {
					return null;
				}
			}
		}

		Set<Pos> logSet = new HashSet<>(logs);
		List<Pos> leaves = new ArrayList<>();
		Set<Pos> leafSeen = new HashSet<>();
		ArrayDeque<Pos> leafQueue = new ArrayDeque<>();
		for (Pos log : logs) {
			for (int[] d : FACES) {
				Pos n = new Pos(log.x() + d[0], log.y() + d[1], log.z() + d[2]);
				if (!logSet.contains(n) && leafSeen.add(n) && grid.kind(n.x(), n.y(), n.z()) == Kind.NATURAL_LEAVES) {
					leafQueue.add(n);
				}
			}
		}

		while (!leafQueue.isEmpty() && leaves.size() < MAX_LEAVES) {
			Pos p = leafQueue.poll();
			leaves.add(p);
			for (int[] d : FACES) {
				Pos n = new Pos(p.x() + d[0], p.y() + d[1], p.z() + d[2]);
				if (leafSeen.add(n) && nearAnyLog(n, logs) && grid.kind(n.x(), n.y(), n.z()) == Kind.NATURAL_LEAVES) {
					leafQueue.add(n);
				}
			}
		}

		return leaves.size() >= MIN_LEAVES ? new Tree(logs, leaves) : null;
	}

	private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

	private static boolean nearAnyLog(Pos p, List<Pos> logs) {
		for (Pos log : logs) {
			if (Math.abs(log.x() - p.x()) + Math.abs(log.y() - p.y()) + Math.abs(log.z() - p.z()) <= LEAF_REACH) {
				return true;
			}
		}

		return false;
	}
}
