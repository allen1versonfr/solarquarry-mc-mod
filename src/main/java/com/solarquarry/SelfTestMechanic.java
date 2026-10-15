package com.solarquarry;

import com.solarquarry.construction.ConstructionProblem;
import com.solarquarry.construction.ConstructionSite;
import com.solarquarry.construction.ConstructionState;
import com.solarquarry.construction.Placement;
import com.solarquarry.registry.ModBlocks;
import com.solarquarry.registry.ModItems;
import com.solarquarry.trader.MechanicHouse;
import com.solarquarry.trader.MechanicHouseSite;
import com.solarquarry.trader.MechanicTrades;
import com.solarquarry.trader.ShopGuard;
import com.solarquarry.trader.ShopManager;
import com.solarquarry.trader.ShopState;
import com.solarquarry.trader.ShopTrader;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;

/** Self-tests for Checkpoint G: the Mechanic builds his house, sells every item with unlimited stock, and his guard. */
final class SelfTestMechanic {
	private static BlockPos obstruction = BlockPos.ZERO;
	private static int placedBeforeObstruction;

	private SelfTestMechanic() {
	}

	static List<ShopTrader> mechanics(ServerLevel level, BlockPos center) {
		return level.getEntitiesOfClass(ShopTrader.class, new AABB(center).inflate(16, 8, 16), e -> e.isAlive());
	}

	static List<ShopGuard> guards(ServerLevel level, BlockPos center) {
		return level.getEntitiesOfClass(ShopGuard.class, new AABB(center).inflate(16, 8, 16), e -> e.isAlive());
	}

