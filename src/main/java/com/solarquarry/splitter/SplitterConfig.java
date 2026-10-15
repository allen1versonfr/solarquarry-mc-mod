package com.solarquarry.splitter;

import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * Faces and mode of a Filtered Splitter, kept apart from the block entity so the rules are unit tested.
 *
 * <ul>
 * <li>Matching items leave through {@link #matchFace()}, every other item through {@link #otherFace()}.</li>
 * <li>Items enter through {@link #inputFace()}, or through any face that is not an output when it is null.</li>
 * <li>The two outputs and the input are always different faces; changing one moves the others out of the way.</li>
 * <li>Whitelist: listed items are "matching". Blacklist: listed items are sent to the other output instead.</li>
 * </ul>
 */
public final class SplitterConfig {
	private Direction matchFace = Direction.NORTH;
	private Direction otherFace = Direction.EAST;
	private @Nullable Direction inputFace;
	private boolean whitelist = true;
	private boolean matchComponents;

	public Direction matchFace() {
		return this.matchFace;
	}

	public Direction otherFace() {
		return this.otherFace;
	}

	public @Nullable Direction inputFace() {
		return this.inputFace;
	}

	public boolean whitelist() {
		return this.whitelist;
	}

	public boolean matchComponents() {
		return this.matchComponents;
	}

	public void setWhitelist(boolean whitelist) {
		this.whitelist = whitelist;
	}

	public void setMatchComponents(boolean matchComponents) {
		this.matchComponents = matchComponents;
	}

	/** Defaults for a newly placed splitter: input from the player's side, matching straight on, others to the right. */
	public void orient(Direction look) {
		this.matchFace = look;
		this.otherFace = look.getAxis().isHorizontal() ? look.getClockWise() : Direction.NORTH;
		this.inputFace = look.getOpposite();
		this.whitelist = true;
		this.matchComponents = false;
	}

	/** Sets all three faces at once (loading saved data); invalid combinations fall back to safe values. */
	public void set(Direction match, Direction other, @Nullable Direction input) {
		this.matchFace = match;
		this.otherFace = other == match ? next(match, match, null) : other;
		this.inputFace = input == this.matchFace || input == this.otherFace ? null : input;
	}

	public void cycleMatch() {
		this.matchFace = next(this.matchFace, this.otherFace, null);
		if (this.inputFace == this.matchFace) {
			this.inputFace = null;
		}
	}

	public void cycleOther() {
		this.otherFace = next(this.otherFace, this.matchFace, null);
		if (this.inputFace == this.otherFace) {
			this.inputFace = null;
		}
	}

	/** Any side, then each face that is not an output, then back to any side. */
	public void cycleInput() {
		Direction[] all = Direction.values();
		int start = this.inputFace == null ? -1 : this.inputFace.ordinal();
		for (int i = start + 1; i < all.length; i++) {
			if (all[i] != this.matchFace && all[i] != this.otherFace) {
				this.inputFace = all[i];
				return;
			}
		}

		this.inputFace = null;
	}

	/** Whether items may be inserted through this face (never through an output). */
	public boolean acceptsFrom(@Nullable Direction side) {
		if (side == null) {
			return true; // hoppers above/below and direct insertion ask with a face; null means "internal"
		}

		if (side == this.matchFace || side == this.otherFace) {
			return false;
		}

		return this.inputFace == null || side == this.inputFace;
	}

	/** Output face for an item that is (or is not) listed in the filter. */
	public Direction outputFor(boolean listed) {
		return listed == this.whitelist ? this.matchFace : this.otherFace;
	}

	private static Direction next(Direction current, Direction avoid, @Nullable Direction avoid2) {
		Direction[] all = Direction.values();
		for (int i = 1; i <= all.length; i++) {
			Direction d = all[(current.ordinal() + i) % all.length];
			if (d != avoid && d != avoid2) {
				return d;
			}
		}

		return current;
	}
}
