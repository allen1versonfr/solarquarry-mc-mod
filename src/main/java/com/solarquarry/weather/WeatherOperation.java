package com.solarquarry.weather;

/**
 * What the Weather Control Machine can do, with its energy costs in the mod's own scale (E). The master plan gave
 * FE targets (2M / 3M / 10M / 1M / 2.5M to start; 10k / 25k / 100k / 15k per tick to maintain); Solar Quarry's scale
 * is smaller (an Elite Solar Panel makes 180 E/t, an Elite Battery holds 6M E), so start costs are divided by 5 and
 * running costs by 50, keeping the ratios: a thunderstorm costs five times clearing the sky and needs a solar farm of
 * about a dozen Elite panels to hold.
 */
public enum WeatherOperation {
	NONE(0, 0),
	CLEAR(400_000, 200),
	RAIN(600_000, 500),
	THUNDER(2_000_000, 2_000),
	TIME_PRESET(200_000, 0),
	TIME_GRADUAL(500_000, 300);

	private final long startCost;
	private final long upkeep;

	WeatherOperation(long startCost, long upkeep) {
		this.startCost = startCost;
		this.upkeep = upkeep;
	}

	/** Energy taken once when the operation starts. */
	public long startCost() {
		return this.startCost;
	}

	/** Energy per tick while it is maintained (sustained weather, a gradual time change). */
	public long upkeep() {
		return this.upkeep;
	}

	public boolean isWeather() {
		return this == CLEAR || this == RAIN || this == THUNDER;
	}

	public boolean isTime() {
		return this == TIME_PRESET || this == TIME_GRADUAL;
	}

	public static WeatherOperation byOrdinal(int ordinal) {
		WeatherOperation[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NONE;
	}

	/** Total energy for an operation: start plus upkeep over the given ticks. */
	public long totalCost(long ticks) {
		return this.startCost + this.upkeep * Math.max(0, ticks);
	}
}