	/** A fresh world shop: fast construction for the test, a player block on the roof's path. Returns the floor centre. */
	static BlockPos setup(ServerLevel level) {
		ShopState.set(level, ShopState.NONE);
		level.removeAttached(ShopState.HOUSE_SITE);
		MechanicHouseSite.forget();
		MechanicHouseSite.interval = 1;
		MechanicHouseSite.stepsPerRound = 4;
		BlockPos center = ShopManager.ensureShop(level);
		ConstructionSite site = MechanicHouseSite.site(level);
		// A player's cobblestone where a roof block goes (the roof is built late, so the walls go up first).
		obstruction = center.offset(2, MechanicHouse.ROOF, 2);
		level.setBlock(obstruction, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
		SelfTest.check(site != null && site.isUnderConstruction() && site.placedCount() == 0 && site.totalSteps() > 400,
			"mechanic: a building site is chosen and the house is planned, nothing placed at once (" + (site == null ? 0 : site.totalSteps()) + " steps)");
		SelfTest.check(level.getBlockState(MechanicHouse.benchPos(center)).isAir() && level.getBlockState(MechanicHouse.signPos(center)).isAir(),
			"mechanic: the house does not appear finished");
		return center;
	}

	static void verifyBuilding(ServerLevel level, BlockPos center) {
		ConstructionSite site = MechanicHouseSite.site(level);
		var mechanics = mechanics(level, center);
		SelfTest.check(site != null && site.isUnderConstruction() && site.placedCount() > 0 && site.placedCount() < site.totalSteps(),
			"mechanic: the house goes up block by block (" + (site == null ? 0 : site.placedCount()) + " placed)");
		SelfTest.check(mechanics.size() == 1 && mechanics.getFirst().getItemBySlot(EquipmentSlot.MAINHAND).is(ModItems.WRENCH),
			"mechanic: he is there, wrench in hand, while he builds");
		SelfTest.check(guards(level, center).size() == 1, "mechanic: his guard is there from the start");
	}

	static void verifyBlocked(ServerLevel level, BlockPos center) {
		ConstructionSite site = MechanicHouseSite.site(level);
		SelfTest.check(site != null && site.state() == ConstructionState.PAUSED && site.problem() == ConstructionProblem.OBSTRUCTED
			&& obstruction.equals(site.problemPos()), "mechanic: construction stops at a player's block and reports where");
		SelfTest.check(level.getBlockState(obstruction).is(Blocks.COBBLESTONE), "mechanic: the player's block is not replaced");
		placedBeforeObstruction = site == null ? 0 : site.placedCount();
		// Save/load mid-construction: progress and plan come back exactly.
		if (site != null) {
			TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
			site.save(out);
			ConstructionSite copy = new ConstructionSite();
			boolean loaded = copy.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), out.buildResult()));
			SelfTest.check(loaded && copy.placedCount() == site.placedCount() && copy.stepsDone() == site.stepsDone() && copy.plan().equals(site.plan())
				&& copy.state() == ConstructionState.PAUSED, "mechanic: construction survives save/load");
		}

		// Reload from the saved world data (as after a restart), then clear the way.
		MechanicHouseSite.save(level);
		MechanicHouseSite.forget();
		ConstructionSite reloaded = MechanicHouseSite.site(level);
		SelfTest.check(reloaded != null && reloaded.placedCount() == placedBeforeObstruction, "mechanic: the saved site is picked up again after a restart");
		level.setBlock(obstruction, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
	}

	static void verifyFinished(ServerLevel level, BlockPos center) {
		ConstructionSite site = MechanicHouseSite.site(level);
		SelfTest.check(site != null && site.isComplete(), "mechanic: the house is finished after the obstruction is cleared ("
			+ (site == null ? "none" : site.state() + " " + site.stepsDone() + "/" + site.totalSteps()) + ")");
		if (site == null) {
			return;
		}

		int wrong = 0;
		for (Placement p : site.plan()) {
			if (!ConstructionSite.matches(level.getBlockState(p.pos()), p.state())) {
				wrong++;
			}
		}

		SelfTest.check(wrong == 0, "mechanic: every block of the plan is in place (" + wrong + " missing)");
		long blocks = site.plan().stream().filter(p -> !p.state().isAir()).count();
		SelfTest.check(site.placedCount() <= site.totalSteps() && site.placedCount() >= blocks,
			"mechanic: no block was placed twice across the pause and reload (" + site.placedCount() + " placements for " + blocks + " blocks)");
		SelfTest.check(level.getBlockState(MechanicHouse.benchPos(center)).is(ModBlocks.FABRICATION_BENCH)
			&& level.getBlockState(center.offset(5, 1, 3)).is(Blocks.ANVIL) && level.getBlockState(center.offset(5, 1, -1)).is(Blocks.BLAST_FURNACE),
			"mechanic: workshop with his workstation (Fabrication Bench), anvil and blast furnace");
		SelfTest.check(level.getBlockState(center.offset(-5, 1, 3)).getBlock() instanceof BedBlock && level.getBlockState(center.offset(-5, 1, 4)).getValue(BedBlock.PART) == BedPart.HEAD,
			"mechanic: living room with a whole bed");
		SelfTest.check(level.getBlockState(center.offset(-3, 1, -1)).is(Blocks.POLISHED_ANDESITE_SLAB) && level.getBlockState(MechanicHouse.doorPos(center)).is(Blocks.SPRUCE_DOOR)
			&& level.getBlockState(MechanicHouse.doorPos(center).above()).is(Blocks.SPRUCE_DOOR), "mechanic: trading counter and a working door");
		SelfTest.check(level.getBlockState(MechanicHouse.doorPos(center).north()).isAir() && level.getBlockState(MechanicHouse.doorPos(center).north().above()).isAir()
			&& level.getBlockState(MechanicHouse.doorPos(center).south()).isAir(), "mechanic: the entrance is clear on both sides");
		boolean signed = level.getBlockEntity(MechanicHouse.signPos(center)) instanceof SignBlockEntity sign
			&& !sign.getFrontText().getMessage(0, false).getString().isEmpty();
		int torches = level.getBlockEntity(MechanicHouse.chestPos(center)) instanceof ChestBlockEntity chest ? chest.countItem(Items.TORCH) : -1;
		SelfTest.check(signed && torches == 32 && MechanicHouseSite.isSupplied(), "mechanic: sign written, supply chest stocked once (" + torches + " torches)");
		SelfTest.check(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, MechanicHouse.traderSpot(center)) > 0
			&& level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, MechanicHouse.benchPos(center).north()) > 0, "mechanic: the rooms are lit");
		var mechanics = mechanics(level, center);
		SelfTest.check(mechanics.size() == 1 && mechanics.getFirst().getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(), "mechanic: he puts the wrench away when done");

		// Not maintained: a broken block stays broken (no free blocks by breaking and waiting).
		BlockPos anvil = center.offset(5, 1, 3);
		level.setBlock(anvil, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
	}

	static void verifyNotRebuilt(ServerLevel level, BlockPos center) {
		SelfTest.check(level.getBlockState(center.offset(5, 1, 3)).isAir() && MechanicHouseSite.site(level).isComplete(),
			"mechanic: a finished house is not rebuilt for free after a player takes a block");
		level.setBlock(center.offset(5, 1, 3), Blocks.ANVIL.defaultBlockState(), Block.UPDATE_ALL);
	}

	/** Trades: every Solar Quarry item for sale, priced at or above its crafting value, never sold out. */
	static void verifyTrades(ServerLevel level, BlockPos center) {
		var mechanics = mechanics(level, center);
		if (mechanics.isEmpty()) {
			SelfTest.check(false, "trades: no Mechanic to trade with");
			return;
		}

		List<MerchantOffer> offers = mechanics.getFirst().getOffers();
		var sales = MechanicTrades.sales(level);
		int missing = 0;
		for (Item item : com.solarquarry.registry.ModCreativeTab.orderedItems()) {
			if (MechanicTrades.isForSale(item) && offers.stream().noneMatch(o -> o.getResult().is(item))) {
				missing++;
				SolarQuarry.LOGGER.info("[SelfTest] not for sale: {}", BuiltInRegistries.ITEM.getKey(item));
			}
		}

		SelfTest.check(missing == 0 && sales.size() >= 60, "trades: the Mechanic sells every Solar Quarry item (" + sales.size() + " offers, " + missing + " missing)");
		boolean cheap = false;
		for (MechanicTrades.Sale sale : sales) {
			double paid = sale.emeralds();
			if (paid + 1e-9 < sale.value() * sale.result().getCount()) {
				cheap = true;
				SolarQuarry.LOGGER.info("[SelfTest] sold below value: {} for {} (value {})", sale.result(), paid, sale.value());
			}
		}

		SelfTest.check(!cheap, "trades: nothing is sold below its crafting value");
		int quarry = price(sales, ModBlocks.QUARRY.asItem());
		int elite = price(sales, ModBlocks.SOLAR_PANEL_ELITE.asItem());
		int basic = price(sales, ModBlocks.SOLAR_PANEL_BASIC.asItem());
		SelfTest.check(elite > basic && quarry > basic && price(sales, ModBlocks.BATTERY_ELITE.asItem()) > price(sales, ModBlocks.BATTERY_BASIC.asItem()),
			"trades: advanced parts cost more than basic ones (panel " + basic + " -> " + elite + ", quarry " + quarry + ")");
		MerchantOffer first = offers.stream().filter(o -> !o.getResult().is(Items.EMERALD)).findFirst().orElseThrow();
		for (int i = 0; i < 500; i++) {
			first.increaseUses();
		}

		SelfTest.check(!first.isOutOfStock() && first.getMaxUses() == MechanicTrades.UNLIMITED, "trades: sell offers never run out (500 purchases)");
		SelfTest.check(offers.stream().noneMatch(o -> !o.getResult().is(Items.EMERALD) && MechanicTrades.isBought(o.getResult().getItem())),
			"trades: nothing he buys is also sold");
		SelfTest.check(offers.stream().allMatch(o -> o.getPriceMultiplier() == 0.0F), "trades: prices stay fixed");
		var guard = guards(level, center);
		SelfTest.check(guard.size() == 1 && guard.getFirst().getItemBySlot(EquipmentSlot.CHEST).has(DataComponents.TRIM)
			&& guard.getFirst().getItemBySlot(EquipmentSlot.CHEST).has(DataComponents.DYED_COLOR), "guard: industrial plating with hazard trim");
	}

	private static int price(List<MechanicTrades.Sale> sales, Item item) {
		return sales.stream().filter(s -> s.result().is(item)).mapToInt(s -> s.emeralds() / s.result().getCount()).findFirst().orElse(-1);
	}

	/** Restores normal speed for other tests. */
	static void restoreSpeed() {
		MechanicHouseSite.interval = 4;
		MechanicHouseSite.stepsPerRound = 1;
	}
}
