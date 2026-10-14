package com.solarquarry.client.render;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

public class LiquidTankRenderState extends BlockEntityRenderState {
	/** 0..1 */
	public float fill;
	public int color = 0xFF3F76E4;
	public boolean glow;
}
