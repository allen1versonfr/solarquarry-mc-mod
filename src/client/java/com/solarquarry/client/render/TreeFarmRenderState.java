package com.solarquarry.client.render;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

public class TreeFarmRenderState extends BlockEntityRenderState {
	/** Drone position relative to the machine block. */
	public float x;
	public float y;
	public float z;
	public float rotor;
	public boolean working;
	public float targetX;
	public float targetY;
	public float targetZ;
	public int light;
}
