package com.solarquarry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solarquarry.SolarQuarry;
import com.solarquarry.fluid.LiquidTankBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Shows the tank's fill level in the window on each side (water blue, lava glowing orange, anything else grey). */
public class LiquidTankRenderer implements BlockEntityRenderer<LiquidTankBlockEntity, LiquidTankRenderState> {
	private static final Identifier GAUGE = SolarQuarry.id("textures/entity/quarry_drill.png");
	private static final float WINDOW_MIN = 4.0F / 16.0F;
	private static final float WINDOW_MAX = 12.0F / 16.0F;
	private static final float BOTTOM = 2.0F / 16.0F;
	private static final float TOP = 14.0F / 16.0F;
	private static final float OUT = 0.003F;

	public LiquidTankRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public LiquidTankRenderState createRenderState() {
		return new LiquidTankRenderState();
	}

	@Override
	public void extractRenderState(
		LiquidTankBlockEntity be, LiquidTankRenderState state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
	) {
		BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPosition, breakProgress);
		state.fill = be.fill();
		Fluid fluid = be.fluid().getFluid();
		state.glow = fluid.is(FluidTags.LAVA);
		state.color = fluid.is(FluidTags.WATER) ? 0xFF3F76E4 : state.glow ? 0xFFFF8A1E : 0xFF9AA4B0;
	}

	@Override
	public void submit(LiquidTankRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.fill <= 0.0F) {
			return;
		}

		float top = BOTTOM + (TOP - BOTTOM) * Math.max(0.03F, state.fill);
		int light = state.glow ? LightCoordsUtil.FULL_BRIGHT : state.lightCoords;
		int color = state.color;
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(GAUGE), (pose, vc) -> {
			BoxBuilder.box(pose, vc, WINDOW_MIN, BOTTOM, -OUT, WINDOW_MAX, top, 1.0F + OUT, color, light);
			BoxBuilder.box(pose, vc, -OUT, BOTTOM, WINDOW_MIN, 1.0F + OUT, top, WINDOW_MAX, color, light);
		});
	}
}
