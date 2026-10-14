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
 */
public final class QuarryFrame {
	/** Height of the gantry rails above the highest working layer. */
	public static final float GANTRY_HEIGHT = 3.0F;
	public static final float POST_HALF = 0.15F;
	public static final float RAIL_HALF = 0.2F;
	public static final float RAIL_THICKNESS = 0.3F;

	public enum Part {
		POST,
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

	public QuarryFrame(MiningPattern pattern) {
		this.lMin = pattern.lateralOffset(0) - 0.5F;
		this.lMax = pattern.lateralOffset(pattern.width() - 1) + 0.5F;
		this.fMin = 0.5F;
		this.fMax = pattern.length() + 0.5F;
		this.gantryY = pattern.maxDy() + GANTRY_HEIGHT;
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

	/** The solid parts: four corner posts, two side rails and two end beams. */
	public List<Box> structure() {
		List<Box> boxes = new ArrayList<>(8);
		float top = this.gantryY + RAIL_THICKNESS;
		for (float l : new float[] {this.lMin, this.lMax}) {
			// Near posts sit just inside the first row so they never cut into the machine itself (1-wide tunnels).
			boxes.add(new Box(Part.POST, l - POST_HALF, l + POST_HALF, 0.0F, top, this.fMin, this.fMin + 2 * POST_HALF));
			boxes.add(new Box(Part.POST, l - POST_HALF, l + POST_HALF, 0.0F, top, this.fMax - POST_HALF, this.fMax + POST_HALF));
		}

		boxes.add(new Box(Part.RAIL, this.lMin - RAIL_HALF, this.lMin + RAIL_HALF, this.gantryY, top, this.fMin, this.fMax));
		boxes.add(new Box(Part.RAIL, this.lMax - RAIL_HALF, this.lMax + RAIL_HALF, this.gantryY, top, this.fMin, this.fMax));
		boxes.add(new Box(Part.END_BEAM, this.lMin, this.lMax, this.gantryY, this.gantryY + 0.2F, this.fMin - 0.1F, this.fMin + 0.1F));
		boxes.add(new Box(Part.END_BEAM, this.lMin, this.lMax, this.gantryY, this.gantryY + 0.2F, this.fMax - 0.1F, this.fMax + 0.1F));
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
