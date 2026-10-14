package com.solarquarry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.solarquarry.SolarQuarry;
import com.solarquarry.blockentity.QuarryBlockEntity;
import com.solarquarry.quarry.MiningPattern;
import com.solarquarry.quarry.QuarryFrame;
import com.solarquarry.quarry.QuarryStatus;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws the quarry's gantry: corner posts, two rails along the mining area, a bridge that follows the
 * current row, a carriage over the current column and a telescoping drill shaft down to the block being mined.
 * Everything is client-side and driven by the synced cursor; the head glides smoothly between updates.
 */
public class QuarryRenderer implements BlockEntityRenderer<QuarryBlockEntity, QuarryRenderState> {
	private static final Identifier FRAME = SolarQuarry.id("textures/entity/quarry_frame.png");
	private static final Identifier STEEL = SolarQuarry.id("textures/entity/quarry_steel.png");
	private static final Identifier DRILL = SolarQuarry.id("textures/entity/quarry_drill.png");
	private static final float HEAD_SPEED = 14.0F;

	public QuarryRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public QuarryRenderState createRenderState() {
		return new QuarryRenderState();
	}

	@Override
	public void extractRenderState(
		QuarryBlockEntity be, QuarryRenderState state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
	) {
		BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPosition, breakProgress);
		state.status = be.getStatus();
		state.forward = be.getAreaDirection();
		MiningPattern pattern = be.pattern();
		QuarryFrame frame = new QuarryFrame(pattern);
		state.frame = frame.structure();
		state.lMin = frame.lMin();
		state.lMax = frame.lMax();
		state.fMin = frame.fMin();
		state.fMax = frame.fMax();
		state.gantryY = frame.gantryY();
		state.showGantry = be.isFrameVisible();

		BlockPos origin = be.getBlockPos();
		BlockPos target;
		BlockPos last = be.getLastTarget();
		if (!state.showGantry || state.status == QuarryStatus.COMPLETE || state.status == QuarryStatus.IDLE) {
			// Parked over the first cell.
			target = be.toWorld(pattern.cell(0), pattern).above(Math.max(0, pattern.maxDy() - pattern.cell(0).dy()));
		} else if (last != null) {
			target = last;
		} else {
			target = be.cursorPos();
		}

		float tx = target.getX() - origin.getX() + 0.5F;
		float ty = target.getY() - origin.getY();
		float tz = target.getZ() - origin.getZ() + 0.5F;
		long now = System.nanoTime();
		float dt = be.clientLastFrameNanos == 0 ? 0.0F : Math.min(0.1F, (now - be.clientLastFrameNanos) / 1.0E9F);
		be.clientLastFrameNanos = now;

		if (Float.isNaN(be.clientHeadX)) {
			be.clientHeadX = tx;
			be.clientHeadY = ty;
			be.clientHeadZ = tz;
		} else {
			float dx = tx - be.clientHeadX;
			float dy = ty - be.clientHeadY;
			float dz = tz - be.clientHeadZ;
			float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
			float step = HEAD_SPEED * dt;
			if (dist > 40.0F || dist <= step) {
				be.clientHeadX = tx;
				be.clientHeadY = ty;
				be.clientHeadZ = tz;
			} else if (dist > 0.0F) {
				be.clientHeadX += dx / dist * step;
				be.clientHeadY += dy / dist * step;
				be.clientHeadZ += dz / dist * step;
			}
		}

		if (state.status.isWorking()) {
			be.clientDrillAngle = (be.clientDrillAngle + dt * 720.0F) % 360.0F;
		}

		state.headX = be.clientHeadX;
		state.headY = be.clientHeadY;
		state.headZ = be.clientHeadZ;
		state.drillAngle = be.clientDrillAngle;
		state.rotorAngle = be.clientDrillAngle * 0.5F;

