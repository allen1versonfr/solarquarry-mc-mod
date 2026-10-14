package com.solarquarry.util;

import java.util.Locale;

public final class EnergyFormat {
	private EnergyFormat() {
	}

	/** Compact energy string such as "12.5k E" or "1.20M E". */
	public static String format(long energy) {
		if (energy >= 1_000_000_000L) {
			return String.format(Locale.ROOT, "%.2fG E", energy / 1_000_000_000.0);
		}

		if (energy >= 1_000_000L) {
			return String.format(Locale.ROOT, "%.2fM E", energy / 1_000_000.0);
		}

		if (energy >= 10_000L) {
			return String.format(Locale.ROOT, "%.1fk E", energy / 1_000.0);
		}

		return energy + " E";
	}
}
