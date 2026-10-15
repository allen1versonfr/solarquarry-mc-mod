package com.solarquarry;

import com.solarquarry.registry.ModBlocks;
import com.solarquarry.turret.LaserTurretBlockEntity;
import com.solarquarry.turret.TurretTargeting;
import com.solarquarry.turret.TurretTier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueOutput;

/** Self-tests for Checkpoint J: Laser Turrets shoot hostile mobs only, pay per shot, respect range and sight. */
final class SelfTestTurrets {
	static final BlockPos TURRET = new BlockPos(360, 201, 360);
	private static final List<LivingEntity> MOBS = new ArrayList<>();
	private static LivingEntity husk;
	private static LivingEntity creeper;
	private static LivingEntity hidden;
	private static LivingEntity far;
	private static long energyBefore;

	private SelfTestTurrets() {
	}

	private static LaserTurretBlockEntity turret(ServerLevel level) {
		return level.getBlockEntity(TURRET) instanceof LaserTurretBlockEntity t ? t : null;
	}

	private static <T extends Mob> T place(ServerLevel level, EntityType<T> type, double dx, double dz) {
		T mob = type.create(level, EntitySpawnReason.COMMAND);
		mob.snapTo(TURRET.getX() + 0.5 + dx, TURRET.getY(), TURRET.getZ() + 0.5 + dz, 0, 0);
		mob.setNoAi(true);
		mob.setPersistenceRequired();
		level.addFreshEntity(mob);
		MOBS.add(mob);
		return mob;
	}

	static void setup(ServerLevel level) {
		MOBS.clear();
		for (int cx = (TURRET.getX() - 32) >> 4; cx <= (TURRET.getX() + 32) >> 4; cx++) {
			for (int cz = (TURRET.getZ() - 32) >> 4; cz <= (TURRET.getZ() + 32) >> 4; cz++) {
				level.setChunkForced(cx, cz, true);
			}
		}

		for (BlockPos p : BlockPos.betweenClosed(TURRET.offset(-24, -1, -24), TURRET.offset(24, -1, 24))) {
			level.setBlock(p, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		}

		for (BlockPos p : BlockPos.betweenClosed(TURRET.offset(-24, 0, -24), TURRET.offset(24, 6, 24))) {
			level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}

		level.setBlock(TURRET, ModBlocks.LASER_TURRET_BASIC.defaultBlockState(), Block.UPDATE_ALL);
		LaserTurretBlockEntity turret = turret(level);
		turret.restoreEnergy(TurretTier.BASIC.capacity());
		turret.setMode(TurretTargeting.Mode.NO_CREEPERS);
		energyBefore = turret.getEnergy();
		husk = place(level, EntityTypes.HUSK, 6, 0);
		creeper = place(level, EntityTypes.CREEPER, -6, 0);
		place(level, EntityTypes.VILLAGER, 0, 4);
		place(level, EntityTypes.WOLF, 0, -4);
		place(level, EntityTypes.IRON_GOLEM, 3, 3);
		place(level, com.solarquarry.registry.ModEntities.SHOP_GUARD, -3, -3);
		far = place(level, EntityTypes.HUSK, 0, 20); // beyond the basic range of 12
		// One behind a wall.
		for (int y = 0; y < 4; y++) {
			for (int x = -2; x <= 2; x++) {
				level.setBlock(TURRET.offset(x, y, -8), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}

		hidden = place(level, EntityTypes.HUSK, 0, -10);
	}

	static void verifyFiring(ServerLevel level) {
		LaserTurretBlockEntity turret = turret(level);
		long shots = turret.getShots();
		long spent = energyBefore - turret.getEnergy();
		SolarQuarry.LOGGER.info("[SelfTest] turret: {} shots, {} E, husk {} hp, status {}", shots, spent, husk.isAlive() ? husk.getHealth() : 0, turret.getStatus());
		SelfTest.check(shots > 0 && (!husk.isAlive() || husk.getHealth() < husk.getMaxHealth()), "turret: shoots a hostile mob in range");
		SelfTest.check(spent == shots * TurretTier.BASIC.shotCost(), "turret: every shot is paid, nothing else uses energy (" + spent + " E for " + shots + " shots)");
		boolean bystandersFine = true;
		for (LivingEntity mob : MOBS) {
			if (mob != husk && mob.getHealth() < mob.getMaxHealth()) {
				bystandersFine = false;
				SolarQuarry.LOGGER.info("[SelfTest] turret hurt {}", mob.getType());
			}
		}

		SelfTest.check(bystandersFine, "turret: villagers, wolves, golems, the guard, creepers (in no-creeper mode), mobs out of range or behind a wall are untouched");
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		turret.saveWithFullMetadata(out);
		BlockEntity copy = BlockEntity.loadStatic(TURRET, level.getBlockState(TURRET), out.buildResult(), level.registryAccess());
		SelfTest.check(copy instanceof LaserTurretBlockEntity c && c.getEnergy() == turret.getEnergy() && c.getMode() == TurretTargeting.Mode.NO_CREEPERS && c.getShots() == shots,
			"turret: energy, settings and count survive save/load");
		// Next: all hostiles (creepers too), but without energy.
		turret.setMode(TurretTargeting.Mode.ALL_HOSTILE);
		turret.extractForPickup(level.registryAccess());
		energyBefore = 0;
	}

	static void verifyNoPower(ServerLevel level) {
		LaserTurretBlockEntity turret = turret(level);
		SelfTest.check(creeper.getHealth() == creeper.getMaxHealth() && turret.getStatus() == LaserTurretBlockEntity.Status.NO_POWER,
			"turret: without energy it does not fire and reports No power");
		turret.setEnabled(false);
		turret.restoreEnergy(TurretTier.BASIC.capacity());
	}

	static void verifyOff(ServerLevel level) {
		LaserTurretBlockEntity turret = turret(level);
		SelfTest.check(creeper.getHealth() == creeper.getMaxHealth() && turret.getStatus() == LaserTurretBlockEntity.Status.OFF, "turret: switched off, it does not fire");
		turret.setEnabled(true);
		turret.setDamaged(true);
	}

	static void verifyDamaged(ServerLevel level) {
		LaserTurretBlockEntity turret = turret(level);
		SelfTest.check(creeper.getHealth() == creeper.getMaxHealth() && turret.getStatus() == LaserTurretBlockEntity.Status.DAMAGED, "turret: a wrecked structure turret never fires");
		turret.setDamaged(false);
	}

	static void verifyCreeper(ServerLevel level) {
		SelfTest.check(creeper.getHealth() < creeper.getMaxHealth() || !creeper.isAlive(), "turret: in all-hostiles mode it shoots creepers too");
		SelfTest.check(far.getHealth() == far.getMaxHealth() && hidden.getHealth() == hidden.getMaxHealth(), "turret: still nothing out of range or out of sight");
		for (LivingEntity mob : MOBS) {
			mob.discard();
		}

		MOBS.clear();
		level.removeBlock(TURRET, false);
		for (int cx = (TURRET.getX() - 32) >> 4; cx <= (TURRET.getX() + 32) >> 4; cx++) {
			for (int cz = (TURRET.getZ() - 32) >> 4; cz <= (TURRET.getZ() + 32) >> 4; cz++) {
				level.setChunkForced(cx, cz, false);
			}
		}
	}
}
