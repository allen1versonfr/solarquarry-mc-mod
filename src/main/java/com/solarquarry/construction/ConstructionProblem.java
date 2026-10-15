package com.solarquarry.construction;

/** Why construction is paused or failed (shown in the machine's GUI). */
public enum ConstructionProblem {
	NONE,
	/** A block that is not plants, snow or air sits where the structure goes (never overwritten). */
	OBSTRUCTED,
	/** Not enough energy for the next block. */
	NO_POWER,
	/** The next block is in an unloaded chunk; construction waits instead of loading it. */
	UNLOADED,
	/** The plan could not be laid out (no ground, too steep, out of the world). */
	NO_SITE;

	public static ConstructionProblem byOrdinal(int ordinal) {
		ConstructionProblem[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NONE;
	}
}
