package com.solarquarry.quarry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class QuarryFrameTest {
	private static MiningPattern strip(int width, int length) {
		return MiningPattern.of(QuarryMode.STRIP_MINE, width, length, 3, 64, 0, 319);
	}

	@Test
	void fourPostsTwoRailsTwoEndBeams() {
		List<QuarryFrame.Box> boxes = new QuarryFrame(strip(16, 16)).structure();
		assertEquals(4, boxes.stream().filter(b -> b.part() == QuarryFrame.Part.POST).count());
		assertEquals(2, boxes.stream().filter(b -> b.part() == QuarryFrame.Part.RAIL).count());
		assertEquals(2, boxes.stream().filter(b -> b.part() == QuarryFrame.Part.END_BEAM).count());
	}

	@Test
	void frameEnclosesTheWorkAreaInEveryDirection() {
		BlockPos origin = new BlockPos(0, 64, 0);
		for (MiningPattern pattern : List.of(strip(5, 7), strip(1, 4), strip(2, 2))) {
		for (Direction forward : Direction.Plane.HORIZONTAL) {
			List<AABB> boxes = new QuarryFrame(pattern).worldBoxes(origin, forward);
			AABB bounds = boxes.get(0);
			for (AABB box : boxes) {
				bounds = bounds.minmax(box);
			}

			// Every cell of the work area lies inside the frame's footprint.
			Direction lateral = forward.getClockWise();
			for (int l = 0; l < pattern.width(); l++) {
				for (int f = 1; f <= pattern.length(); f++) {
					BlockPos cell = origin.relative(forward, f).relative(lateral, pattern.lateralOffset(l));
					assertTrue(bounds.contains(cell.getX() + 0.5, 64.5, cell.getZ() + 0.5), forward + " cell " + l + "," + f);
				}
			}

			// The quarry block itself is outside the frame posts (access is preserved).
			for (AABB box : boxes) {
				assertFalse(box.intersects(new AABB(origin)), forward + ": frame overlaps the machine");
			}
		}
		}
	}

	@Test
	void growsWithTheArea() {
		QuarryFrame small = new QuarryFrame(strip(16, 16));
		QuarryFrame large = new QuarryFrame(strip(64, 64));
		assertTrue(large.lMax() - large.lMin() > small.lMax() - small.lMin());
		assertTrue(large.fMax() > small.fMax());
	}

	@Test
	void areaClearRaisesTheGantry() {
		MiningPattern clear = MiningPattern.of(QuarryMode.AREA_CLEAR, 8, 8, 10, 64, 0, 319);
		assertEquals(clear.maxDy() + QuarryFrame.GANTRY_HEIGHT, new QuarryFrame(clear).gantryY());
		assertTrue(new QuarryFrame(clear).gantryY() > new QuarryFrame(strip(8, 8)).gantryY());
	}

	@Test
	void legsReachTheGroundAndTheMachineGetsASupport() {
		QuarryFrame frame = new QuarryFrame(strip(5, 5)).withLegs(new float[] {-4, 0, -2.5F, -96, -3});
		List<QuarryFrame.Box> posts = frame.posts();
		assertEquals(-4.0F, posts.get(0).y0());
		assertEquals(0.0F, posts.get(1).y0());
		assertEquals(-96.0F, posts.get(3).y0());
		assertTrue(frame.structure().stream().anyMatch(b -> b.part() == QuarryFrame.Part.SUPPORT && b.y0() == -3.0F));
		assertTrue(new QuarryFrame(strip(5, 5)).structure().stream().noneMatch(b -> b.part() == QuarryFrame.Part.SUPPORT), "no support when standing on the ground");
	}

	@Test
	void legsStandOutsideTheWorkArea() {
		MiningPattern pattern = strip(5, 5);
		QuarryFrame frame = new QuarryFrame(pattern);
		for (QuarryFrame.Box post : frame.posts()) {
			boolean outsideLaterally = post.l1() <= frame.lMin() || post.l0() >= frame.lMax();
			assertTrue(outsideLaterally, "post " + post + " overlaps the mined columns");
		}
	}
}
