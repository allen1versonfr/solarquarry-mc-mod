package com.solarquarry;

import com.solarquarry.blockentity.BatteryBlockEntity;
import com.solarquarry.boots.RocketBoots;
import com.solarquarry.boots.RocketBootsItem;
import com.solarquarry.boots.RocketTier;
import com.solarquarry.charging.ChargingStationBlockEntity;
import com.solarquarry.item.LiquidProspectorItem;
import com.solarquarry.item.PoweredItem;
import com.solarquarry.item.ProspectorItem;
import com.solarquarry.item.ScannerHelmetItem;
import com.solarquarry.item.SlimeProspectorItem;
import com.solarquarry.registry.ModBlocks;
import com.solarquarry.registry.ModItems;
import java.util.Random;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/** Self-tests for Checkpoint H: crouch-to-fly Rocket Boots, the Charging Station, powered prospectors and helmet. */
final class SelfTestPowered {
	static final BlockPos STATION = new BlockPos(330, 200, 330);
	static final BlockPos POOL = new BlockPos(404, 200, 404);
	static final BlockPos DARK = new BlockPos(404, 150, 404);
	static final BlockPos LIT = new BlockPos(410, 150, 404);
	private static ItemStack charging = ItemStack.EMPTY;
	private static long stationBefore;

	private SelfTestPowered() {
	}