		Level level = be.getLevel();
		if (level != null) {
			BlockPos headPos = BlockPos.containing(origin.getX() + state.headX, origin.getY() + state.headY, origin.getZ() + state.headZ);
			state.headLight = LightCoordsUtil.getLightCoords(level, headPos.above());
			state.frameLight = LightCoordsUtil.getLightCoords(level, origin.above((int) state.gantryY));
		} else {
			state.headLight = LightCoordsUtil.FULL_BRIGHT;
			state.frameLight = LightCoordsUtil.FULL_BRIGHT;
		}
	}

	@Override
	public void submit(QuarryRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		this.submitRotor(state, poseStack, collector);
		if (!state.showGantry) {
			return;
		}

		Direction forward = state.forward;
		Direction lateral = forward.getClockWise();
		// Area bounds in local (lateral, forward) block units measured from the quarry block center.
		float lMin = state.lMin;
		float lMax = state.lMax;
		float fMin = state.fMin;
		float fMax = state.fMax;
		float gy = state.gantryY;

		// Head position in local coordinates.
		float hl = (state.headX - 0.5F) * lateral.getStepX() + (state.headZ - 0.5F) * lateral.getStepZ();
		float hf = (state.headX - 0.5F) * forward.getStepX() + (state.headZ - 0.5F) * forward.getStepZ();
		float headTop = state.headY + 1.0F;
		int frameLight = state.frameLight;
		int headLight = state.headLight;

		// Posts, side rails and end beams: the same boxes the collision uses (QuarryFrame).
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(FRAME), (pose, vc) -> {
			for (QuarryFrame.Box box : state.frame) {
				this.localBox(pose, vc, forward, lateral, box.l0(), box.l1(), box.y0(), box.y1(), box.f0(), box.f1(), frameLight);
			}
		});

		// Steel joints where the posts meet the rails, base plates and mid-height braces.
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(STEEL), (pose, vc) -> {
			for (QuarryFrame.Box post : state.frame) {
				if (post.part() != QuarryFrame.Part.POST) {
					continue;
				}

				float l = (post.l0() + post.l1()) * 0.5F;
				float f = (post.f0() + post.f1()) * 0.5F;
				this.localBox(pose, vc, forward, lateral, l - 0.28F, l + 0.28F, gy - 0.2F, gy + 0.42F, f - 0.28F, f + 0.28F, frameLight);
				this.localBox(pose, vc, forward, lateral, l - 0.3F, l + 0.3F, 0.0F, 0.08F, f - 0.3F, f + 0.3F, frameLight);
				this.localBox(pose, vc, forward, lateral, l - 0.2F, l + 0.2F, gy * 0.5F - 0.1F, gy * 0.5F + 0.1F, f - 0.2F, f + 0.2F, frameLight);
			}
		});

		// Status lights on top of the four posts (full bright, same colour as the machine's lamp).
		int postLamp = state.status.color() | 0xFF000000;
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(DRILL), (pose, vc) -> {
			for (QuarryFrame.Box post : state.frame) {
				if (post.part() == QuarryFrame.Part.POST) {
					float l = (post.l0() + post.l1()) * 0.5F;
					float f = (post.f0() + post.f1()) * 0.5F;
					this.localBox(pose, vc, forward, lateral, l - 0.1F, l + 0.1F, gy + 0.42F, gy + 0.6F, f - 0.1F, f + 0.1F, postLamp, LightCoordsUtil.FULL_BRIGHT);
				}
			}
		});

		RenderType steelType = RenderTypes.entityCutout(STEEL);
		collector.submitCustomGeometry(poseStack, steelType, (pose, vc) -> {
			// Bridge spanning the area at the current row.
			this.localBox(pose, vc, forward, lateral, lMin - 0.1F, lMax + 0.1F, gy + 0.3F, gy + 0.6F, hf - 0.22F, hf + 0.22F, frameLight);
			// Carriage riding on the bridge.
			this.localBox(pose, vc, forward, lateral, hl - 0.4F, hl + 0.4F, gy + 0.15F, gy + 0.85F, hf - 0.4F, hf + 0.4F, frameLight);
			// Telescoping shaft down to the working block.
			float shaftBottom = Math.min(gy, headTop + 0.55F);
			if (shaftBottom < gy + 0.15F) {
				this.localBox(pose, vc, forward, lateral, hl - 0.16F, hl + 0.16F, shaftBottom, gy + 0.15F, hf - 0.16F, hf + 0.16F, headLight);
				// Thicker outer sleeve on the upper part of the shaft.
				float sleeveBottom = Math.max(shaftBottom, gy - 2.0F);
				this.localBox(pose, vc, forward, lateral, hl - 0.22F, hl + 0.22F, sleeveBottom, gy + 0.15F, hf - 0.22F, hf + 0.22F, frameLight);
			}
		});

		// Status beacon on the carriage (full bright so it reads as a light).
		int lampColor = state.status.color() | 0xFF000000;
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(DRILL), (pose, vc) ->
			this.localBox(pose, vc, forward, lateral, hl - 0.12F, hl + 0.12F, gy + 0.85F, gy + 1.05F, hf - 0.12F, hf + 0.12F, lampColor, LightCoordsUtil.FULL_BRIGHT)
		);

		// Spinning drill bit.
		poseStack.pushPose();
		float hx = 0.5F + lateral.getStepX() * hl + forward.getStepX() * hf;
		float hz = 0.5F + lateral.getStepZ() * hl + forward.getStepZ() * hf;
		poseStack.translate(hx, headTop + 0.05F, hz);
		poseStack.mulPose(Axis.YP.rotationDegrees(state.drillAngle));
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(DRILL), (pose, vc) -> {
			BoxBuilder.box(pose, vc, -0.3F, 0.25F, -0.3F, 0.3F, 0.55F, 0.3F, -1, headLight);
			BoxBuilder.box(pose, vc, -0.2F, 0.05F, -0.2F, 0.2F, 0.25F, 0.2F, -1, headLight);
			BoxBuilder.box(pose, vc, -0.08F, -0.15F, -0.08F, 0.08F, 0.05F, 0.08F, -1, headLight);
			BoxBuilder.box(pose, vc, -0.42F, 0.3F, -0.06F, 0.42F, 0.4F, 0.06F, -1, headLight);
		});
		poseStack.popPose();
	}

	/** Spinning cooling rotor on top of the machine. */
	private void submitRotor(QuarryRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
		poseStack.pushPose();
		poseStack.translate(0.5F, 1.0F, 0.5F);
		poseStack.mulPose(Axis.YP.rotationDegrees(state.rotorAngle));
		int light = state.lightCoords;
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(STEEL), (pose, vc) -> {
			BoxBuilder.box(pose, vc, -0.08F, 0.0F, -0.08F, 0.08F, 0.14F, 0.08F, -1, light);
			BoxBuilder.box(pose, vc, -0.34F, 0.08F, -0.05F, 0.34F, 0.12F, 0.05F, -1, light);
			BoxBuilder.box(pose, vc, -0.05F, 0.08F, -0.34F, 0.05F, 0.12F, 0.34F, -1, light);
		});
		poseStack.popPose();
	}

	private void localBox(PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer vc, Direction forward, Direction lateral,
		float l0, float l1, float y0, float y1, float f0, float f1, int light) {
		this.localBox(pose, vc, forward, lateral, l0, l1, y0, y1, f0, f1, -1, light);
	}

	private void localBox(PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer vc, Direction forward, Direction lateral,
		float l0, float l1, float y0, float y1, float f0, float f1, int color, int light) {
		float ax = 0.5F + lateral.getStepX() * l0 + forward.getStepX() * f0;
		float az = 0.5F + lateral.getStepZ() * l0 + forward.getStepZ() * f0;
		float bx = 0.5F + lateral.getStepX() * l1 + forward.getStepX() * f1;
		float bz = 0.5F + lateral.getStepZ() * l1 + forward.getStepZ() * f1;
		BoxBuilder.tiledBox(pose, vc, Math.min(ax, bx), y0, Math.min(az, bz), Math.max(ax, bx), y1, Math.max(az, bz), color, light);
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 256;
	}
}
