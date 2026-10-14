package com.solarquarry.quarry;

import com.mojang.authlib.GameProfile;
import com.solarquarry.config.SolarQuarryConfig;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Asks the world (and any protection mod) whether the quarry's owner may break a block, whether or not the owner is
 * online. The quarry acts as a Fabric {@link FakePlayer} carrying the owner's profile, so:
 * <ul>
 * <li>vanilla spawn protection treats it like the owner (operators are exempt as usual);</li>
 * <li>claim and protection mods that listen to Fabric's {@link PlayerBlockBreakEvents#BEFORE} (the standard hook)
 * can refuse the break, exactly as they would for the owner in person;</li>
 * </ul>
 * Protection mods that only hook client input or their own packets cannot be detected by any machine.
 */
public final class QuarryProtection {
	private QuarryProtection() {
	}

	/**
	 * The player the quarry acts as: a fake player with the owner's profile (never the real player, so mods that react to
	 * a player's own actions are not triggered), or Fabric's generic fake player for quarries with no owner.
	 */
	public static ServerPlayer actor(ServerLevel level, @Nullable UUID owner, String ownerName) {
		if (owner == null) {
			return FakePlayer.get(level);
		}

		return FakePlayer.get(level, new GameProfile(owner, ownerName.isEmpty() ? "[Solar Quarry]" : ownerName));
	}

	/** True when the owner is allowed to break this block here. */
	public static boolean mayBreak(ServerLevel level, ServerPlayer actor, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		if (!SolarQuarryConfig.get().respectProtection()) {
			return true;
		}

		if (!level.mayInteract(actor, pos)) {
			return false;
		}

		return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, actor, pos, state, blockEntity);
	}
}
