package com.solarquarry.construction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The shared self-building engine used by the quarry (frame), the Tree Farm, the Kelp Farm and the Mechanic's house.
 *
 * <p>A site holds a resolved plan (world positions and block states, built in list order) or a number of "virtual"
 * steps for structures that are not made of blocks (the quarry frame). Every few ticks it does a bounded amount of work:
 * blocks that are already right are skipped (so resuming after a reload never places anything twice), air and
 * replaceable plants are built over, and anything else stops construction with {@link ConstructionProblem#OBSTRUCTED}
 * at that position: player builds are never overwritten. It never loads chunks. A completed site checks one of its
 * blocks per second and goes back to building when one went missing.
 *
 * <p>All of this runs on the server; clients only see the blocks it places and the progress the machine syncs.
 */
public final class ConstructionSite {
	/** Ticks between work rounds while building or waiting. */
	public static final int DEFAULT_INTERVAL = 4;
	/** Ticks between checks while paused (only the blocking position is looked at). */
	public static final int RETRY_INTERVAL = 40;

	private List<Placement> plan = List.of();
	private int virtualSteps;
	private int cursor;
	private int placed;
	private ConstructionState state = ConstructionState.IDLE;
	private ConstructionProblem problem = ConstructionProblem.NONE;
	private @Nullable BlockPos problemPos;
	private int cooldown;
	private int checkCursor;
	private final int interval;
	private final int stepsPerRound;

	public ConstructionSite() {
		this(DEFAULT_INTERVAL, 1);
	}

	public ConstructionSite(int interval, int stepsPerRound) {
		this.interval = Math.max(1, interval);
		this.stepsPerRound = Math.max(1, stepsPerRound);
	}

	// ---- planning ----

	/** Sets the block plan. An identical plan keeps the progress; a different one starts validating it. */
	public void plan(List<Placement> placements) {
		if (this.virtualSteps == 0 && placements.equals(this.plan) && this.state != ConstructionState.IDLE) {
			return;
		}

		this.plan = List.copyOf(placements);
		this.virtualSteps = 0;
		this.cursor = 0;
		this.checkCursor = 0;
		this.problem = ConstructionProblem.NONE;
		this.problemPos = null;
		this.state = ConstructionState.VALIDATING;
	}

	/**
	 * Sets a plan of {@code steps} steps that place no blocks (the quarry frame is drawn and made solid by the machine).
	 * Changing the size keeps the steps already done, up to the new size.
	 */
	public void planVirtual(int steps) {
		if (!this.plan.isEmpty() || steps != this.virtualSteps || this.state == ConstructionState.IDLE) {
			boolean fresh = this.state == ConstructionState.IDLE || !this.plan.isEmpty();
			this.plan = List.of();
			this.cursor = fresh ? 0 : Math.min(this.cursor, steps);
			this.virtualSteps = Math.max(0, steps);
			this.problem = ConstructionProblem.NONE;
			this.problemPos = null;
			this.state = this.cursor >= this.virtualSteps && this.virtualSteps > 0 ? ConstructionState.COMPLETED : ConstructionState.VALIDATING;
		}
	}

	/** Marks a site that could not be laid out. */
	public void fail(ConstructionProblem why) {
		this.state = ConstructionState.ERROR;
		this.problem = why;
		this.problemPos = null;
	}

	/** Forgets everything (the machine was reset). */
	public void reset() {
		this.plan = List.of();
		this.virtualSteps = 0;
		this.cursor = 0;
		this.placed = 0;
		this.checkCursor = 0;
		this.state = ConstructionState.IDLE;
		this.problem = ConstructionProblem.NONE;
		this.problemPos = null;
	}

	/** Treats the whole plan as built (migration of machines from before the framework existed, tests). */
	public void markComplete() {
		this.cursor = this.totalSteps();
		this.state = this.totalSteps() > 0 ? ConstructionState.COMPLETED : ConstructionState.IDLE;
		this.problem = ConstructionProblem.NONE;
		this.problemPos = null;
	}

	// ---- ticking ----

	/** Advances construction; cheap when idle, complete or waiting. Returns true if anything changed (for syncing). */
	public boolean tick(ServerLevel level, ConstructionHost host) {
		switch (this.state) {
			case IDLE, ERROR -> {
				return false;
			}
			case VALIDATING -> {
				if (this.totalSteps() == 0) {
					this.fail(ConstructionProblem.NO_SITE);
				} else {
					this.state = ConstructionState.BUILDING;
					this.cooldown = 0;
				}

				return true;
			}
			case COMPLETED -> {
				return this.maintain(level);
			}
			default -> {
			}
		}

		if (this.cooldown > 0) {
			this.cooldown--;
			return false;
		}

		ConstructionState before = this.state;
		ConstructionProblem problemBefore = this.problem;
		int cursorBefore = this.cursor;
		this.cooldown = (this.state == ConstructionState.PAUSED ? RETRY_INTERVAL : this.interval) - 1;
		for (int done = 0; done < this.stepsPerRound; ) {
			if (this.cursor >= this.totalSteps()) {
				this.complete(level, host);
				break;
			}

			if (this.virtualSteps > 0) {
				if (!host.payForStep()) {
					this.pause(ConstructionProblem.NO_POWER, null);
					break;
				}

				this.cursor++;
				this.placed++;
				done++;
				this.resume();
				host.onStepDone(level, null);
				continue;
			}

			Placement p = this.plan.get(this.cursor);
			if (!level.isLoaded(p.pos())) {
				this.pause(ConstructionProblem.UNLOADED, p.pos());
				break;
			}

			BlockState existing = level.getBlockState(p.pos());
			if (matches(existing, p.state())) {
				this.cursor++; // already there (resumed after a reload, or built by someone else): never placed twice
				this.resume();
				continue;
			}

			if (level.getBlockEntity(p.pos()) != null || !host.canReplace(existing)) {
				this.pause(ConstructionProblem.OBSTRUCTED, p.pos());
				break;
			}

			if (!host.payForStep()) {
				this.pause(ConstructionProblem.NO_POWER, p.pos());
				break;
			}

			if (!existing.isAir()) {
				level.destroyBlock(p.pos(), false); // grass, flowers, snow: cleared, not dropped
			}

			level.setBlock(p.pos(), p.state(), Block.UPDATE_ALL);
			this.cursor++;
			this.placed++;
			done++;
			this.resume();
			host.onStepDone(level, p.pos());
		}

		return before != this.state || problemBefore != this.problem || cursorBefore != this.cursor;
	}

	private void resume() {
		if (this.state == ConstructionState.PAUSED) {
			this.state = ConstructionState.BUILDING;
		}

		this.problem = ConstructionProblem.NONE;
		this.problemPos = null;
	}

	private void pause(ConstructionProblem why, @Nullable BlockPos at) {
		this.state = ConstructionState.PAUSED;
		this.problem = why;
		this.problemPos = at == null ? null : at.immutable();
	}

	private void complete(ServerLevel level, ConstructionHost host) {
		this.state = ConstructionState.COMPLETED;
		this.problem = ConstructionProblem.NONE;
		this.problemPos = null;
		this.checkCursor = 0;
		host.onCompleted(level);
	}

	/** Completed: checks one block per call every 20 calls and goes back to building when it is gone. */
	private boolean maintain(ServerLevel level) {
		if (this.plan.isEmpty()) {
			return false;
		}

		if (++this.cooldown < 20) {
			return false;
		}

		this.cooldown = 0;
		this.checkCursor = (this.checkCursor + 1) % this.plan.size();
		Placement p = this.plan.get(this.checkCursor);
		if (level.isLoaded(p.pos()) && !matches(level.getBlockState(p.pos()), p.state())) {
			this.cursor = this.checkCursor;
			this.state = ConstructionState.BUILDING;
			return true;
		}

		return false;
	}

	/** Same block (properties such as waterlogging or facing may differ, e.g. after a block update). */
	public static boolean matches(BlockState existing, BlockState wanted) {
		return existing.getBlock() == wanted.getBlock();
	}

	/**
	 * Removes the structure's blocks that are still what this site placed and that {@code owned} accepts (the
	 * machine's own construction blocks, which drop nothing), e.g. when the machine is broken. Player blocks and
	 * anything changed since are left alone. Unloaded positions are skipped.
	 */
	public int dismantle(ServerLevel level, Predicate<BlockState> owned) {
		int removed = 0;
		for (int i = this.plan.size() - 1; i >= 0; i--) {
			Placement p = this.plan.get(i);
			if (!level.isLoaded(p.pos())) {
				continue;
			}

			BlockState existing = level.getBlockState(p.pos());
			if (matches(existing, p.state()) && owned.test(existing)) {
				level.setBlock(p.pos(), existing.getFluidState().isEmpty() ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
					: existing.getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
				removed++;
			}
		}

		return removed;
	}

	// ---- queries ----

	public ConstructionState state() {
		return this.state;
	}

	public ConstructionProblem problem() {
		return this.problem;
	}

	public @Nullable BlockPos problemPos() {
		return this.problemPos;
	}

	public boolean isComplete() {
		return this.state == ConstructionState.COMPLETED;
	}

	/** True while the structure is planned but not finished (building, paused, validating). */
	public boolean isUnderConstruction() {
		return this.state == ConstructionState.VALIDATING || this.state == ConstructionState.BUILDING || this.state == ConstructionState.PAUSED;
	}

	public int totalSteps() {
		return this.virtualSteps > 0 ? this.virtualSteps : this.plan.size();
	}

	public int stepsDone() {
		return Math.min(this.cursor, this.totalSteps());
	}

	/** Steps actually performed (blocks placed or virtual steps paid) since the site was created; never repeated. */
	public int placedCount() {
		return this.placed;
	}

	/** 0-100. */
	public int percent() {
		int total = this.totalSteps();
		return total == 0 ? 0 : this.stepsDone() * 100 / total;
	}

	public List<Placement> plan() {
		return this.plan;
	}

	/** The block being worked on next (for animations), or null. */
	public @Nullable BlockPos currentTarget() {
		if (this.problemPos != null) {
			return this.problemPos;
		}

		return this.cursor < this.plan.size() ? this.plan.get(this.cursor).pos() : null;
	}

	// ---- persistence ----

	public void save(ValueOutput output) {
		ValueOutput out = output.child("Construction");
		out.putInt("State", this.state.ordinal());
		out.putInt("Problem", this.problem.ordinal());
		out.putInt("Cursor", this.cursor);
		out.putInt("Placed", this.placed);
		out.putInt("Virtual", this.virtualSteps);
		if (this.problemPos != null) {
			out.putLong("ProblemPos", this.problemPos.asLong());
		}

		if (!this.plan.isEmpty()) {
			out.store("Plan", Placement.LIST_CODEC, this.plan);
		}
	}

	/** Loads the site; returns false if the data has no construction section (saved before the framework existed). */
	public boolean load(ValueInput input) {
		var in = input.child("Construction");
		if (in.isEmpty()) {
			return false;
		}

		ValueInput c = in.get();
		this.plan = c.read("Plan", Placement.LIST_CODEC).map(List::copyOf).orElse(List.of());
		this.virtualSteps = Math.max(0, c.getIntOr("Virtual", 0));
		this.cursor = Math.max(0, Math.min(c.getIntOr("Cursor", 0), this.totalSteps()));
		this.placed = Math.max(0, c.getIntOr("Placed", 0));
		this.state = ConstructionState.byOrdinal(c.getIntOr("State", 0));
		this.problem = ConstructionProblem.byOrdinal(c.getIntOr("Problem", 0));
		this.problemPos = c.getLong("ProblemPos").map(BlockPos::of).orElse(null);
		if (this.state == ConstructionState.BUILDING || this.state == ConstructionState.PAUSED) {
			this.cooldown = 0; // re-check right away after loading
		}

		return true;
	}

	/** Positions of the plan (for tests and for keeping farm work areas clear of the structure). */
	public List<BlockPos> positions() {
		List<BlockPos> list = new ArrayList<>(this.plan.size());
		for (Placement p : this.plan) {
			list.add(p.pos());
		}

		return list;
	}
}
