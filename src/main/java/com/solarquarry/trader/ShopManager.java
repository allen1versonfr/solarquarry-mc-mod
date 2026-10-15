package com.solarquarry.trader;

import com.solarquarry.SolarQuarry;
import com.solarquarry.registry.ModBlocks;
import com.solarquarry.registry.ModEntities;
import com.solarquarry.registry.ModItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Runs the Mechanic feature: the first-join introduction and starter kit (once per player), the shop (built once per
 * world), and next-morning replacement of a dead trader or guard. All state is persistent ({@link ShopState}), so
 * restarts, unloaded chunks and repeated events never create duplicates or a second kit.
 */
public final class ShopManager {
	/** Ticks after joining before the trader introduces himself (the world is loaded around the player by then). */
	public static final int INTRO_DELAY = 100;
	private static final Map<UUID, Integer> PENDING_INTROS = new HashMap<>();

	private ShopManager() {
	}

	/** The starter kit: everything needed to craft a first quarry, plus power, logistics and a wrench. */
	public static List<ItemStack> starterKit() {
		List<ItemStack> kit = new ArrayList<>();
		kit.add(new ItemStack(ModItems.MACHINE_FRAME, 2));
		kit.add(new ItemStack(ModItems.DRILL_HEAD));
		kit.add(new ItemStack(ModItems.CIRCUIT_BOARD));
		kit.add(new ItemStack(Items.IRON_INGOT, 4));
		kit.add(new ItemStack(Items.REDSTONE_BLOCK));
		kit.add(new ItemStack(ModBlocks.SOLAR_PANEL_BASIC, 2));
		kit.add(new ItemStack(ModBlocks.ENERGY_CABLE, 8));
		kit.add(new ItemStack(ModBlocks.ITEM_PIPE, 8));
		kit.add(new ItemStack(Items.CHEST));
		kit.add(new ItemStack(Items.TORCH, 32));
		kit.add(new ItemStack(ModItems.WRENCH));
		kit.add(new ItemStack(ModBlocks.FABRICATION_BENCH));
		return kit;
	}

	// ---- events ----

	public static void onJoin(ServerPlayer player) {
		if (!Boolean.TRUE.equals(player.getAttached(ShopState.KIT_CLAIMED))) {
			PENDING_INTROS.put(player.getUUID(), INTRO_DELAY);
		}
	}

