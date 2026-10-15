package com.solarquarry.boots;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of the Rocket Boots: <b>crouch to fly</b>. While a player wearing charged boots holds crouch in the air,
 * the thrusters push them upward (up to the tier's climb speed) and burn energy every tick; letting go of crouch ends
 * the thrust and they fall normally (the boots then absorb part of the landing). Everything is decided on the server
 * from the player's own movement state, so the client cannot fly without paying: no packet asks for thrust.
 *
 * <p>Not while on the ground (crouching there still just sneaks), in water or lava, on a ladder or vine, gliding with
 * an elytra, riding, sleeping, spectating, flying in creative, or with a container screen open. Nothing changes the
 * player's attributes, so nothing can stay stuck after swapping boots, dying or changing dimension.
 */
public final class RocketBoots {
	private static final Set<UUID> THRUSTING = new HashSet<>();

	private RocketBoots() {
	}

	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			tickPlayer(player);
		}
	}

	public static void forget(UUID player) {
		THRUSTING.remove(player);
	}

	public static boolean isThrusting(UUID player) {
		return THRUSTING.contains(player);
	}

	/** Why the thrusters may not fire right now, or null if they may. */
	public static String blockedReason(ServerPlayer player) {
		if (RocketBootsItem.worn(player).isEmpty()) {
			return "no_boots";
		}

		if (!player.isShiftKeyDown()) {
			return "not_crouching";
		}

		if (!player.isAlive() || player.isSpectator() || player.isPassenger() || player.isSleeping() || player.getAbilities().flying) {
			return "state";
		}

		if (player.onGround()) {
			return "on_ground";
		}

		if (player.isInWater() || player.isInLava() || player.onClimbable() || player.isFallFlying()) {
			return "terrain";
		}

		if (player.containerMenu != player.inventoryMenu) {
			return "screen";
		}

		return null;
	}

	/** One tick of the boots for one player; returns true if the thrusters fired. */
	public static boolean tickPlayer(ServerPlayer player) {
		boolean was = THRUSTING.contains(player.getUUID());
		String blocked = blockedReason(player);
		ItemStack boots = RocketBootsItem.worn(player);
		if (blocked != null) {
			if (was) {
				stop(player, false);
			}

			return false;
		}

		RocketTier tier = ((RocketBootsItem) boots.getItem()).tier();
		if (!com.solarquarry.item.PoweredItem.use(boots, tier.thrustCost())) {
			if (was) {
				stop(player, true);
			} else if (player.tickCount % 20 == 0) {
				player.sendOverlayMessage(Component.translatable("solarquarry.rocket_boots.empty"));
			}

			return false;
		}

		Vec3 motion = player.getDeltaMovement();
		player.setDeltaMovement(motion.x, RocketTier.thrust(motion.y, tier.climb()), motion.z);
		player.connection.send(new ClientboundSetEntityMotionPacket(player));
		player.connection.resetFlyingTicks(); // powered flight, not a cheat
		player.resetFallDistance(); // the fall counts from where the thrust ends
		if (!was) {
			THRUSTING.add(player.getUUID());
			player.level().playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.7F, 1.3F);
		}

		if (player.tickCount % 2 == 0) {
			player.level().sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() - 0.1, player.getZ(), 3, 0.12, 0.02, 0.12, 0.01);
			player.level().sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() - 0.2, player.getZ(), 2, 0.15, 0.02, 0.15, 0.01);
		}

		if (player.tickCount % 8 == 0) {
			player.level().playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.15F, 1.8F);
		}

		return true;
	}

	private static void stop(ServerPlayer player, boolean empty) {
		THRUSTING.remove(player.getUUID());
		player.level().playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4F, 1.6F);
		if (empty) {
			player.sendOverlayMessage(Component.translatable("solarquarry.rocket_boots.empty"));
		}
	}

	/** Fall damage after the boots absorb their share (called from the fall-damage hook, server side). */
	public static int reduceFallDamage(ServerPlayer player, int damage) {
		ItemStack boots = RocketBootsItem.worn(player);
		if (boots.isEmpty() || damage <= 0) {
			return damage;
		}

		RocketTier tier = ((RocketBootsItem) boots.getItem()).tier();
		int absorbed = RocketTier.absorbedDamage(damage, tier.fallProtection(), RocketBootsItem.energy(boots));
		if (absorbed > 0) {
			RocketBootsItem.setEnergy(boots, RocketBootsItem.energy(boots) - absorbed * RocketTier.FALL_COST_PER_POINT);
			player.level().sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 8, 0.3, 0.05, 0.3, 0.02);
		}

		return damage - absorbed;
	}
}
