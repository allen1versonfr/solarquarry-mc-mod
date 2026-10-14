package com.solarquarry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Emits textured axis-aligned boxes; long boxes are split into unit segments so textures tile instead of stretching. */
public final class BoxBuilder {
	private BoxBuilder() {
	}

	public static void tiledBox(PoseStack.Pose pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int color, int light) {
		float dx = x1 - x0;
		float dy = y1 - y0;
		float dz = z1 - z0;
		if (dx >= dy && dx >= dz && dx > 1.0F) {
			for (float s = x0; s < x1 - 1.0E-4F; s += 1.0F) {
				box(pose, vc, s, y0, z0, Math.min(x1, s + 1.0F), y1, z1, color, light);
			}
		} else if (dz >= dy && dz > 1.0F) {
			for (float s = z0; s < z1 - 1.0E-4F; s += 1.0F) {
				box(pose, vc, x0, y0, s, x1, y1, Math.min(z1, s + 1.0F), color, light);
			}
		} else if (dy > 1.0F) {
			for (float s = y0; s < y1 - 1.0E-4F; s += 1.0F) {
				box(pose, vc, x0, s, z0, x1, Math.min(y1, s + 1.0F), z1, color, light);
			}
		} else {
			box(pose, vc, x0, y0, z0, x1, y1, z1, color, light);
		}
	}

	public static void box(PoseStack.Pose pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int color, int light) {
		// UVs follow the face size so thin parts sample a matching strip of the texture.
		float ux = Math.min(1.0F, x1 - x0);
		float uy = Math.min(1.0F, y1 - y0);
		float uz = Math.min(1.0F, z1 - z0);
		// down
		quad(pose, vc, color, light, 0, -1, 0,
			x0, y0, z1, 0, uz, x0, y0, z0, 0, 0, x1, y0, z0, ux, 0, x1, y0, z1, ux, uz);
		// up
		quad(pose, vc, color, light, 0, 1, 0,
			x0, y1, z0, 0, 0, x0, y1, z1, 0, uz, x1, y1, z1, ux, uz, x1, y1, z0, ux, 0);
		// north (-z)
		quad(pose, vc, color, light, 0, 0, -1,
			x1, y1, z0, 0, 0, x1, y0, z0, 0, uy, x0, y0, z0, ux, uy, x0, y1, z0, ux, 0);
		// south (+z)
		quad(pose, vc, color, light, 0, 0, 1,
			x0, y1, z1, 0, 0, x0, y0, z1, 0, uy, x1, y0, z1, ux, uy, x1, y1, z1, ux, 0);
		// west (-x)
		quad(pose, vc, color, light, -1, 0, 0,
			x0, y1, z0, 0, 0, x0, y0, z0, 0, uy, x0, y0, z1, uz, uy, x0, y1, z1, uz, 0);
		// east (+x)
		quad(pose, vc, color, light, 1, 0, 0,
			x1, y1, z1, 0, 0, x1, y0, z1, 0, uy, x1, y0, z0, uz, uy, x1, y1, z0, uz, 0);
	}

	private static void quad(
		PoseStack.Pose pose, VertexConsumer vc, int color, int light, float nx, float ny, float nz,
		float ax, float ay, float az, float au, float av,
		float bx, float by, float bz, float bu, float bv,
		float cx, float cy, float cz, float cu, float cv,
		float dx, float dy, float dz, float du, float dv
	) {
		vertex(pose, vc, ax, ay, az, au, av, color, light, nx, ny, nz);
		vertex(pose, vc, bx, by, bz, bu, bv, color, light, nx, ny, nz);
		vertex(pose, vc, cx, cy, cz, cu, cv, color, light, nx, ny, nz);
		vertex(pose, vc, dx, dy, dz, du, dv, color, light, nx, ny, nz);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float u, float v, int color, int light, float nx, float ny, float nz) {
		vc.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
	}
}
