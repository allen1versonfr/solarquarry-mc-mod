package com.solarquarry.storage;

/** Storage crate tiers: one, two or three pages of 54 slots. */
public enum CrateTier {
	BASIC("basic", 1),
	REINFORCED("reinforced", 2),
	ADVANCED("advanced", 3);

	public static final int PAGE_SLOTS = 54;
	private final String id;
	private final int pages;

	CrateTier(String id, int pages) {
		this.id = id;
		this.pages = pages;
	}

	public String id() {
		return this.id;
	}

	public int pages() {
		return this.pages;
	}

	public int slots() {
		return this.pages * PAGE_SLOTS;
	}
}
