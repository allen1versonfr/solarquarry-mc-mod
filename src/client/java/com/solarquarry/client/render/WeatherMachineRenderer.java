package com.solarquarry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.solarquarry.SolarQuarry;
import com.solarquarry.weather.WeatherMachineBlockEntity;
import com.solarquarry.weather.WeatherOperation;
import com.solarquarry.weather.WeatherStage;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Weather Control Machine above its base: four capacitor banks, a tower with an energy core, two counter-rotating
 * control rings, four antenna arms and, while it operates, an energy column into the sky. Everything is driven by the
 * synced stage: dim and slow on standby, capacitors lighting one by one while charging, rings swinging into line during
 * calibration, fast and bright while operating, red and slowing down when power runs out, fading during shutdown.
 * The geometry is a few dozen boxes per frame; no per-tick server data beyond the stage.
 */
public class WeatherMachineRenderer implements BlockEntityRenderer<WeatherMachineBlockEntity, WeatherMachineRenderState> {
	private static final Identifier STEEL = SolarQuarry.id("textures/entity/quarry_steel.png");
	private static final Identifier LAMP = SolarQuarry.id("textures/entity/quarry_drill.png");

	public WeatherMachineRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public WeatherMachineRenderState createRenderState() {
		return new WeatherMachineRenderState();
	}

