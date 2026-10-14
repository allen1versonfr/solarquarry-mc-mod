package com.solarquarry.client.render;

import com.solarquarry.quarry.QuarryStatus;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

public class QuarryRenderState extends BlockEntityRenderState {
	public QuarryStatus status = QuarryStatus.IDLE;
	public Direction forward = Direction.SOUTH;
	public int areaSize = 16;
	/** Head position relative to the quarry block origin (block-space, smoothed). */
	public float headX;
	public float headY;
	public float headZ;
	public float drillAngle;
	public float rotorAngle;
	public int headLight;
	public int frameLight;
	public boolean showGantry;
}
