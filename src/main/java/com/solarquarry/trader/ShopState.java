package com.solarquarry.trader;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.solarquarry.SolarQuarry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * Persistent shop bookkeeping, attached to the Overworld: where the shop stands, which trader and guard belong to it,
 * and on which day either died (a replacement arrives the next morning). Players carry their own starter-kit state.
 *
 * @param deathDay day number (game time / 24000) the trader died on, or -1 while it lives
 * @param guardDeathDay the same for the guard
 */
public record ShopState(boolean built, Optional<BlockPos> center, Optional<UUID> trader, Optional<UUID> guard, long deathDay, long guardDeathDay) {
	public static final ShopState NONE = new ShopState(false, Optional.empty(), Optional.empty(), Optional.empty(), -1L, -1L);

	public static final Codec<ShopState> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.BOOL.optionalFieldOf("built", false).forGetter(ShopState::built),
		BlockPos.CODEC.optionalFieldOf("center").forGetter(ShopState::center),
		UUIDUtil.CODEC.optionalFieldOf("trader").forGetter(ShopState::trader),
		UUIDUtil.CODEC.optionalFieldOf("guard").forGetter(ShopState::guard),
		Codec.LONG.optionalFieldOf("death_day", -1L).forGetter(ShopState::deathDay),
		Codec.LONG.optionalFieldOf("guard_death_day", -1L).forGetter(ShopState::guardDeathDay)
	).apply(i, ShopState::new));

	/** The world's shop (on the Overworld). */
	public static final AttachmentType<ShopState> SHOP = AttachmentRegistry.createPersistent(SolarQuarry.id("trader_shop"), CODEC);
	/** Per player: the starter kit was delivered (or is waiting as a parcel). Survives death. */
	public static final AttachmentType<Boolean> KIT_CLAIMED = AttachmentRegistry.create(SolarQuarry.id("starter_kit_claimed"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());
	/** Per player: starter-kit items that did not fit yet; delivered as space frees up. Survives death. */
	public static final AttachmentType<List<ItemStack>> PARCEL = AttachmentRegistry.create(SolarQuarry.id("starter_kit_parcel"),
		builder -> builder.persistent(ItemStack.CODEC.listOf()).copyOnDeath());

	public static ShopState get(ServerLevel overworld) {
		return overworld.getAttachedOrElse(SHOP, NONE);
	}

	public static void set(ServerLevel overworld, ShopState state) {
		overworld.setAttached(SHOP, state);
	}

	public ShopState withBuilt(BlockPos pos) {
		return new ShopState(true, Optional.of(pos), this.trader, this.guard, this.deathDay, this.guardDeathDay);
	}

	public ShopState withTrader(UUID id) {
		return new ShopState(this.built, this.center, Optional.of(id), this.guard, -1L, this.guardDeathDay);
	}

	public ShopState withGuard(UUID id) {
		return new ShopState(this.built, this.center, this.trader, Optional.of(id), this.deathDay, -1L);
	}

	public ShopState withTraderDead(long day) {
		return new ShopState(this.built, this.center, Optional.empty(), this.guard, day, this.guardDeathDay);
	}

	public ShopState withGuardDead(long day) {
		return new ShopState(this.built, this.center, this.trader, Optional.empty(), this.deathDay, day);
	}

	/** Whether a replacement is due: one died on an earlier day and it is now morning (0..12000 of the day). */
	public static boolean replacementDue(long deathDay, long dayTime) {
		if (deathDay < 0) {
			return false;
		}

		long today = Math.floorDiv(dayTime, 24000L);
		long timeOfDay = Math.floorMod(dayTime, 24000L);
		return today > deathDay && timeOfDay < 12000L;
	}
}
