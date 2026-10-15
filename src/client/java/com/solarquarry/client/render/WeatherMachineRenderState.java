package com.solarquarry.client.render;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/** What the Weather Control Machine renderer draws this frame. */
public class WeatherMachineRenderState extends BlockEntityRenderState {
	public int stage;
	public int operation;
	/** 0..1 through the current stage. */
	public float progress;
	public float time;
	public int light;
}
