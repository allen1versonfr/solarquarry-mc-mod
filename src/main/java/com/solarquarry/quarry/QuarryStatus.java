package com.solarquarry.quarry;

import net.minecraft.util.StringRepresentable;

/**
 * Quarry state machine.
 * <pre>
 *   IDLE --start--> RUNNING <--> WAITING_FOR_POWER / OUTPUT_BLOCKED / WAITING_FOR_CHUNK (automatic)
 *   RUNNING/waiting --pause--> PAUSED --resume--> RUNNING
 *   RUNNING --volume done--> COMPLETE --start--> RUNNING (restarts the scan)
 * </pre>
 * The mode (IDLE/RUNNING/PAUSED) is what the player chose; the status is what the machine is doing right now.
 */
public enum QuarryStatus implements StringRepresentable {
	IDLE("idle", 0xFF8A9199),
	RUNNING("running", 0xFF4CE06A),
	SCANNING("scanning", 0xFF4CE0C8),
	PAUSED("paused", 0xFFE0B341),
	WAITING_FOR_POWER("waiting_for_power", 0xFFE04B3A),
	OUTPUT_BLOCKED("output_blocked", 0xFFE07A2E),
	WAITING_FOR_CHUNK("waiting_for_chunk", 0xFFE07A2E),
	COMPLETE("complete", 0xFF47A7E0),
	/** Raising the frame (shared construction framework) before the first block is mined. */
	CONSTRUCTING("constructing", 0xFF4CE0C8);

	private final String name;
	private final int color;

	QuarryStatus(String name, int color) {
		this.name = name;
		this.color = color;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}

	public int color() {
		return this.color;
	}

	public String translationKey() {
		return "solarquarry.status." + this.name;
	}

	/** Explains what the player can do about this state. */
	public String hintKey() {
		return "solarquarry.status." + this.name + ".hint";
	}

	/** True while the drill is actively working (animation and sounds run). */
	public boolean isWorking() {
		return this == RUNNING || this == SCANNING;
	}

	/** True for states that need the player's attention. */
	public boolean isProblem() {
		return this == WAITING_FOR_POWER || this == OUTPUT_BLOCKED || this == WAITING_FOR_CHUNK;
	}

	public static QuarryStatus byOrdinal(int ordinal) {
		QuarryStatus[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : IDLE;
	}

	public static QuarryStatus byName(String name) {
		for (QuarryStatus status : values()) {
			if (status.name.equals(name)) {
				return status;
			}
		}

		return IDLE;
	}
}
