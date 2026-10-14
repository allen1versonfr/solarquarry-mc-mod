package com.solarquarry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.solarquarry.blockentity.ItemPipeBlockEntity;
import com.solarquarry.pipe.TravelingItem;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Renders items gliding through pipes, interpolated client-side from occasional server snapshots. */
public class ItemPipeRenderer implements BlockEntityRenderer<ItemPipeBlockEntity, ItemPipeRenderState> {
	private static final float FACE = 0.42F;
	private final ItemModelResolver itemModelResolver;

	public ItemPipeRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModelResolver = context.itemModelResolver();
	}

	@Override
	public ItemPipeRenderState createRenderState() {
		return new ItemPipeRenderState();
	}

	@Override
	public void extractRenderState(
		ItemPipeBlockEntity be, ItemPipeRenderState state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
	) {
		BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPosition, breakProgress);
		Level level = be.getLevel();
		List<TravelingItem> items = be.getItems();
		while (state.entries.size() < items.size()) {
			state.entries.add(new ItemPipeRenderState.Entry());
		}

		while (state.entries.size() > items.size()) {
			state.entries.remove(state.entries.size() - 1);
		}

		if (level == null) {
			state.entries.clear();
			return;
		}

		long now = level.getGameTime();
		if (be.clientSnapshotTime == 0L) {
			be.clientSnapshotTime = now;
		}

		float elapsed = ((now - be.clientSnapshotTime) + partialTicks) * be.tier().step();
		int seed = (int) be.getBlockPos().asLong();
		for (int i = 0; i < items.size(); i++) {
			TravelingItem item = items.get(i);
			ItemPipeRenderState.Entry entry = state.entries.get(i);
			float t = item.waiting ? item.ticks : Math.min(ItemPipeBlockEntity.TRAVEL_TICKS, item.ticks + elapsed);
			float center = ItemPipeBlockEntity.CENTER_TICKS;
			Direction dir;
			float along;
			if (t <= center || item.to == null) {
				dir = item.from;
				along = FACE * (1.0F - Math.min(1.0F, t / center));
			} else {
				dir = item.to;
				along = FACE * ((t - center) / (ItemPipeBlockEntity.TRAVEL_TICKS - center));
			}

			entry.x = 0.5F + dir.getStepX() * along;
			entry.y = 0.5F + dir.getStepY() * along;
			entry.z = 0.5F + dir.getStepZ() * along;
			entry.spin = (now + partialTicks + i * 37) * 4.0F % 360.0F;
			this.itemModelResolver.updateForTopItem(entry.item, item.stack, ItemDisplayContext.FIXED, level, null, seed + i);
		}
	}

	@Override
	public void submit(ItemPipeRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		for (ItemPipeRenderState.Entry entry : state.entries) {
			if (entry.item.isEmpty()) {
				continue;
			}

			poseStack.pushPose();
			poseStack.translate(entry.x, entry.y - 0.08F, entry.z);
			poseStack.mulPose(Axis.YP.rotationDegrees(entry.spin));
			poseStack.scale(0.3F, 0.3F, 0.3F);
			entry.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
	}

	@Override
	public int getViewDistance() {
		return 48;
	}
}
