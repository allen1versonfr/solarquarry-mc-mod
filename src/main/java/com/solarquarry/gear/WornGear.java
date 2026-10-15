package com.solarquarry.gear;

import com.solarquarry.item.PoweredItem;
import com.solarquarry.registry.ModItems;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side effects of worn gear, checked once a second: the Mining Helmet gives Haste I below {@link #MINING_Y};
 * the Night-Vision Goggles give Night Vision while charged and burn {@link #GOGGLES_DRAIN} E per tick worn. Effects
 * last a few seconds and are only refreshed, so taking the gear off ends them shortly after.
 */
public final class WornGear {
	public static final int MINING_Y = 50;
	public static final long GOGGLES_DRAIN = 3;
	public static final long GOGGLES_CAPACITY = 30_000;

	private WornGear() {
	}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			apply(player);
		}
	}

	/** One second of worn-gear effects for one player; returns which applied (bit 1 haste, bit 2 night vision). */
	public static int apply(ServerPlayer player) {
		int applied = 0;
		ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
		if (head.is(ModItems.MINING_HELMET) && player.getY() < MINING_Y) {
			player.addEffect(new MobEffectInstance(MobEffects.HASTE, 60, 0, true, false, true));
			applied |= 1;
		}

		if (head.is(ModItems.NIGHT_VISION_GOGGLES) && !player.isSpectator()) {
			if (PoweredItem.use(head, GOGGLES_DRAIN * 20)) {
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false, true));
				applied |= 2;
			}
		}

		return applied;
	}
}
