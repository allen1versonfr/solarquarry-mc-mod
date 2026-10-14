package com.solarquarry.quarry;

/** How a quarry walks through its work volume. */
public enum QuarryMode {
	/** Mines the area layer by layer from the quarry's level down to the target depth. */
	STRIP_MINE("strip_mine"),
	/** Bores a horizontal tunnel (width x height) forward from the quarry, starting at the quarry's level. */
	TUNNEL_BORE("tunnel_bore"),
	/** Clears the area from the quarry's level up to the configured height (top layer first). */
	AREA_CLEAR("area_clear"),
	/** Scans the strip-mine volume and mines ore blocks first, then (unless "ores only") everything else. */
	ORE_PRIORITY("ore_priority");

	private final String id;

	QuarryMode(String id) {
		this.id = id;
	}

	public String id() {
		return this.id;
	}

	public String translationKey() {
		return "solarquarry.mode." + this.id;
	}

	public String descriptionKey() {
		return "solarquarry.mode." + this.id + ".desc";
	}

	/** True for modes that dig downward toward the target depth. */
	public boolean usesTargetDepth() {
		return this == STRIP_MINE || this == ORE_PRIORITY;
	}

	/** True for modes that use the height setting. */
	public boolean usesHeight() {
		return this == TUNNEL_BORE || this == AREA_CLEAR;
	}

	public QuarryMode next() {
		QuarryMode[] values = values();
		return values[(this.ordinal() + 1) % values.length];
	}

	public QuarryMode previous() {
		QuarryMode[] values = values();
		return values[(this.ordinal() + values.length - 1) % values.length];
	}

	public static QuarryMode byOrdinal(int ordinal) {
		QuarryMode[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : STRIP_MINE;
	}

	public static QuarryMode byId(String id) {
		for (QuarryMode mode : values()) {
			if (mode.id.equals(id)) {
				return mode;
			}
		}

		return STRIP_MINE;
	}
}
