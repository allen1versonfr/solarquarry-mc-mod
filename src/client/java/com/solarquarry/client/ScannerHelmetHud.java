package com.solarquarry.client;

import com.solarquarry.SolarQuarry;
import com.solarquarry.item.PoweredItem;
import com.solarquarry.item.ScannerHelmetItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Scanner Helmet display (client only): light at the wearer's feet and what it means for hostile spawning, plus red
 * dust markers on nearby ground dark enough for hostile mobs. The values are the real light levels the client has
 * for those blocks; the dimension's own block-light limit is used. Light is only one spawning rule, so the HUD says
 * "light allows" rather than "mobs will spawn". Markers are refreshed twice a second within a small radius.
 */
public final class ScannerHelmetHud {
	private static final int SAFE = 0xFF59D97A;
	private static final int UNSAFE = 0xFFE5534B;
	private static final DustParticleOptions MARKER = new DustParticleOptions(0xE5534B, 0.8F);

	private ScannerHelmetHud() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, SolarQuarry.id("scanner_helmet"), (g, delta) -> draw(g));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player != null && client.level != null && !client.isPaused() && client.player.tickCount % 10 == 0
				&& ScannerHelmetItem.active(client.player)) {
				markers(client.level, client.player.blockPosition());
			}
		});
	}

	private static boolean lightAllowsSpawn(Level level, BlockPos pos) {
		return ScannerHelmetItem.lightAllowsSpawn(level, pos);
	}

	private static void draw(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || !ScannerHelmetItem.active(mc.player)) {
			return;
		}

		BlockPos feet = mc.player.blockPosition();
		int block = mc.level.getBrightness(LightLayer.BLOCK, feet);
		int sky = mc.level.getBrightness(LightLayer.SKY, feet);
		boolean allows = lightAllowsSpawn(mc.level, feet);
		int x = 6;
		int y = 6;
		g.fill(x - 3, y - 3, x + 150, y + 33, 0x99101418);
		g.fill(x - 3, y - 3, x + 150, y - 2, 0xFFE3A23A);
		g.text(mc.font, Component.translatable("hud.solarquarry.scanner.light", block, sky), x, y, 0xFFE8ECEF, true);
		g.text(mc.font, Component.translatable(allows ? "hud.solarquarry.scanner.unsafe" : "hud.solarquarry.scanner.safe"), x, y + 10, allows ? UNSAFE : SAFE, true);
		long energy = PoweredItem.energy(ScannerHelmetItem.worn(mc.player));
		g.text(mc.font, Component.translatable("hud.solarquarry.scanner.charge", Math.round(100.0 * energy / ScannerHelmetItem.CAPACITY)), x, y + 20, 0xFF9AA3AD, true);
	}

	private static void markers(Level level, BlockPos center) {
		int r = ScannerHelmetItem.RADIUS;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				for (int dy = -3; dy <= 3; dy++) {
					pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					BlockState here = level.getBlockState(pos);
					if (!here.isAir() || !level.getBlockState(pos.above()).isAir()) {
						continue;
					}

					BlockPos below = pos.below();
					BlockState ground = level.getBlockState(below);
					if (!ground.isFaceSturdy(level, below, Direction.UP) || !lightAllowsSpawn(level, pos)) {
						continue;
					}

					level.addParticle(MARKER, pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, 0, 0, 0);
				}
			}
		}
	}
}
