package com.solarquarry.quarry;

import net.minecraft.util.StringRepresentable;

public enum QuarryStatus implements StringRepresentable {
	IDLE("idle", 0xFF8A9199),
	RUNNING("running", 0xFF4CE06A),
	PAUSED("paused", 0xFFE0B341),
	NO_ENERGY("no_energy", 0xFFE04B3A),
	BUFFER_FULL("buffer_full", 0xFFE07A2E),
	BLOCKED("blocked", 0xFFE07A2E),
	FINISHED("finished", 0xFF47A7E0);

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

	public boolean isWorking() {
		return this == RUNNING;
	}

	public static QuarryStatus byOrdinal(int ordinal) {
		QuarryStatus[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : IDLE;
	}
}
