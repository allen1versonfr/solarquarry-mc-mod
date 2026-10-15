package com.solarquarry.quarry;

import com.solarquarry.blockentity.QuarryBlockEntity;
import com.solarquarry.trader.ShopGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Quarry security drone (Security Upgrade). It hovers near its quarry, flies at hostile mobs inside the defence radius
 * and zaps them. It is never saved with the chunk: the quarry deploys drones when needed, so a reload can never
 * duplicate them, and a drone whose quarry is gone, unpowered or without the upgrade switches itself off.
 */
public class SecurityDrone extends Mob {
	public static final double SPEED = 0.35;
	public static final float DAMAGE = 5.0F;
	public static final int ATTACK_INTERVAL = 15;
	private @Nullable BlockPos home;
	private @Nullable LivingEntity target;
	private int attackCooldown;

	public SecurityDrone(EntityType<? extends SecurityDrone> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 14.0).add(Attributes.MOVEMENT_SPEED, 0.4).add(Attributes.ARMOR, 4.0);
	}

	public void setHome(BlockPos home) {
		this.home = home.immutable();
	}

	public @Nullable BlockPos getHome() {
		return this.home;
	}

	/** Hostile mobs only: never players, pets, villagers, the trader's guard or neutral mobs (same rule as the guard). */
	public static boolean isTarget(LivingEntity entity) {
		return ShopGuard.isThreat(entity) && !(entity instanceof SecurityDrone);
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.home == null || !(level.getBlockEntity(this.home) instanceof QuarryBlockEntity quarry) || !quarry.ownsDrone(this)) {
			this.discard();
			return;
		}

		if (this.attackCooldown > 0) {
			this.attackCooldown--;
		}

		if (this.target == null || !this.target.isAlive() || !this.inRadius(this.target.position()) || this.tickCount % 20 == 0) {
			this.target = this.findTarget(level);
		}

		Vec3 goal;
		if (this.target != null) {
			goal = this.target.position().add(0, this.target.getBbHeight() + 0.6, 0);
			if (this.distanceToSqr(this.target) < 6.25 && this.attackCooldown == 0 && quarry.payForDroneAttack()) {
				this.attackCooldown = ATTACK_INTERVAL;
				this.target.hurtServer(level, this.damageSources().mobAttack(this), DAMAGE);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.target.getX(), this.target.getY() + this.target.getBbHeight() * 0.6, this.target.getZ(),
					8, 0.2, 0.2, 0.2, 0.05);
				level.playSound(null, this.blockPosition(), SoundEvents.BEE_STING, SoundSource.NEUTRAL, 0.6F, 1.6F);
			}
		} else {
			// Patrol: hover over the quarry, circling slowly.
			double angle = (this.tickCount + this.getId() * 37) * 0.05;
			goal = Vec3.atCenterOf(this.home).add(Math.cos(angle) * 2.5, 3.0, Math.sin(angle) * 2.5);
		}

		Vec3 delta = goal.subtract(this.position());
		double distance = delta.length();
		Vec3 motion = distance < 0.2 ? Vec3.ZERO : delta.scale(Math.min(SPEED, distance) / distance);
		this.setDeltaMovement(this.getDeltaMovement().scale(0.5).add(motion.scale(0.5)));
		if (motion.lengthSqr() > 1.0E-4) {
			this.setYRot((float) (Math.atan2(motion.z, motion.x) * 180.0 / Math.PI) - 90.0F);
			this.yBodyRot = this.getYRot();
		}
	}

	@Override
	public void travel(Vec3 input) {
		this.move(net.minecraft.world.entity.MoverType.SELF, this.getDeltaMovement());
	}

	private boolean inRadius(Vec3 pos) {
		return this.home != null && pos.distanceToSqr(Vec3.atCenterOf(this.home)) <= QuarryBlockEntity.SECURITY_RADIUS * QuarryBlockEntity.SECURITY_RADIUS;
	}

	private @Nullable LivingEntity findTarget(ServerLevel level) {
		if (this.home == null) {
			return null;
		}

		AABB area = new AABB(this.home).inflate(QuarryBlockEntity.SECURITY_RADIUS);
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area, SecurityDrone::isTarget)) {
			if (!this.inRadius(entity.position())) {
				continue;
			}

			double d = this.distanceToSqr(entity);
			if (d < bestDistance) {
				bestDistance = d;
				best = entity;
			}
		}

		return best;
	}

	public @Nullable LivingEntity getTargetMob() {
		return this.target;
	}
}
