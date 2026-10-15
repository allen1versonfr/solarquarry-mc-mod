package com.solarquarry.client.render;

import com.solarquarry.quarry.QuarryStatus;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

public class QuarryRenderState extends BlockEntityRenderState {
	public QuarryStatus status = QuarryStatus.IDLE;
	public Direction forward = Direction.SOUTH;
	/** Work volume bounds in local (lateral, forward) block units from the quarry block center. */
	public float lMin;
	public float lMax;
	public float fMin;
	public float fMax;
	public float gantryY = 3.0F;
	/** Head position relative to the quarry block origin (block-space, smoothed). */
	public float headX;
	public float headY;
	public float headZ;
	public float drillAngle;
	public float rotorAngle;
	public int headLight;
	public int frameLight;
	public boolean showGantry;
	public float railLeft;
	public float railRight;
	/** Liquid Remover arm: shown when the upgrade is installed; position in local (lateral, forward) units and y. */
	/** Miner Drone position relative to the machine (NaN when it is not out). */
	public float minerX = Float.NaN;
	public float minerY;
	public float minerZ;
	public boolean pump;
	public float pumpL;
	public float pumpF;
	public float pumpY;
	public java.util.List<com.solarquarry.quarry.QuarryFrame.Box> frame = java.util.List.of();
	/** False while the frame is still being raised: only the built parts are drawn, without the gantry. */
	public boolean frameComplete = true;
}
