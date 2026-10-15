package com.solarquarry.weather;

/**
 * The machine's lifecycle; every stage is real server state and drives the animations and sounds.
 * <pre>
 * STANDBY -> POWER_INTAKE -> CORE_CHARGING -> CALIBRATION -> SYNCHRONIZATION -> OPERATION
 *        -> STABILIZATION (sustained weather) -> SHUTDOWN -> STANDBY
 * </pre>
 * Power loss during operation or stabilization goes to SHUTDOWN (a controlled stop). Startup stages take
 * {@link #ticks()} each; the energy was paid when the command was accepted.
 */
public enum WeatherStage {
	STANDBY(0),
	POWER_INTAKE(40),
	CORE_CHARGING(40),
	CALIBRATION(40),
	SYNCHRONIZATION(30),
	OPERATION(0),
	STABILIZATION(0),
	SHUTDOWN(40);

	private final int ticks;

	WeatherStage(int ticks) {
		this.ticks = ticks;
	}

	public int ticks() {
		return this.ticks;
	}

	public boolean isStartup() {
		return this == POWER_INTAKE || this == CORE_CHARGING || this == CALIBRATION || this == SYNCHRONIZATION;
	}

	public boolean isActive() {
		return this != STANDBY;
	}

	public static WeatherStage byOrdinal(int ordinal) {
		WeatherStage[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : STANDBY;
	}
}
