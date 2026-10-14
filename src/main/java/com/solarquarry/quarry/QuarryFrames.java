package com.solarquarry.quarry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The solid frames of the quarries currently loaded in each level (client and server keep their own copies).
 * Quarries register their frame boxes while the frame is shown and remove them when they stop showing it, are
 * removed or are unloaded. {@link #addCollisions} is called for every moving entity, so it returns quickly when
 * no frame is near.
 */
public final class QuarryFrames {
	/** Frame of one quarry: the solid boxes and their combined bounds (for a quick reject). */
	public record Entry(List<AABB> boxes, AABB bounds) {
	}

	private static final Map<Level, Map<Long, Entry>> FRAMES = Collections.synchronizedMap(new WeakHashMap<>());
	private static volatile int count;

	private QuarryFrames() {
	}

	public static void put(Level level, long key, List<AABB> boxes) {
		if (boxes.isEmpty()) {
			remove(level, key);
			return;
		}

		AABB bounds = boxes.get(0);
		for (AABB box : boxes) {
			bounds = bounds.minmax(box);
		}

		Entry entry = new Entry(List.copyOf(boxes), bounds);
		Map<Long, Entry> map;
		synchronized (FRAMES) {
			map = FRAMES.computeIfAbsent(level, l -> new ConcurrentHashMap<>());
		}

		if (map.put(key, entry) == null) {
			count++;
		}
	}

	public static void remove(Level level, long key) {
		Map<Long, Entry> map = FRAMES.get(level);
		if (map != null && map.remove(key) != null) {
			count = Math.max(0, count - 1);
		}
	}

	public static List<AABB> boxesIntersecting(Level level, AABB area) {
		if (count == 0) {
			return List.of();
		}

		Map<Long, Entry> map = FRAMES.get(level);
		if (map == null || map.isEmpty()) {
			return List.of();
		}

		List<AABB> hits = null;
		for (Entry entry : map.values()) {
			if (!entry.bounds().intersects(area)) {
				continue;
			}

			for (AABB box : entry.boxes()) {
				if (box.intersects(area)) {
					if (hits == null) {
						hits = new ArrayList<>(4);
					}

					hits.add(box);
				}
			}
		}

		return hits == null ? List.of() : hits;
	}

	/** Adds the frame boxes touching {@code area} to an entity's collision list (used by the movement hook). */
	public static List<VoxelShape> addCollisions(Level level, AABB area, List<VoxelShape> original) {
		List<AABB> hits = boxesIntersecting(level, area);
		if (hits.isEmpty()) {
			return original;
		}

		List<VoxelShape> combined = new ArrayList<>(original.size() + hits.size());
		combined.addAll(original);
		for (AABB box : hits) {
			combined.add(Shapes.create(box));
		}

		return combined;
	}
}
