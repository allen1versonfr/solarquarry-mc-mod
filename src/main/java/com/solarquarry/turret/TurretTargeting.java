package com.solarquarry.turret;

import com.solarquarry.trader.ShopGuard;
import com.solarquarry.trader.ShopTrader;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;

/** Who a Laser Turret may shoot. Shared by crafted turrets and the ones found in structures. */
public final class TurretTargeting {
	public enum Mode {
		/** Every hostile mob, creepers included (they are shot before they get close). */
		ALL_HOSTILE,
		/** Hostile mobs except creepers, so nothing explodes next to the base. */
		NO_CREEPERS;

		public Mode next() {
			return values()[(this.ordinal() + 1) % values().length];
		}

		public static Mode byOrdinal(int ordinal) {
			Mode[] values = values();
			return ordinal >= 0 && ordinal < values.length ? values[ordinal] : ALL_HOSTILE;
		}
	}

	private TurretTargeting() {
	}

	/**
	 * Hostile mobs only: never players, villagers and other NPCs, the Mechanic or his guard, pets and other owned
	 * creatures, or neutral mobs (endermen, piglins, wolves...), angry or not.
	 */
	public static boolean isTarget(LivingEntity entity, Mode mode) {
		if (!entity.isAlive() || entity instanceof Player || entity instanceof Npc || entity instanceof ShopTrader || entity instanceof ShopGuard) {
			return false;
		}

		if (entity instanceof OwnableEntity owned && owned.getOwnerReference() != null) {
			return false;
		}

		if (!(entity instanceof Enemy) || entity instanceof NeutralMob) {
			return false;
		}

		return !(mode == Mode.NO_CREEPERS && entity instanceof Creeper);
	}
}
