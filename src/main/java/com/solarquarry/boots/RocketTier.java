package com.solarquarry.boots;

import com.solarquarry.registry.ModGear;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;

/**
 * Rocket Boots tiers: armour material, energy capacity, the climb speed their thrusters reach (blocks per tick),
 * the energy they burn per tick of thrust, and the share of fall damage they absorb (paid with energy).
 * Each tier holds more energy, climbs faster and flies longer on a full charge.
 */
public enum RocketTier {
	LEATHER("leather", 20_000, 0.30, 25, 0.40),
	IRON("iron", 50_000, 0.38, 30, 0.60),
	DIAMOND("diamond", 120_000, 0.46, 35, 0.80),
	HELIONITE("helionite", 300_000, 0.58, 40, 1.00);

	/** Energy per point of fall damage absorbed. */
	public static final long FALL_COST_PER_POINT = 100;
	/** Upward acceleration per tick of thrust (gravity is 0.08). */
	public static final double THRUST = 0.16;

	private final String id;
	private final long capacity;
	private final double climb;
	private final long thrustCost;
	private final double fallProtection;

	RocketTier(String id, long capacity, double climb, long thrustCost, double fallProtection) {
		this.id = id;
		this.capacity = capacity;
		this.climb = climb;
		this.thrustCost = thrustCost;
		this.fallProtection = fallProtection;
	}

	public String id() {
		return this.id;
	}

	public long capacity() {
		return this.capacity;
	}

	/** Highest climb speed in blocks per tick. */
	public double climb() {
		return this.climb;
	}

	/** Energy per tick while the thrusters fire. */
	public long thrustCost() {
		return this.thrustCost;
	}

	public double fallProtection() {
		return this.fallProtection;
	}

	/** Seconds of thrust on a full charge. */
	public double flightSeconds() {
		return this.capacity / (double) this.thrustCost / 20.0;
	}

	public ArmorMaterial material() {
		return switch (this) {
			case LEATHER -> ArmorMaterials.LEATHER;
			case IRON -> ArmorMaterials.IRON;
			case DIAMOND -> ArmorMaterials.DIAMOND;
			case HELIONITE -> ModGear.HELIONITE_ARMOR;
		};
	}

	/** Vertical speed after one tick of thrust: accelerates up to the climb speed, never slows a faster rise. */
	public static double thrust(double vy, double climb) {
		if (vy >= climb) {
			return vy;
		}

		return Math.min(climb, vy + THRUST);
	}

	/** Fall damage the boots absorb, limited by their share and the energy available. */
	public static int absorbedDamage(int damage, double protection, long energy) {
		int wanted = (int) Math.floor(damage * protection);
		int affordable = (int) Math.min(wanted, energy / FALL_COST_PER_POINT);
		return Math.max(0, affordable);
	}
}