	@Override
	public void extractRenderState(WeatherMachineBlockEntity be, WeatherMachineRenderState state, float partialTicks, Vec3 cameraPosition,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPosition, breakProgress);
		state.stage = be.getStage().ordinal();
		state.operation = be.getOperation().ordinal();
		int ticks = be.getStage().ticks();
		state.progress = ticks == 0 ? 0.0F : Math.min(1.0F, (be.getStageTicks() + partialTicks) / ticks);
		state.time = (be.getLevel() == null ? 0 : be.getLevel().getGameTime() % 24_000L) + partialTicks;
		state.light = be.getLevel() == null ? LightCoordsUtil.FULL_BRIGHT : LightCoordsUtil.getLightCoords(be.getLevel(), be.getBlockPos().above());
	}

	private static int argb(float r, float g, float b) {
		return 0xFF000000 | ((int) (Math.clamp(r, 0, 1) * 255) << 16) | ((int) (Math.clamp(g, 0, 1) * 255) << 8) | (int) (Math.clamp(b, 0, 1) * 255);
	}

	@Override
	public void submit(WeatherMachineRenderState s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		WeatherStage stage = WeatherStage.byOrdinal(s.stage);
		WeatherOperation op = WeatherOperation.byOrdinal(s.operation);
		int light = s.light;
		// How energised the machine is: drives brightness and rotation speed.
		float power = switch (stage) {
			case STANDBY -> 0.15F;
			case POWER_INTAKE -> 0.2F + 0.3F * s.progress;
			case CORE_CHARGING -> 0.5F + 0.3F * s.progress;
			case CALIBRATION, SYNCHRONIZATION -> 0.85F;
			case OPERATION, STABILIZATION -> 1.0F;
			case SHUTDOWN -> 0.8F * (1.0F - s.progress);
		};
		boolean storm = op == WeatherOperation.THUNDER;
		boolean temporal = op.isTime();
		float pulse = 0.75F + 0.25F * (float) Math.sin(s.time * (0.1F + power * 0.5F));
		int core = temporal ? argb(0.55F * pulse * power + 0.2F, 0.75F * pulse * power + 0.2F, 1.0F * pulse)
			: storm ? argb(0.75F * pulse, 0.65F * pulse, 1.0F * pulse * power + 0.2F)
			: argb(1.0F * pulse * power + 0.2F, 0.8F * pulse * power + 0.15F, 0.25F * power);
		float spin = s.time * (2.0F + power * 14.0F);

		// Tower and capacitor banks on the base.
		collector.submitCustomGeometry(pose, RenderTypes.entityCutout(STEEL), (p, vc) -> {
			BoxBuilder.box(p, vc, 0.3F, 1.0F, 0.3F, 0.7F, 2.6F, 0.7F, 0xFF3A3F46, light);
			BoxBuilder.box(p, vc, 0.25F, 2.6F, 0.25F, 0.75F, 2.75F, 0.75F, 0xFF23272C, light);
			for (float[] c : new float[][] {{-0.15F, -0.15F}, {0.95F, -0.15F}, {-0.15F, 0.95F}, {0.95F, 0.95F}}) {
				BoxBuilder.box(p, vc, c[0], 1.0F, c[1], c[0] + 0.2F, 1.7F, c[1] + 0.2F, 0xFF2B2D31, light);
			}
		});
		// Capacitors light one after another while charging.
		int lit = switch (stage) {
			case STANDBY, SHUTDOWN -> stage == WeatherStage.SHUTDOWN && s.progress < 0.5F ? 4 : 0;
			case POWER_INTAKE -> (int) (s.progress * 2.99F);
			case CORE_CHARGING -> 2 + (int) (s.progress * 2.99F);
			default -> 4;
		};
		final int litCount = Math.min(4, lit);
		collector.submitCustomGeometry(pose, RenderTypes.entityCutout(LAMP), (p, vc) -> {
			float[][] caps = {{-0.15F, -0.15F}, {0.95F, -0.15F}, {0.95F, 0.95F}, {-0.15F, 0.95F}};
			for (int i = 0; i < 4; i++) {
				int color = i < litCount ? (storm ? 0xFFB9A8FF : 0xFFFFD34A) : 0xFF3A3F46;
				BoxBuilder.box(p, vc, caps[i][0] + 0.04F, 1.7F, caps[i][1] + 0.04F, caps[i][0] + 0.16F, 1.78F, caps[i][1] + 0.16F, color,
					i < litCount ? LightCoordsUtil.FULL_BRIGHT : light);
			}

			// Energy core.
			BoxBuilder.box(p, vc, 0.36F, 2.0F, 0.36F, 0.64F, 2.5F, 0.64F, core, LightCoordsUtil.FULL_BRIGHT);
		});
		// Two control rings, counter-rotating; during calibration they swing from tilted into level.
		float tilt = switch (stage) {
			case STANDBY, POWER_INTAKE, CORE_CHARGING -> 35.0F;
			case CALIBRATION -> 35.0F * (1.0F - s.progress);
			case SHUTDOWN -> 35.0F * s.progress;
			default -> 0.0F;
		};
		for (int ring = 0; ring < 2; ring++) {
			pose.pushPose();
			pose.translate(0.5F, 2.2F + ring * 0.35F, 0.5F);
			pose.mulPose(Axis.YP.rotationDegrees(ring == 0 ? spin : -spin * 1.3F));
			pose.mulPose(Axis.XP.rotationDegrees(ring == 0 ? tilt : -tilt));
			float r = ring == 0 ? 1.15F : 0.85F;
			int ringColor = stage == WeatherStage.STANDBY ? 0xFF4B5058 : ring == 0 ? 0xFF5B636E : 0xFFE3A23A;
			collector.submitCustomGeometry(pose, RenderTypes.entityCutout(STEEL), (p, vc) -> {
				for (int seg = 0; seg < 12; seg++) {
					double a = Math.PI * 2 * seg / 12;
					float x = (float) Math.cos(a) * r;
					float z = (float) Math.sin(a) * r;
					BoxBuilder.box(p, vc, x - 0.12F, -0.04F, z - 0.12F, x + 0.12F, 0.04F, z + 0.12F, ringColor, light);
				}
			});
			pose.popPose();
		}

		// Antenna arms: fold out with power.
		float reach = 0.35F + 0.5F * Math.min(1.0F, power);
		pose.pushPose();
		pose.translate(0.5F, 2.75F, 0.5F);
		pose.mulPose(Axis.YP.rotationDegrees(-spin * 0.3F));
		collector.submitCustomGeometry(pose, RenderTypes.entityCutout(STEEL), (p, vc) -> {
			BoxBuilder.box(p, vc, -0.04F, 0.0F, -0.04F, 0.04F, 0.9F, 0.04F, 0xFF5B636E, light);
			for (int i = 0; i < 4; i++) {
				double a = Math.PI / 2 * i;
				float x = (float) Math.cos(a) * reach;
				float z = (float) Math.sin(a) * reach;
				BoxBuilder.box(p, vc, Math.min(0, x) - 0.03F, 0.5F, Math.min(0, z) - 0.03F, Math.max(0, x) + 0.03F, 0.56F, Math.max(0, z) + 0.03F, 0xFF3A3F46, light);
				BoxBuilder.box(p, vc, x - 0.03F, 0.5F, z - 0.03F, x + 0.03F, 0.9F, z + 0.03F, 0xFF3A3F46, light);
			}
		});
		pose.popPose();
		// Energy column while operating (taller and brighter for storms; pale blue for time).
		if (stage == WeatherStage.OPERATION || stage == WeatherStage.STABILIZATION || stage == WeatherStage.SYNCHRONIZATION) {
			float height = stage == WeatherStage.SYNCHRONIZATION ? 6.0F * s.progress : storm ? 48.0F : 32.0F;
			float w = storm ? 0.09F : 0.06F;
			int beam = temporal ? 0xFF9AD7FF : storm ? 0xFFC9B8FF : 0xFFFFE07A;
			collector.submitCustomGeometry(pose, RenderTypes.entityCutout(LAMP), (p, vc) ->
				BoxBuilder.tiledBox(p, vc, 0.5F - w, 3.65F, 0.5F - w, 0.5F + w, 3.65F + height, 0.5F + w, beam, LightCoordsUtil.FULL_BRIGHT));
		}
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 160;
	}
}
