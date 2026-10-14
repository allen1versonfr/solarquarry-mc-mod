package com.solarquarry.pipe;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * One Sorting Terminal destination: an inventory position and what it should receive. Pure data and logic
 * (items and tags are ids), so the ranking is unit tested without a running game.
 *
 * <ul>
 * <li>{@link Mode#ALLOW}: receives only the listed items and tags (rank class {@link #CLASS_MATCH}).</li>
 * <li>{@link Mode#BLOCK}: receives everything except the listed items and tags (class {@link #CLASS_GENERAL}).</li>
 * <li>{@link Mode#FALLBACK}: receives anything no other destination takes (class {@link #CLASS_FALLBACK}).</li>
 * </ul>
 * Within a class, higher priority wins.
 */
public final class SortRule {
	public static final int MAX_ITEMS = 9;
	public static final int MAX_TAGS = 3;
	public static final int MIN_PRIORITY = -5;
	public static final int MAX_PRIORITY = 5;
	public static final int CLASS_EXCLUDED = 0;
	public static final int CLASS_FALLBACK = 1;
	public static final int CLASS_GENERAL = 2;
	public static final int CLASS_MATCH = 3;

	public enum Mode {
		ALLOW,
		BLOCK,
		FALLBACK;

		public Mode next() {
			return values()[(this.ordinal() + 1) % values().length];
		}

		public static Mode byOrdinal(int ordinal) {
			return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : ALLOW;
		}
	}

	private final long pos;
	private Mode mode = Mode.ALLOW;
	private int priority;
	private final List<String> items = new ArrayList<>();
	private final List<String> tags = new ArrayList<>();

	public SortRule(long pos) {
		this.pos = pos;
	}

	public long pos() {
		return this.pos;
	}

	public Mode mode() {
		return this.mode;
	}

	public void setMode(Mode mode) {
		this.mode = mode == null ? Mode.ALLOW : mode;
	}

	public int priority() {
		return this.priority;
	}

	public void setPriority(int priority) {
		this.priority = Math.clamp(priority, MIN_PRIORITY, MAX_PRIORITY);
	}

	/** Item ids, in slot order; empty strings for empty slots (always {@link #MAX_ITEMS} long). */
	public List<String> items() {
		while (this.items.size() < MAX_ITEMS) {
			this.items.add("");
		}

		return this.items;
	}

	public void setItem(int slot, String id) {
		if (slot >= 0 && slot < MAX_ITEMS) {
			this.items().set(slot, id == null ? "" : id);
		}
	}

	/** Tag ids without '#', always {@link #MAX_TAGS} long. */
	public List<String> tags() {
		while (this.tags.size() < MAX_TAGS) {
			this.tags.add("");
		}

		return this.tags;
	}

	public void setTag(int index, String tag) {
		if (index >= 0 && index < MAX_TAGS) {
			String clean = tag == null ? "" : tag.trim();
			if (clean.startsWith("#")) {
				clean = clean.substring(1);
			}

			this.tags().set(index, clean.length() > 96 ? clean.substring(0, 96) : clean);
		}
	}

	public boolean hasEntries() {
		return this.items().stream().anyMatch(s -> !s.isEmpty()) || this.tags().stream().anyMatch(s -> !s.isEmpty());
	}

	/** Whether the item (by id, and tag lookup) is on this rule's list. */
	public boolean lists(String itemId, Predicate<String> hasTag) {
		for (String id : this.items()) {
			if (!id.isEmpty() && id.equals(itemId)) {
				return true;
			}
		}

		for (String tag : this.tags()) {
			if (!tag.isEmpty() && hasTag.test(tag)) {
				return true;
			}
		}

		return false;
	}

	/** Rank class for an item: {@link #CLASS_MATCH}, {@link #CLASS_GENERAL}, {@link #CLASS_FALLBACK} or {@link #CLASS_EXCLUDED}. */
	public int classify(String itemId, Predicate<String> hasTag) {
		return switch (this.mode) {
			case ALLOW -> this.lists(itemId, hasTag) ? CLASS_MATCH : CLASS_EXCLUDED;
			case BLOCK -> this.lists(itemId, hasTag) ? CLASS_EXCLUDED : CLASS_GENERAL;
			case FALLBACK -> CLASS_FALLBACK;
		};
	}

	/** Single comparable rank: class first, then priority; 0 means excluded. */
	public int rank(String itemId, Predicate<String> hasTag) {
		int cls = this.classify(itemId, hasTag);
		return cls == CLASS_EXCLUDED ? 0 : cls * 100 + (this.priority - MIN_PRIORITY) + 1;
	}
}
