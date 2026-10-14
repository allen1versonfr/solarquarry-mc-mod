package com.solarquarry.pipe;

/**
 * Pipe tiers. Items move {@link #step()} travel units per tick through a pipe that is
 * {@link #TRAVEL_UNITS} units long, so a basic pipe takes 10 ticks per block, a reinforced pipe 5 and an
 * advanced pipe 2. Machines hand over up to {@link #batch()} items at a time, and a pipe holds up to
 * {@link #capacity()} stacks in transit.
 */
public enum PipeTier {
	BASIC("basic", 1, 16, 8),
	REINFORCED("reinforced", 2, 32, 12),
	ADVANCED("advanced", 5, 64, 16);

	public static final int TRAVEL_UNITS = 10;
	public static final int CENTER_UNITS = TRAVEL_UNITS / 2;

	private final String id;
	private final int step;
	private final int batch;
	private final int capacity;

	PipeTier(String id, int step, int batch, int capacity) {
		this.id = id;
		this.step = step;
		this.batch = batch;
		this.capacity = capacity;
	}

	public String id() {
		return this.id;
	}

	public int step() {
		return this.step;
	}

	public int batch() {
		return this.batch;
	}

	public int capacity() {
		return this.capacity;
	}

	/** Ticks an item needs to cross one pipe block. */
	public int ticksPerBlock() {
		return (TRAVEL_UNITS + this.step - 1) / this.step;
	}

	/** Blocks per second an item travels. */
	public double blocksPerSecond() {
		return 20.0 / this.ticksPerBlock();
	}
}
