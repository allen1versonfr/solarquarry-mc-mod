package com.solarquarry.pipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class SortRuleTest {
	private static final Predicate<String> ORE_TAGS = Set.of("c:ores", "c:ores/iron")::contains;
	private static final Predicate<String> NO_TAGS = tag -> false;

	@Test
	void allowMatchesListedItemsAndTagsOnly() {
		SortRule rule = new SortRule(1L);
		rule.setItem(0, "minecraft:raw_iron");
		rule.setTag(0, "#c:ores");
		assertEquals(SortRule.CLASS_MATCH, rule.classify("minecraft:raw_iron", NO_TAGS));
		assertEquals(SortRule.CLASS_MATCH, rule.classify("minecraft:iron_ore", ORE_TAGS));
		assertEquals(SortRule.CLASS_EXCLUDED, rule.classify("minecraft:dirt", NO_TAGS));
		assertEquals("c:ores", rule.tags().get(0), "a leading # is removed");
	}

	@Test
	void blockAcceptsEverythingExceptListed() {
		SortRule rule = new SortRule(1L);
		rule.setMode(SortRule.Mode.BLOCK);
		rule.setItem(3, "minecraft:cobblestone");
		assertEquals(SortRule.CLASS_EXCLUDED, rule.classify("minecraft:cobblestone", NO_TAGS));
		assertEquals(SortRule.CLASS_GENERAL, rule.classify("minecraft:dirt", NO_TAGS));
	}

	@Test
	void fallbackTakesAnything() {
		SortRule rule = new SortRule(1L);
		rule.setMode(SortRule.Mode.FALLBACK);
		rule.setItem(0, "minecraft:dirt");
		assertEquals(SortRule.CLASS_FALLBACK, rule.classify("minecraft:dirt", NO_TAGS));
		assertEquals(SortRule.CLASS_FALLBACK, rule.classify("minecraft:diamond", NO_TAGS));
	}

	@Test
	void classBeatsPriority() {
		SortRule listed = new SortRule(1L);
		listed.setItem(0, "minecraft:raw_iron");
		listed.setPriority(-5);
		SortRule general = new SortRule(2L);
		general.setMode(SortRule.Mode.BLOCK);
		general.setPriority(5);
		SortRule fallback = new SortRule(3L);
		fallback.setMode(SortRule.Mode.FALLBACK);
		fallback.setPriority(5);
		int a = listed.rank("minecraft:raw_iron", NO_TAGS);
		int b = general.rank("minecraft:raw_iron", NO_TAGS);
		int c = fallback.rank("minecraft:raw_iron", NO_TAGS);
		assertTrue(a > b && b > c && c > 0, a + " > " + b + " > " + c);
	}

	@Test
	void priorityOrdersWithinAClassAndIsClamped() {
		SortRule low = new SortRule(1L);
		low.setItem(0, "minecraft:raw_iron");
		SortRule high = new SortRule(2L);
		high.setItem(0, "minecraft:raw_iron");
		high.setPriority(99);
		assertEquals(SortRule.MAX_PRIORITY, high.priority());
		assertTrue(high.rank("minecraft:raw_iron", NO_TAGS) > low.rank("minecraft:raw_iron", NO_TAGS));
		assertEquals(0, low.rank("minecraft:dirt", NO_TAGS), "excluded items rank 0");
	}

	@Test
	void modesCycle() {
		assertEquals(SortRule.Mode.BLOCK, SortRule.Mode.ALLOW.next());
		assertEquals(SortRule.Mode.FALLBACK, SortRule.Mode.BLOCK.next());
		assertEquals(SortRule.Mode.ALLOW, SortRule.Mode.FALLBACK.next());
		assertEquals(SortRule.Mode.ALLOW, SortRule.Mode.byOrdinal(42));
	}
}