	public static void tick(MinecraftServer server) {
		if (!PENDING_INTROS.isEmpty()) {
			List<UUID> due = new ArrayList<>();
			PENDING_INTROS.replaceAll((id, ticks) -> ticks - 1);
			PENDING_INTROS.forEach((id, ticks) -> {
				if (ticks <= 0) {
					due.add(id);
				}
			});
			for (UUID id : due) {
				PENDING_INTROS.remove(id);
				ServerPlayer player = server.getPlayerList().getPlayer(id);
				if (player != null) {
					introduce(player);
				}
			}
		}

		MechanicHouseSite.tick(server);
		if (server.getTickCount() % 20 == 0) {
			ServerLevel overworld = server.overworld();
			replaceIfDue(overworld);
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				deliverParcel(player);
			}
		}
	}

	/** First join: make sure the shop exists, then the trader greets the player and hands over the starter kit. */
	public static void introduce(ServerPlayer player) {
		ServerLevel overworld = player.level().getServer().overworld();
		BlockPos shop = ensureShop(overworld);
		boolean first = giveStarterKit(player);
		if (first) {
			player.sendSystemMessage(Component.translatable("solarquarry.trader.welcome", player.getDisplayName()).withStyle(ChatFormatting.GOLD));
			player.sendSystemMessage(Component.translatable("solarquarry.trader.kit").withStyle(ChatFormatting.YELLOW));
			if (shop != null) {
				player.sendSystemMessage(Component.translatable("solarquarry.trader.shop", shop.getX(), shop.getY(), shop.getZ()).withStyle(ChatFormatting.YELLOW));
			}

			player.level().playSound(null, player.blockPosition(), SoundEvents.WANDERING_TRADER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
		}
	}

	/**
	 * Gives the starter kit once. Whatever does not fit is kept as a parcel and delivered as space frees up.
	 * @return true if the kit was given now, false if this player already had it
	 */
	public static boolean giveStarterKit(ServerPlayer player) {
		if (Boolean.TRUE.equals(player.getAttached(ShopState.KIT_CLAIMED))) {
			return false;
		}

		player.setAttached(ShopState.KIT_CLAIMED, true);
		List<ItemStack> leftover = new ArrayList<>();
		for (ItemStack stack : starterKit()) {
			if (!player.getInventory().add(stack) || !stack.isEmpty()) {
				if (!stack.isEmpty()) {
					leftover.add(stack.copy());
				}
			}
		}

		if (!leftover.isEmpty()) {
			player.setAttached(ShopState.PARCEL, List.copyOf(leftover));
			player.sendSystemMessage(Component.translatable("solarquarry.trader.parcel", leftover.size()).withStyle(ChatFormatting.YELLOW));
		}

		return true;
	}

	/** Delivers waiting kit items into free inventory space. */
	public static void deliverParcel(ServerPlayer player) {
		List<ItemStack> parcel = player.getAttached(ShopState.PARCEL);
		if (parcel == null || parcel.isEmpty() || !player.isAlive()) {
			return;
		}

		List<ItemStack> left = new ArrayList<>();
		boolean delivered = false;
		for (ItemStack stack : parcel) {
			ItemStack copy = stack.copy();
			int before = copy.getCount();
			player.getInventory().add(copy);
			delivered |= copy.getCount() < before;
			if (!copy.isEmpty()) {
				left.add(copy);
			}
		}

		if (left.isEmpty()) {
			player.removeAttached(ShopState.PARCEL);
			player.sendSystemMessage(Component.translatable("solarquarry.trader.parcel_done").withStyle(ChatFormatting.GREEN));
		} else if (delivered) {
			player.setAttached(ShopState.PARCEL, List.copyOf(left));
		}
	}

	/**
	 * Picks the Mechanic's building site once per world, starts the construction of his house and brings him and his
	 * guard there. Returns the floor centre. Worlds from 3.x keep their finished shop as it is.
	 */
	public static @Nullable BlockPos ensureShop(ServerLevel overworld) {
		ShopState state = ShopState.get(overworld);
		if (state.built() && state.center().isPresent()) {
			return state.center().get();
		}

		BlockPos spawn = overworld.getRespawnData().globalPos().pos();
		overworld.getChunk(spawn); // make sure the area is generated
		BlockPos site = MechanicHouse.findSite(overworld, spawn);
		state = state.withBuilt(site).withHouse(ShopState.MECHANIC_HOUSE);
		ShopState.set(overworld, state);
		MechanicHouseSite.start(overworld, site);
		spawnTrader(overworld);
		spawnGuard(overworld);
		SolarQuarry.LOGGER.info("The Mechanic starts building his house at {}", site);
		return site;
	}

	/** Where the Mechanic stands and the guard waits, for the house this world has. */
	public static BlockPos traderSpot(ShopState state) {
		BlockPos floor = state.center().orElseThrow();
		return state.isMechanicHouse() ? MechanicHouse.traderSpot(floor) : floor.offset(0, 1, 1);
	}

	public static BlockPos guardSpot(ShopState state) {
		BlockPos floor = state.center().orElseThrow();
		return state.isMechanicHouse() ? MechanicHouse.guardSpot(floor) : floor.offset(2, 1, -2);
	}

	public static @Nullable ShopTrader spawnTrader(ServerLevel overworld) {
		ShopState state = ShopState.get(overworld);
		if (state.center().isEmpty()) {
			return null;
		}

		BlockPos spot = traderSpot(state);
		overworld.getChunk(spot);
		ShopTrader trader = ModEntities.SHOP_TRADER.create(overworld, EntitySpawnReason.EVENT);
		if (trader == null) {
			return null;
		}

		trader.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 180.0F, 0.0F);
		var site = MechanicHouseSite.site(overworld);
		if (state.isMechanicHouse() && site != null && site.isUnderConstruction()) {
			trader.setHomeTo(state.center().get(), 12); // he is still building
		} else {
			trader.anchorTo(spot);
		}

		trader.setShopMember(true);
		ShopState.set(overworld, ShopState.get(overworld).withTrader(trader.getUUID()));
		overworld.addFreshEntity(trader);
		return trader;
	}

	public static @Nullable ShopGuard spawnGuard(ServerLevel overworld) {
		ShopState state = ShopState.get(overworld);
		if (state.center().isEmpty()) {
			return null;
		}

		BlockPos spot = guardSpot(state);
		overworld.getChunk(spot);
		ShopGuard guard = ModEntities.SHOP_GUARD.create(overworld, EntitySpawnReason.EVENT);
		if (guard == null) {
			return null;
		}

		guard.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.0F, 0.0F);
		guard.equip();
		guard.setHomeTo(state.center().get(), 10);
		guard.setShopMember(true);
		ShopState.set(overworld, ShopState.get(overworld).withGuard(guard.getUUID()));
		overworld.addFreshEntity(guard);
		return guard;
	}

	public static long day(ServerLevel level) {
		return Math.floorDiv(level.getOverworldClockTime(), 24000L);
	}

	public static void onTraderDied(ServerLevel level, ShopTrader trader) {
		ServerLevel overworld = level.getServer().overworld();
		ShopState state = ShopState.get(overworld);
		if (state.trader().isPresent() && state.trader().get().equals(trader.getUUID())) {
			ShopState.set(overworld, state.withTraderDead(day(overworld)));
			SolarQuarry.LOGGER.info("The shop's trader died; a replacement arrives next morning");
		}
	}

	public static void onGuardDied(ServerLevel level, ShopGuard guard) {
		ServerLevel overworld = level.getServer().overworld();
		ShopState state = ShopState.get(overworld);
		if (state.guard().isPresent() && state.guard().get().equals(guard.getUUID())) {
			ShopState.set(overworld, state.withGuardDead(day(overworld)));
		}
	}

	/** Next morning after a death, exactly one replacement arrives (the death day is cleared when it does). */
	public static void replaceIfDue(ServerLevel overworld) {
		ShopState state = ShopState.get(overworld);
		if (!state.built()) {
			return;
		}

		long dayTime = overworld.getOverworldClockTime();
		if (state.trader().isEmpty() && ShopState.replacementDue(state.deathDay(), dayTime)) {
			if (spawnTrader(overworld) != null) {
				SolarQuarry.LOGGER.info("A new trader arrived at the shop");
			}
		}

		state = ShopState.get(overworld);
		if (state.guard().isEmpty() && ShopState.replacementDue(state.guardDeathDay(), dayTime)) {
			spawnGuard(overworld);
		}
	}

	public static boolean isRegisteredTrader(ServerLevel level, Entity trader) {
		ShopState state = ShopState.get(level.getServer().overworld());
		return state.trader().isPresent() && state.trader().get().equals(trader.getUUID());
	}

	public static boolean isRegisteredGuard(ServerLevel level, Entity guard) {
		ShopState state = ShopState.get(level.getServer().overworld());
		return state.guard().isPresent() && state.guard().get().equals(guard.getUUID());
	}
}
