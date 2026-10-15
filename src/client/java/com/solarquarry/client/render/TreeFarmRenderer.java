package com.solarquarry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.solarquarry.SolarQuarry;
import com.solarquarry.treefarm.TreeFarmBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The forestry drone: a small quad-rotor that flies from its dock on the machine to the block it is working on,
 * hovers there and shows a cutting beam while harvesting.
 */
public class TreeFarmRenderer implements BlockEntityRenderer<TreeFarmBlockEntity, TreeFarmRenderState> {
	private static final Identifier STEEL = SolarQuarry.id("textures/entity/quarry_steel.png");
	private static final Identifier LAMP = SolarQuarry.id("textures/entity/quarry_drill.png");
	private static final float SPEED = 8.0F;

	public TreeFarmRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public TreeFarmRenderState createRenderState() {
		return new TreeFarmRenderState();
	}

	@Override
	public void extractRenderState(TreeFarmBlockEntity be, TreeFarmRenderState state, float partialTicks, Vec3 cameraPosition,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPosition, breakProgress);
		BlockPos origin = be.getBlockPos();
		BlockPos target = be.getDroneTarget();
		boolean working = target != null && be.getStatus() != TreeFarmBlockEntity.Status.IDLE && be.getStatus() != TreeFarmBlockEntity.Status.NO_POWER;
		float tx = working ? target.getX() - origin.getX() + 0.5F : 0.5F;
		float ty = working ? target.getY() - origin.getY() + 1.6F : 1.35F;
		float tz = working ? target.getZ() - origin.getZ() + 0.5F : 0.5F;
		long now = System.nanoTime();
		float dt = be.clientLastNanos == 0 ? 0.0F : Math.min(0.1F, (now - be.clientLastNanos) / 1.0E9F);
		be.clientLastNanos = now;
		if (Float.isNaN(be.clientDroneX)) {
			be.clientDroneX = tx;
			be.clientDroneY = ty;
			be.clientDroneZ = tz;
		} else {
			float dx = tx - be.clientDroneX;
			float dy = ty - be.clientDroneY;
			float dz = tz - be.clientDroneZ;
			float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
			float step = SPEED * dt;
			if (dist > 48.0F || dist <= step) {
				be.clientDroneX = tx;
				be.clientDroneY = ty;
				be.clientDroneZ = tz;
			} else if (dist > 0.0F) {
				be.clientDroneX += dx / dist * step;
				be.clientDroneY += dy / dist * step;
				be.clientDroneZ += dz / dist * step;
			}
		}

		state.x = be.clientDroneX;
		state.y = be.clientDroneY + (float) Math.sin(now / 3.0E8) * 0.05F;
		state.z = be.clientDroneZ;
		state.rotor = (now / 1_000_000L % 360L) * 3.0F;
		state.working = working;
		state.targetX = tx;
		state.targetY = ty - 1.1F;
		state.targetZ = tz;
		state.light = be.getLevel() == null ? LightCoordsUtil.FULL_BRIGHT
			: LightCoordsUtil.getLightCoords(be.getLevel(), BlockPos.containing(origin.getX() + state.x, origin.getY() + state.y, origin.getZ() + state.z));
	}

	@Override
	public void submit(TreeFarmRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		int light = state.light;
		poseStack.pushPose();
		poseStack.translate(state.x, state.y, state.z);
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(STEEL), (pose, vc) -> {
			BoxBuilder.box(pose, vc, -0.18F, -0.08F, -0.18F, 0.18F, 0.06F, 0.18F, -1, light);
			BoxBuilder.box(pose, vc, -0.4F, 0.0F, -0.04F, 0.4F, 0.04F, 0.04F, -1, light);
			BoxBuilder.box(pose, vc, -0.04F, 0.0F, -0.4F, 0.04F, 0.04F, 0.4F, -1, light);
			BoxBuilder.box(pose, vc, -0.05F, -0.2F, -0.05F, 0.05F, -0.08F, 0.05F, 0xFF3A3F46, light);
		});
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(LAMP), (pose, vc) ->
			BoxBuilder.box(pose, vc, -0.06F, 0.06F, -0.06F, 0.06F, 0.12F, 0.06F, state.working ? 0xFF4CE06A : 0xFF8A9199, LightCoordsUtil.FULL_BRIGHT));
		for (float[] arm : new float[][] {{0.4F, 0.0F}, {-0.4F, 0.0F}, {0.0F, 0.4F}, {0.0F, -0.4F}}) {
			poseStack.pushPose();
			poseStack.translate(arm[0], 0.06F, arm[1]);
			poseStack.mulPose(Axis.YP.rotationDegrees(state.rotor));
			collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(LAMP), (pose, vc) ->
				BoxBuilder.box(pose, vc, -0.16F, 0.0F, -0.025F, 0.16F, 0.015F, 0.025F, 0xFF2A2E33, light));
			poseStack.popPose();
		}

		poseStack.popPose();
		if (state.working) {
			// Cutting beam from the drone down to the block.
			float x = state.x;
			float z = state.z;
			float top = state.y - 0.2F;
			float bottom = Math.min(top, state.targetY + 0.5F);
			collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(LAMP), (pose, vc) ->
				BoxBuilder.box(pose, vc, x - 0.02F, bottom, z - 0.02F, x + 0.02F, top, z + 0.02F, 0xFF7CFF9A, LightCoordsUtil.FULL_BRIGHT));
		}
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 128;
	}
}
