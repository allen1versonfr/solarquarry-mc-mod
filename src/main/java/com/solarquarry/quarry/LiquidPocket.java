package com.solarquarry.quarry;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A connected body of liquid inside a quarry's work volume, found by a bounded flood fill and drained as one job.
 *
 * <p>Draining a pond one block at a time does not work: every removed source is refilled by flowing water from its
 * neighbours, and two neighbouring sources even make a new one (the infinite-water rule), so a pump can burn energy
 * forever without progress. The Liquid Remover therefore maps the whole pocket first (at most {@link #MAX_CELLS}
 * cells, {@link #MAP_PER_TICK} per tick, never outside the work volume or into unloaded chunks) and then removes it
 * from the top layer down without waking the neighbouring liquid, so nothing flows back while it works. Liquid that
 * still flows in afterwards (from outside the area) starts another pass; after {@link #MAX_PASSES} passes in a row
 * the pump reports the inflow and waits instead of draining forever.
 *
 * <p>This class holds only positions and counters; the quarry does the world changes and pays for them.
 */
public final class LiquidPocket {
	public static final int MAX_CELLS = 8192;
	public static final int MAP_PER_TICK = 1024;
	public static final int MAX_PASSES = 4;
	/** Ticks the pump waits after giving up on an inflow before it tries again. */
	public static final int INFLOW_BACKOFF = 600;

	public enum Phase {
		IDLE,
		MAPPING,
		DRAINING
	}

	public static final int NONE = 0;
	public static final int FLOWING = 1;
	public static final int SOURCE = 2;

	/** Reads the world for the flood fill (unit tested with a fake grid). */
	public interface Grid {
		/** {@link #SOURCE}, {@link #FLOWING}, or {@link #NONE} when the position holds no liquid or is not loaded. */
		int liquid(BlockPos pos);
	}

	private Phase phase = Phase.IDLE;
	private BlockPos seed = BlockPos.ZERO;
	private BoundingBox bounds = new BoundingBox(0, 0, 0, 0, 0, 0);
	private final LongArrayFIFOQueue frontier = new LongArrayFIFOQueue();
	private final LongOpenHashSet visited = new LongOpenHashSet();
	private final List<BlockPos> cells = new ArrayList<>();
	private int next;
	private int total;
	private int removed;
	private int passes;
	private boolean truncated;
	private boolean inflow;

	public Phase phase() {
		return this.phase;
	}

	public BlockPos seed() {
		return this.seed;
	}

	/** Cells found in the current pass. */
	public int total() {
		return this.total;
	}

	/** Cells removed in the current pass. */
	public int removed() {
		return this.removed;
	}

	public int passes() {
		return this.passes;
	}

	/** True when the pocket was larger than {@link #MAX_CELLS} (the rest is found by the next pass). */
	public boolean truncated() {
		return this.truncated;
	}

	/** True after {@link #MAX_PASSES} passes in a row still found liquid: it keeps flowing in from outside. */
	public boolean inflow() {
		return this.inflow;
	}

	/** Starts mapping the pocket containing {@code seed}. Keeps the pass count when continuing the same job. */
	public void start(BlockPos seed, BoundingBox bounds, boolean newJob) {
		this.phase = Phase.MAPPING;
		this.seed = seed.immutable();
		this.bounds = bounds;
		this.frontier.clear();
		this.visited.clear();
		this.cells.clear();
		this.next = 0;
		this.total = 0;
		this.removed = 0;
		this.truncated = false;
		if (newJob) {
			this.passes = 0;
			this.inflow = false;
		}

		this.frontier.enqueue(this.seed.asLong());
		this.visited.add(this.seed.asLong());
	}

	/** Forgets the job (upgrade removed, area changed). */
	public void reset() {
		this.phase = Phase.IDLE;
		this.frontier.clear();
		this.visited.clear();
		this.cells.clear();
		this.next = 0;
		this.total = 0;
		this.removed = 0;
		this.passes = 0;
		this.truncated = false;
		this.inflow = false;
	}

	/**
	 * Explores up to {@code budget} cells. Returns true when the map is complete; the cells are then ordered top layer
	 * first (and sources before flowing liquid on each layer) and the phase becomes {@link Phase#DRAINING}.
	 */
	public boolean map(Grid grid, int budget) {
		if (this.phase != Phase.MAPPING) {
			return this.phase == Phase.DRAINING;
		}

		BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
		for (int i = 0; i < budget && !this.frontier.isEmpty(); i++) {
			BlockPos pos = BlockPos.of(this.frontier.dequeueLong());
			if (grid.liquid(pos) == NONE) {
				continue;
			}

			if (this.cells.size() >= MAX_CELLS) {
				this.truncated = true;
				this.frontier.clear();
				break;
			}

			this.cells.add(pos);
			for (Direction d : Direction.values()) {
				n.setWithOffset(pos, d);
				if (this.bounds.isInside(n) && this.visited.add(n.asLong())) {
					this.frontier.enqueue(n.asLong());
				}
			}
		}

		if (!this.frontier.isEmpty()) {
			return false;
		}

		this.cells.sort(Comparator.comparingInt((BlockPos p) -> -p.getY()).thenComparingInt(p -> grid.liquid(p) == SOURCE ? 0 : 1));
		this.total = this.cells.size();
		this.next = 0;
		this.phase = this.total == 0 ? Phase.IDLE : Phase.DRAINING;
		return true;
	}

	/** The next cell to drain, or null when the pass is done. */
	public BlockPos peek() {
		return this.phase == Phase.DRAINING && this.next < this.cells.size() ? this.cells.get(this.next) : null;
	}

	/** The cell from {@link #peek()} was drained (or found already dry). */
	public void advance(boolean drained) {
		this.next++;
		if (drained) {
			this.removed++;
		}
	}

	/**
	 * Ends a pass. Returns true if another pass should map the area again (something was drained and the pass limit
	 * is not reached); otherwise the job is finished or, after {@link #MAX_PASSES}, marked as an inflow.
	 */
	public boolean finishPass() {
		boolean drainedSomething = this.removed > 0;
		this.passes++;
		this.phase = Phase.IDLE;
		this.cells.clear();
		if (drainedSomething && this.passes >= MAX_PASSES) {
			this.inflow = true;
			return false;
		}

		return drainedSomething;
	}

	/** For saving: whether a job is under way (it is mapped again from its seed after loading). */
	public boolean active() {
		return this.phase != Phase.IDLE;
	}

	/** Restores counters after loading; mapping restarts from the seed. */
	public void restore(BlockPos seed, BoundingBox bounds, int passes, boolean inflow) {
		this.start(seed, bounds, true);
		this.passes = passes;
		this.inflow = inflow;
	}
}
