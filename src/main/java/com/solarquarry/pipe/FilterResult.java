package com.solarquarry.pipe;

public enum FilterResult {
	/** The item may not enter. */
	BLOCKED,
	/** The item may pass (blacklist filter that does not list it, or a plain pipe). */
	PASS,
	/** The item is explicitly listed by a whitelist filter: routes through here are preferred. */
	PASS_MATCHED
}
