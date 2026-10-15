package com.solarquarry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.solarquarry.SolarQuarry;
import com.solarquarry.quarry.SecurityDrone;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;

/** Security drone: armoured quad-rotor with a red sensor eye and a stun prong underneath. */
public class SecurityDroneRenderer extends EntityRenderer<SecurityDrone, SecurityDroneRenderer.State> {
	private static final Identifier STEEL = SolarQuarry.id("textures/entity/quarry_steel.png");
	private static final Identifier LAMP = SolarQuarry.id("textures/entity/quarry_drill.png");

	public static class State extends EntityRenderState {
		public float yaw;
		public float rotor;
		public boolean hurt;
	}

	public SecurityDroneRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.3F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(SecurityDrone entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yaw = entity.getYRot();
		state.rotor = (entity.tickCount + partialTicks) * 60.0F;
		state.hurt = entity.hurtTime > 0;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		int light = state.lightCoords;
		int body = state.hurt ? 0xFFFF8080 : -1;
		poseStack.pushPose();
		poseStack.translate(0.0F, 0.2F, 0.0F);
		poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(STEEL), (pose, vc) -> {
			BoxBuilder.box(pose, vc, -0.22F, -0.1F, -0.22F, 0.22F, 0.08F, 0.22F, body, light);
			BoxBuilder.box(pose, vc, -0.45F, 0.0F, -0.04F, 0.45F, 0.04F, 0.04F, body, light);
			BoxBuilder.box(pose, vc, -0.04F, 0.0F, -0.45F, 0.04F, 0.04F, 0.45F, body, light);
			BoxBuilder.box(pose, vc, -0.03F, -0.28F, 0.1F, 0.03F, -0.1F, 0.16F, 0xFF3A3F46, light);
		});
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(LAMP), (pose, vc) -> {
			BoxBuilder.box(pose, vc, -0.07F, -0.05F, 0.22F, 0.07F, 0.05F, 0.25F, 0xFFFF3030, LightCoordsUtil.FULL_BRIGHT);
			BoxBuilder.box(pose, vc, -0.02F, -0.32F, 0.11F, 0.02F, -0.28F, 0.15F, 0xFF7CC8FF, LightCoordsUtil.FULL_BRIGHT);
		});
		for (float[] arm : new float[][] {{0.45F, 0.0F}, {-0.45F, 0.0F}, {0.0F, 0.45F}, {0.0F, -0.45F}}) {
			poseStack.pushPose();
			poseStack.translate(arm[0], 0.06F, arm[1]);
			poseStack.mulPose(Axis.YP.rotationDegrees(state.rotor));
			collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(LAMP), (pose, vc) ->
				BoxBuilder.box(pose, vc, -0.18F, 0.0F, -0.025F, 0.18F, 0.015F, 0.025F, 0xFF2A2E33, light));
			poseStack.popPose();
		}

		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
