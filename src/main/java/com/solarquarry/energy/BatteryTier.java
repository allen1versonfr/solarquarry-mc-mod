package com.solarquarry.energy;

public enum BatteryTier {
	BASIC("basic", 250_000L, 256),
	ADVANCED("advanced", 1_200_000L, 1_024),
	ELITE("elite", 6_000_000L, 4_096);

	private final String id;
	private final long capacity;
	private final long maxTransfer;

	BatteryTier(String id, long capacity, long maxTransfer) {
		this.id = id;
		this.capacity = capacity;
		this.maxTransfer = maxTransfer;
	}

	public String id() {
		return this.id;
	}

	public long capacity() {
		return this.capacity;
	}

	/** Maximum energy per tick this battery can receive or deliver. */
	public long maxTransfer() {
		return this.maxTransfer;
	}
}
