package com.solarquarry.turret;

/**
 * Laser Turret tiers: range (blocks), damage per shot, ticks between shots, energy per shot, energy buffer and the
 * most it accepts per tick. Higher tiers reach further and hit harder but cost much more to run.
 */
public enum TurretTier {
	BASIC("basic", 12, 4.0F, 20, 150, 20_000, 256),
	ADVANCED("advanced", 18, 7.0F, 14, 400, 60_000, 512),
	ELITE("elite", 24, 12.0F, 10, 1_000, 150_000, 1_024);

	private final String id;
	private final int range;
	private final float damage;
	private final int cooldown;
	private final long shotCost;
	private final long capacity;
	private final long maxInput;

	TurretTier(String id, int range, float damage, int cooldown, long shotCost, long capacity, long maxInput) {
		this.id = id;
		this.range = range;
		this.damage = damage;
		this.cooldown = cooldown;
		this.shotCost = shotCost;
		this.capacity = capacity;
		this.maxInput = maxInput;
	}

	public String id() {
		return this.id;
	}

	public int range() {
		return this.range;
	}

	public float damage() {
		return this.damage;
	}

	public int cooldown() {
		return this.cooldown;
	}

	public long shotCost() {
		return this.shotCost;
	}

	public long capacity() {
		return this.capacity;
	}

	public long maxInput() {
		return this.maxInput;
	}

	/** Damage per second at full power. */
	public float dps() {
		return this.damage * 20.0F / this.cooldown;
	}

	/** Energy per second while firing continuously. */
	public long powerWhileFiring() {
		return this.shotCost * 20L / this.cooldown;
	}

	public static TurretTier byOrdinal(int ordinal) {
		TurretTier[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : BASIC;
	}
}
