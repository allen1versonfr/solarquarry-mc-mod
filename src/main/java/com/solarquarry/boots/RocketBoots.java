package com.solarquarry.boots;

import com.solarquarry.SolarQuarry;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of the Rocket Boots. The client only asks ("I crouch-jumped"); the server checks everything (boots worn,
 * energy, crouching, on the ground a moment ago, cooldown), then launches the player itself. Nothing changes the
 * player's attributes, so nothing can stay stuck after swapping boots, dying or changing dimension.
 */
public final class RocketBoots {
	/** Ticks after leaving the ground in which a rocket jump is still accepted (covers network delay). */
	public static final int GROUND_GRACE = 6;
	private static final Map<UUID, Long> LAST_ON_GROUND = new HashMap<>();
	private static final Map<UUID, Long> LAST_LAUNCH = new HashMap<>();

	/** Client to server: "I crouch-jumped". */
	public record JumpPayload() implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<JumpPayload> TYPE = new CustomPacketPayload.Type<>(SolarQuarry.id("rocket_jump"));
		public static final StreamCodec<ByteBuf, JumpPayload> STREAM_CODEC = StreamCodec.unit(new JumpPayload());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private RocketBoots() {
	}

	public static void tick(MinecraftServer server) {
		long now = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.onGround()) {
				LAST_ON_GROUND.put(player.getUUID(), now);
			}
		}
	}

	/** Marks a player as standing on the ground now (used by the self-test, which has no real players). */
	public static void markOnGround(UUID player, long tick) {
		LAST_ON_GROUND.put(player, tick);
	}

	public static void forget(UUID player) {
		LAST_ON_GROUND.remove(player);
		LAST_LAUNCH.remove(player);
	}

	public static void handle(JumpPayload payload, ServerPlayNetworking.Context context) {
		tryLaunch(context.player());
	}

	/** Validates and performs a rocket jump. Returns true if the player was launched. */
	public static boolean tryLaunch(ServerPlayer player) {
		ItemStack boots = RocketBootsItem.worn(player);
		if (boots.isEmpty() || !player.isAlive() || player.isSpectator() || player.isPassenger() || player.isFallFlying()) {
			return false;
		}

		long now = player.level().getServer().getTickCount();
		long ground = LAST_ON_GROUND.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2);
		long last = LAST_LAUNCH.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2);
		if (!player.isShiftKeyDown() || now - ground > GROUND_GRACE || now - last < RocketTier.COOLDOWN) {
			return false;
		}

		RocketTier tier = ((RocketBootsItem) boots.getItem()).tier();
		long energy = RocketBootsItem.energy(boots);
		if (energy < RocketTier.JUMP_COST) {
			player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("solarquarry.rocket_boots.empty"));
			return false;
		}

		RocketBootsItem.setEnergy(boots, energy - RocketTier.JUMP_COST);
		LAST_LAUNCH.put(player.getUUID(), now);
		Vec3 motion = player.getDeltaMovement();
		player.setDeltaMovement(motion.x * 1.2, Math.max(motion.y, tier.launch()), motion.z * 1.2);
		player.connection.send(new ClientboundSetEntityMotionPacket(player));
		// Landing at or above the launch height never hurts (the game's own impulse rule, like wind charges).
		player.setIgnoreFallDamageFromCurrentImpulse(true, player.position());
		player.level().sendParticles(ParticleTypes.FLAME, player.getX(), player.getY(), player.getZ(), 12, 0.15, 0.05, 0.15, 0.02);
		player.level().sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 6, 0.2, 0.05, 0.2, 0.01);
		player.level().playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.8F, 1.2F);
		return true;
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
