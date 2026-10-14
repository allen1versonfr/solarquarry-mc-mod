package com.solarquarry.energy;

public enum SolarTier {
	BASIC("basic", 10, 2_000),
	ADVANCED("advanced", 45, 9_000),
	ELITE("elite", 200, 40_000);

	private final String id;
	private final int peakGeneration;
	private final long bufferCapacity;

	SolarTier(String id, int peakGeneration, long bufferCapacity) {
		this.id = id;
		this.peakGeneration = peakGeneration;
		this.bufferCapacity = bufferCapacity;
	}

	public String id() {
		return this.id;
	}

	/** Energy per tick at full, unobstructed noon sunlight. */
	public int peakGeneration() {
		return this.peakGeneration;
	}

	public long bufferCapacity() {
		return this.bufferCapacity;
	}
}
