package com.solarquarry.boots;

import com.solarquarry.registry.ModGear;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;

/**
 * Rocket Boots tiers: armour material, energy capacity, launch speed (vanilla jump is 0.42) and the share of fall
 * damage the boots absorb (paid with energy).
 */
public enum RocketTier {
	LEATHER("leather", 20_000, 0.70, 0.40),
	IRON("iron", 50_000, 0.85, 0.60),
	DIAMOND("diamond", 120_000, 1.00, 0.80),
	HELIONITE("helionite", 300_000, 1.20, 1.00);

	/** Energy per rocket jump. */
	public static final long JUMP_COST = 400;
	/** Energy per point of fall damage absorbed. */
	public static final long FALL_COST_PER_POINT = 100;
	/** Ticks between two rocket jumps. */
	public static final int COOLDOWN = 10;

	private final String id;
	private final long capacity;
	private final double launch;
	private final double fallProtection;

	RocketTier(String id, long capacity, double launch, double fallProtection) {
		this.id = id;
		this.capacity = capacity;
		this.launch = launch;
		this.fallProtection = fallProtection;
	}

	public String id() {
		return this.id;
	}

	public long capacity() {
		return this.capacity;
	}

	public double launch() {
		return this.launch;
	}

	public double fallProtection() {
		return this.fallProtection;
	}

	public ArmorMaterial material() {
		return switch (this) {
			case LEATHER -> ArmorMaterials.LEATHER;
			case IRON -> ArmorMaterials.IRON;
			case DIAMOND -> ArmorMaterials.DIAMOND;
			case HELIONITE -> ModGear.HELIONITE_ARMOR;
		};
	}

	/** Approximate jump height in blocks for a launch speed (gravity 0.08, drag 0.98 per tick). */
	public static double jumpHeight(double launch) {
		double v = launch;
		double y = 0;
		while (v > 0) {
			y += v;
			v = (v - 0.08) * 0.98;
		}

		return y;
	}

	/** Fall damage left after the boots absorb their share, limited by the energy available. */
	public static int absorbedDamage(int damage, double protection, long energy) {
		int wanted = (int) Math.floor(damage * protection);
		int affordable = (int) Math.min(wanted, energy / FALL_COST_PER_POINT);
		return Math.max(0, affordable);
	}
}
