package com.solarquarry.blockentity;

/**
 * Rolling count of events per second over the last {@code seconds} completed seconds. The owner calls
 * {@link #record} for each event and {@link #advance} once per second (every 20 ticks). Pure logic, unit tested.
 */
public final class RateWindow {
	private final int[] buckets;
	private int current;
	private int index;
	private int filled;

	public RateWindow(int seconds) {
		this.buckets = new int[Math.max(1, seconds)];
	}

	public void record(int count) {
		if (count > 0) {
			this.current += count;
		}
	}

	/** Closes the current second. */
	public void advance() {
		this.buckets[this.index] = this.current;
		this.index = (this.index + 1) % this.buckets.length;
		this.filled = Math.min(this.buckets.length, this.filled + 1);
		this.current = 0;
	}

	/** Average per second over the completed seconds (0 before the first second has completed). */
	public double perSecond() {
		if (this.filled == 0) {
			return 0.0;
		}

		long sum = 0;
		for (int i = 0; i < this.filled; i++) {
			sum += this.buckets[(this.index - 1 - i + this.buckets.length) % this.buckets.length];
		}

		return (double) sum / this.filled;
	}

	public void reset() {
		java.util.Arrays.fill(this.buckets, 0);
		this.current = 0;
		this.index = 0;
		this.filled = 0;
	}
}