	private static FakePlayer player(ServerLevel level, String name) {
		return FakePlayer.get(level, new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(name.getBytes()), name));
	}

	// ---- Rocket Boots ----

	static void verifyRocketBoots(ServerLevel level) {
		FakePlayer player = player(level, "BootsTest");
		player.snapTo(110.5, 205, 110.5, 0, 0);
		player.setDeltaMovement(0, 0, 0);
		ItemStack boots = new ItemStack(ModItems.IRON_ROCKET_BOOTS);
		player.setItemSlot(EquipmentSlot.FEET, boots);
		player.setOnGround(false);
		player.setShiftKeyDown(true);
		SelfTest.check(!RocketBoots.tickPlayer(player), "boots: an empty pair does not fire");
		RocketBootsItem.setEnergy(boots, 10_000);
		player.setShiftKeyDown(false);
		SelfTest.check(!RocketBoots.tickPlayer(player), "boots: no thrust without crouching");
		player.setShiftKeyDown(true);
		player.setOnGround(true);
		SelfTest.check(!RocketBoots.tickPlayer(player) && RocketBootsItem.energy(boots) == 10_000, "boots: crouching on the ground still just sneaks");
		player.setOnGround(false);
		boolean fired = RocketBoots.tickPlayer(player);
		SelfTest.check(fired && Math.abs(player.getDeltaMovement().y - RocketTier.THRUST) < 1e-6 && RocketBootsItem.energy(boots) == 10_000 - RocketTier.IRON.thrustCost(),
			"boots: crouching in the air fires the thrusters and costs energy (" + RocketBootsItem.energy(boots) + " E)");
		for (int i = 0; i < 19; i++) {
			RocketBoots.tickPlayer(player);
		}

		SelfTest.check(Math.abs(player.getDeltaMovement().y - RocketTier.IRON.climb()) < 1e-6 && RocketBootsItem.energy(boots) == 10_000 - 20 * RocketTier.IRON.thrustCost(),
			"boots: they climb at the tier's speed and burn energy every tick of thrust (" + String.format(java.util.Locale.ROOT, "%.2f", player.getDeltaMovement().y) + ")");
		player.setShiftKeyDown(false);
		long before = RocketBootsItem.energy(boots);
		SelfTest.check(!RocketBoots.tickPlayer(player) && !RocketBoots.isThrusting(player.getUUID()) && RocketBootsItem.energy(boots) == before,
			"boots: letting go of crouch stops the thrust (no energy used)");
		player.setShiftKeyDown(true);
		RocketBootsItem.setEnergy(boots, RocketTier.IRON.thrustCost() - 1);
		SelfTest.check(!RocketBoots.tickPlayer(player) && RocketBootsItem.energy(boots) == RocketTier.IRON.thrustCost() - 1, "boots: no free flight when nearly empty");
		RocketBootsItem.setEnergy(boots, 10_000);
		RocketBootsItem.setEnergy(boots, 10_000);
		int left = RocketBoots.reduceFallDamage(player, 10);
		SelfTest.check(left == 4 && RocketBootsItem.energy(boots) == 10_000 - 6 * RocketTier.FALL_COST_PER_POINT,
			"boots: iron boots absorb 60% of fall damage, paid with energy (" + left + " left)");
		player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
		SelfTest.check(RocketBoots.reduceFallDamage(player, 10) == 10 && !RocketBoots.tickPlayer(player), "boots: taking them off ends every effect");
		for (int i = 1; i < RocketTier.values().length; i++) {
			RocketTier a = RocketTier.values()[i - 1];
			RocketTier b = RocketTier.values()[i];
			if (!(b.capacity() > a.capacity() && b.climb() > a.climb() && b.flightSeconds() > a.flightSeconds())) {
				SelfTest.check(false, "boots: " + b + " is better than " + a);
			}
		}

		SelfTest.check(true, "boots: every tier stores more, climbs faster and flies longer (" + Math.round(RocketTier.LEATHER.flightSeconds()) + " s to "
			+ Math.round(RocketTier.HELIONITE.flightSeconds()) + " s)");
		BlockPos cell = new BlockPos(110, 205, 112);
		level.setBlock(cell, ModBlocks.BATTERY_BASIC.defaultBlockState(), Block.UPDATE_ALL);
		((BatteryBlockEntity) level.getBlockEntity(cell)).restoreEnergy(1_000_000L);
		ItemStack hand = new ItemStack(ModItems.ORE_PROSPECTOR);
		player.setItemInHand(InteractionHand.MAIN_HAND, hand);
		level.getBlockState(cell).useItemOn(hand, level, player, InteractionHand.MAIN_HAND,
			new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(cell), Direction.UP, cell, false));
		SelfTest.check(PoweredItem.energy(hand) == ProspectorItem.CAPACITY, "powered: a battery charges any powered tool (" + PoweredItem.energy(hand) + " E)");
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		level.removeBlock(cell, false);
		RocketBoots.forget(player.getUUID());
	}

	// ---- Charging Station ----

	private static ChargingStationBlockEntity station(ServerLevel level) {
		return level.getBlockEntity(STATION) instanceof ChargingStationBlockEntity s ? s : null;
	}

	static void setupStation(ServerLevel level) {
		level.setChunkForced(STATION.getX() >> 4, STATION.getZ() >> 4, true);
		level.setBlock(STATION, ModBlocks.CHARGING_STATION.defaultBlockState(), Block.UPDATE_ALL);
		ChargingStationBlockEntity station = station(level);
		station.restoreEnergy(100_000);
		charging = new ItemStack(ModItems.DIAMOND_ROCKET_BOOTS);
		charging.setDamageValue(7);
		var unbreaking = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING);
		charging.enchant(unbreaking, 2);
		station.setItem(ChargingStationBlockEntity.INPUT, charging);
		stationBefore = station.getEnergy();
		SelfTest.check(!station.canPlaceItem(ChargingStationBlockEntity.INPUT, new ItemStack(Items.STONE))
			&& !station.canPlaceItem(ChargingStationBlockEntity.OUTPUT, new ItemStack(ModItems.IRON_ROCKET_BOOTS))
			&& !station.canPlaceItemThroughFace(ChargingStationBlockEntity.INPUT, new ItemStack(ModItems.ORE_PROSPECTOR), Direction.DOWN),
			"station: accepts only powered gear, only in its input, not from below");
	}

	static void verifyStationCharging(ServerLevel level) {
		ChargingStationBlockEntity station = station(level);
		ItemStack item = station.getItem(ChargingStationBlockEntity.INPUT);
		long charged = PoweredItem.energy(item);
		long spent = stationBefore - station.getEnergy();
		SelfTest.check(charged > 0 && charged == spent, "station: energy into the item = energy out of the station (" + charged + " / " + spent + ")");
		SelfTest.check(charged <= 40L * ChargingStationBlockEntity.rateFor(item), "station: limited to the charging rate (" + ChargingStationBlockEntity.rateFor(item) + " E/t)");
		SelfTest.check(item.getDamageValue() == 7 && item.getEnchantments().size() == 1 && station.getStatus() == ChargingStationBlockEntity.Status.CHARGING,
			"station: durability and enchantments untouched while charging");
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		station.saveWithFullMetadata(out);
		var copy = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(STATION, level.getBlockState(STATION),
			out.buildResult(), level.registryAccess());
		SelfTest.check(copy instanceof ChargingStationBlockEntity c && c.getEnergy() == station.getEnergy()
			&& PoweredItem.energy(c.getItem(ChargingStationBlockEntity.INPUT)) == charged, "station: energy and the item's charge survive save/load");
		// Taking the item out mid-charge keeps its charge (it lives on the item).
		ItemStack taken = station.removeItem(ChargingStationBlockEntity.INPUT, 1);
		SelfTest.check(PoweredItem.energy(taken) == charged, "station: an item taken out half-charged keeps its charge");
		station.setItem(ChargingStationBlockEntity.INPUT, taken);
	}

	static void fillStation(ServerLevel level) {
		ChargingStationBlockEntity station = station(level);
		ItemStack item = station.getItem(ChargingStationBlockEntity.INPUT);
		PoweredItem.setEnergy(item, PoweredItem.capacityOf(item) - 10);
	}

	static void verifyStationDone(ServerLevel level) {
		ChargingStationBlockEntity station = station(level);
		ItemStack out = station.getItem(ChargingStationBlockEntity.OUTPUT);
		SelfTest.check(station.getItem(ChargingStationBlockEntity.INPUT).isEmpty() && PoweredItem.isFull(out) && station.getStatus() == ChargingStationBlockEntity.Status.DONE,
			"station: a full item moves to the output and charging stops");
		SelfTest.check(station.canTakeItemThroughFace(ChargingStationBlockEntity.OUTPUT, out, Direction.DOWN), "station: charged items come out of the bottom");
		station.removeItem(ChargingStationBlockEntity.OUTPUT, 1);
		// No power: an item waits, nothing changes.
		station.extractForPickup(level.registryAccess());
		ItemStack prospector = new ItemStack(ModItems.ORE_PROSPECTOR);
		station.setItem(ChargingStationBlockEntity.INPUT, prospector);
		ChargingStationBlockEntity.serverTick(level, STATION, level.getBlockState(STATION), station);
		SelfTest.check(PoweredItem.energy(station.getItem(ChargingStationBlockEntity.INPUT)) == 0 && station.getStatus() == ChargingStationBlockEntity.Status.NO_POWER,
			"station: without energy it waits and reports No power");
		level.removeBlock(STATION, false);
		level.setChunkForced(STATION.getX() >> 4, STATION.getZ() >> 4, false);
	}

	// ---- tools ----

	/** Independent copy of the classic slime-chunk formula, to check the prospector against. */
	private static boolean referenceSlimeChunk(long seed, int x, int z) {
		Random random = new Random(seed + (long) (x * x * 4987142) + (long) (x * 5947611) + (long) (z * z) * 4392871L + (long) (z * 389711) ^ 987234911L);
		return random.nextInt(10) == 0;
	}

	static void verifyTools(ServerLevel level) {
		FakePlayer player = player(level, "ToolTest");
		player.snapTo(POOL.getX() + 0.5, POOL.getY() + 2, POOL.getZ() + 0.5, 0, 0);
		player.getCooldowns().tick();

		// Ore Prospector: refuses when empty, pays per scan.
		ItemStack ore = new ItemStack(ModItems.ORE_PROSPECTOR);
		player.setItemInHand(InteractionHand.MAIN_HAND, ore);
		InteractionResult empty = ore.getItem().use(level, player, InteractionHand.MAIN_HAND);
		SelfTest.check(empty == InteractionResult.FAIL && PoweredItem.energy(ore) == 0, "ore prospector: refuses to scan without charge");
		PoweredItem.setEnergy(ore, 1_000);
		InteractionResult ok = ore.getItem().use(level, player, InteractionHand.MAIN_HAND);
		SelfTest.check(ok == InteractionResult.SUCCESS && PoweredItem.energy(ore) == 1_000 - ProspectorItem.SCAN_COST, "ore prospector: a scan costs " + ProspectorItem.SCAN_COST + " E");
		InteractionResult again = ore.getItem().use(level, player, InteractionHand.MAIN_HAND);
		SelfTest.check(again == InteractionResult.FAIL && PoweredItem.energy(ore) == 1_000 - ProspectorItem.SCAN_COST, "ore prospector: repeated use during the cooldown costs nothing and scans nothing");

		// Liquid Prospector: exact sources in the chunk.
		ChunkPos chunk = new ChunkPos(POOL.getX() >> 4, POOL.getZ() >> 4);
		level.setChunkForced(chunk.x(), chunk.z(), true);
		level.setBlock(POOL, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(POOL.east(), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(POOL.east(2), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(POOL.south(3), Blocks.LAVA.defaultBlockState(), Block.UPDATE_CLIENTS);
		LiquidProspectorItem.Scan scan = LiquidProspectorItem.scan(level, POOL.above(2));
		SelfTest.check(scan.water().sources == 3 && scan.lava().sources == 1 && POOL.equals(scan.water().nearest) && POOL.south(3).equals(scan.lava().nearest),
			"liquid prospector: finds every water and lava source in the chunk and the nearest of each (" + scan.water().sources + " water, " + scan.lava().sources + " lava)");
		ItemStack liquid = new ItemStack(ModItems.LIQUID_PROSPECTOR);
		PoweredItem.setEnergy(liquid, LiquidProspectorItem.SCAN_COST);
		player.setItemInHand(InteractionHand.MAIN_HAND, liquid);
		SelfTest.check(liquid.getItem().use(level, player, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS && PoweredItem.energy(liquid) == 0,
			"liquid prospector: a scan costs " + LiquidProspectorItem.SCAN_COST + " E");
		for (BlockPos p : new BlockPos[] {POOL, POOL.east(), POOL.east(2), POOL.south(3)}) {
			level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}

		level.setChunkForced(chunk.x(), chunk.z(), false);

		// Slime Prospector: the game's own rule, checked against an independent copy of the formula.
		long seed = level.getSeed();
		int mismatches = 0;
		int slimy = 0;
		for (int x = -10; x <= 10; x++) {
			for (int z = -10; z <= 10; z++) {
				boolean mine = SlimeProspectorItem.isSlimeChunk(seed, x, z);
				if (mine != referenceSlimeChunk(seed, x, z)) {
					mismatches++;
				}

				slimy += mine ? 1 : 0;
			}
		}

		SelfTest.check(mismatches == 0 && slimy > 10 && slimy < 100, "slime prospector: slime chunks match the world seed rule (" + slimy + " of 441 chunks)");
		ItemStack slime = new ItemStack(ModItems.SLIME_PROSPECTOR);
		PoweredItem.setEnergy(slime, 1_000);
		player.setItemInHand(InteractionHand.MAIN_HAND, slime);
		SelfTest.check(slime.getItem().use(level, player, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS
			&& PoweredItem.energy(slime) == 1_000 - SlimeProspectorItem.SCAN_COST, "slime prospector: a scan costs " + SlimeProspectorItem.SCAN_COST + " E");
		SelfTest.check(SlimeProspectorItem.direction(0, -10).equals("n") && SlimeProspectorItem.direction(10, 0).equals("e") && SlimeProspectorItem.direction(-7, 7).equals("sw"),
			"slime prospector: directions are right");

		// Scanner Helmet: drains while worn, the light rule follows the real light.
		ItemStack helmet = new ItemStack(ModItems.SCANNER_HELMET);
		PoweredItem.setEnergy(helmet, 5);
		player.setItemSlot(EquipmentSlot.HEAD, helmet);
		boolean on1 = ScannerHelmetItem.drain(player);
		boolean on2 = ScannerHelmetItem.drain(player);
		boolean on3 = ScannerHelmetItem.drain(player);
		SelfTest.check(on1 && on2 && !on3 && PoweredItem.energy(helmet) == 1 && !ScannerHelmetItem.active(player),
			"scanner helmet: uses " + ScannerHelmetItem.DRAIN + " E per tick while worn and switches off when empty");
		level.setChunkForced(chunk.x(), chunk.z(), true);
		for (BlockPos room : new BlockPos[] {DARK, LIT}) {
			for (BlockPos p : BlockPos.betweenClosed(room.offset(-2, -1, -2), room.offset(2, 2, 2))) {
				level.setBlock(p, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
			}

			level.setBlock(room.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			level.setBlock(room, room == LIT ? Blocks.TORCH.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}

		player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
	}

	/** After the light engine has caught up: the light rule of the Scanner Helmet. */
	static void verifyLight(ServerLevel level) {
		boolean darkAllows = ScannerHelmetItem.lightAllowsSpawn(level, DARK);
		boolean litAllows = ScannerHelmetItem.lightAllowsSpawn(level, LIT);
		SelfTest.check(darkAllows && !litAllows, "scanner helmet: a dark enclosed spot allows hostile spawns, a torch makes it safe (block light "
			+ level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, LIT) + ")");
		for (BlockPos room : new BlockPos[] {DARK, LIT}) {
			for (BlockPos p : BlockPos.betweenClosed(room.offset(-2, -1, -2), room.offset(2, 2, 2))) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}

		level.setChunkForced(DARK.getX() >> 4, DARK.getZ() >> 4, false);
	}
}
