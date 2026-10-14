package com.solarquarry.energy;

/**
 * Solar panel tiers. Higher tiers are not just bigger numbers: they cope better with bad weather and the elite
 * tier keeps a trickle of output at night, so each upgrade changes how a base is powered.
 */
public enum SolarTier {
	BASIC("basic", 8, 2_000, 0.0F, 0.4F, 0.2F),
	ADVANCED("advanced", 40, 9_000, 0.0F, 0.6F, 0.35F),
	ELITE("elite", 180, 40_000, 0.12F, 0.8F, 0.5F);

	private final String id;
	private final int peakGeneration;
	private final long bufferCapacity;
	private final float nightFactor;
	private final float rainFactor;
	private final float stormFactor;

	SolarTier(String id, int peakGeneration, long bufferCapacity, float nightFactor, float rainFactor, float stormFactor) {
		this.id = id;
		this.peakGeneration = peakGeneration;
		this.bufferCapacity = bufferCapacity;
		this.nightFactor = nightFactor;
		this.rainFactor = rainFactor;
		this.stormFactor = stormFactor;
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

	/** Fraction of peak output kept at night (0 = none). */
	public float nightFactor() {
		return this.nightFactor;
	}

	/** Output multiplier while it rains on the panel. */
	public float rainFactor() {
		return this.rainFactor;
	}

	/** Output multiplier during thunderstorms. */
	public float stormFactor() {
		return this.stormFactor;
	}

	/**
	 * Pure generation formula (unit tested).
	 * @param sun 0..1 sun height factor (1 at noon, 0 below the horizon)
	 * @param skyVisible whether the panel sees the sky
	 */
	public int generation(float sun, boolean skyVisible, boolean raining, boolean thundering) {
		if (!skyVisible) {
			return 0;
		}

		float factor;
		if (sun < 0.05F) {
			factor = this.nightFactor;
		} else {
			factor = Math.max(sun, this.nightFactor);
		}

		if (thundering) {
			factor *= this.stormFactor;
		} else if (raining) {
			factor *= this.rainFactor;
		}

		if (factor <= 0.0F) {
			return 0;
		}

		return Math.max(1, Math.round(this.peakGeneration * Math.min(1.0F, factor)));
	}
}
