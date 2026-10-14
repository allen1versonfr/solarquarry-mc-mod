package com.solarquarry.quarry;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/**
 * Geometry of the quarry's frame, shared by the renderer (client) and the collision hook (both sides), so what you
 * see is exactly what you bump into. Coordinates are local: lateral and forward in block units measured from the
 * quarry block's center, y from the quarry block's bottom face.
 *
 * <p>The four legs stand just outside the work area (so mining never undermines them) and reach down to the ground;
 * a support column holds up the machine itself when it was placed above the ground.
 */
public final class QuarryFrame {
	/** Height of the gantry rails above the highest working layer. */
	public static final float GANTRY_HEIGHT = 3.0F;
	public static final float POST_SIZE = 0.3F;
	public static final float RAIL_HALF = 0.2F;
	public static final float RAIL_THICKNESS = 0.3F;
	/** Longest leg drawn below the quarry's level. */
	public static final int MAX_LEG = 96;

	public enum Part {
		POST,
		SUPPORT,
		RAIL,
		END_BEAM
	}

	/** A box in local (lateral, y, forward) space. */
	public record Box(Part part, float l0, float l1, float y0, float y1, float f0, float f1) {
	}

	private final float lMin;
	private final float lMax;
	private final float fMin;
	private final float fMax;
	private final float gantryY;
	/** Leg bottoms (dy, at most 0) for the 4 posts in {@link #posts()} order, then the support. */
	private float[] legs = new float[0];

	public QuarryFrame(MiningPattern pattern) {
		this.lMin = pattern.lateralOffset(0) - 0.5F;
		this.lMax = pattern.lateralOffset(pattern.width() - 1) + 0.5F;
		this.fMin = 0.5F;
		this.fMax = pattern.length() + 0.5F;
		this.gantryY = pattern.maxDy() + GANTRY_HEIGHT;
	}

	/** Sets where the legs meet the ground (see {@link #posts()} for the order; one more value for the support). */
	public QuarryFrame withLegs(float[] bottoms) {
		this.legs = bottoms == null ? new float[0] : bottoms.clone();
		return this;
	}

	public float lMin() {
		return this.lMin;
	}

	public float lMax() {
		return this.lMax;
	}

	public float fMin() {
		return this.fMin;
	}

	public float fMax() {
		return this.fMax;
	}

	public float gantryY() {
		return this.gantryY;
	}

	/** Lateral centre line of the left and right side rails. */
	public float railLeft() {
		return this.lMin - POST_SIZE * 0.5F;
	}

	public float railRight() {
		return this.lMax + POST_SIZE * 0.5F;
	}

	private float leg(int index) {
		return index < this.legs.length ? Math.min(0.0F, this.legs[index]) : 0.0F;
	}

	/**
	 * The four legs: on the outer side of the two lateral edges, the near pair over the machine's own row and the far
	 * pair just past the last row. None of them stands on a block the quarry will mine.
	 */
	public List<Box> posts() {
		float top = this.gantryY + RAIL_THICKNESS;
		List<Box> posts = new ArrayList<>(4);
		posts.add(new Box(Part.POST, this.lMin - POST_SIZE, this.lMin, this.leg(0), top, this.fMin - POST_SIZE, this.fMin));
		posts.add(new Box(Part.POST, this.lMin - POST_SIZE, this.lMin, this.leg(1), top, this.fMax, this.fMax + POST_SIZE));
		posts.add(new Box(Part.POST, this.lMax, this.lMax + POST_SIZE, this.leg(2), top, this.fMin - POST_SIZE, this.fMin));
		posts.add(new Box(Part.POST, this.lMax, this.lMax + POST_SIZE, this.leg(3), top, this.fMax, this.fMax + POST_SIZE));
		return posts;
	}

	/** The solid parts: legs, the machine's support (when it floats), two side rails and two end beams. */
	public List<Box> structure() {
		List<Box> boxes = new ArrayList<>(this.posts());
		float support = this.leg(4);
		if (support < 0.0F) {
			boxes.add(new Box(Part.SUPPORT, -0.3F, 0.3F, support, 0.0F, -0.3F, 0.3F));
		}

		float top = this.gantryY + RAIL_THICKNESS;
		float left = this.railLeft();
		float right = this.railRight();
		float near = this.fMin - POST_SIZE * 0.5F;
		float far = this.fMax + POST_SIZE * 0.5F;
		boxes.add(new Box(Part.RAIL, left - RAIL_HALF, left + RAIL_HALF, this.gantryY, top, near, far));
		boxes.add(new Box(Part.RAIL, right - RAIL_HALF, right + RAIL_HALF, this.gantryY, top, near, far));
		boxes.add(new Box(Part.END_BEAM, left, right, this.gantryY, this.gantryY + 0.2F, near - 0.1F, near + 0.1F));
		boxes.add(new Box(Part.END_BEAM, left, right, this.gantryY, this.gantryY + 0.2F, far - 0.1F, far + 0.1F));
		return boxes;
	}

	/** World-space box of a local box for a quarry at {@code origin} whose work area extends in {@code forward}. */
	public static AABB toWorld(Box box, BlockPos origin, Direction forward) {
		Direction lateral = forward.getClockWise();
		double ax = origin.getX() + 0.5 + lateral.getStepX() * box.l0() + forward.getStepX() * box.f0();
		double az = origin.getZ() + 0.5 + lateral.getStepZ() * box.l0() + forward.getStepZ() * box.f0();
		double bx = origin.getX() + 0.5 + lateral.getStepX() * box.l1() + forward.getStepX() * box.f1();
		double bz = origin.getZ() + 0.5 + lateral.getStepZ() * box.l1() + forward.getStepZ() * box.f1();
		return new AABB(Math.min(ax, bx), origin.getY() + box.y0(), Math.min(az, bz), Math.max(ax, bx), origin.getY() + box.y1(), Math.max(az, bz));
	}

	/** All solid parts in world space. */
	public List<AABB> worldBoxes(BlockPos origin, Direction forward) {
		List<Box> local = this.structure();
		List<AABB> result = new ArrayList<>(local.size());
		for (Box box : local) {
			result.add(toWorld(box, origin, forward));
		}

		return result;
	}
}
