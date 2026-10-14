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
	public java.util.List<com.solarquarry.quarry.QuarryFrame.Box> frame = java.util.List.of();
}
