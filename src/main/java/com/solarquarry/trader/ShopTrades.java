package com.solarquarry.trader;

import com.solarquarry.registry.ModBlocks;
import com.solarquarry.registry.ModGear;
import com.solarquarry.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ItemLike;

/**
 * The trader's price list (emeralds). Stock per offer refills every morning. Nothing it sells can be broken down into
 * something it buys for more than it cost, so there is no buy-sell loop that makes emeralds from nothing.
 */
public final class ShopTrades {
	/** Trades per offer before the next morning's restock. */
	public static final int STOCK = 12;

	private ShopTrades() {
	}

	public static List<MerchantOffer> offers() {
		List<MerchantOffer> offers = new ArrayList<>();
		// Selling: parts for a first quarry setup.
		sell(offers, 1, Items.TORCH, 32);
		sell(offers, 3, ModItems.CIRCUIT_BOARD, 2);
		sell(offers, 4, ModItems.PHOTOVOLTAIC_CELL, 2);
		sell(offers, 3, ModBlocks.ENERGY_CABLE, 16);
		sell(offers, 3, ModBlocks.ITEM_PIPE, 16);
		sell(offers, 4, ModBlocks.FABRICATION_BENCH, 1);
		sell(offers, 6, ModItems.UPGRADE_BASE, 1);
		sell(offers, 8, ModBlocks.SOLAR_PANEL_BASIC, 1);
		sell(offers, 8, ModBlocks.BATTERY_BASIC, 1);
		sell(offers, 20, ModItems.SPEED_UPGRADE, 1);
		// Buying: what a quarry digs up.
		buy(offers, Items.COBBLESTONE, 32, 1);
		buy(offers, Items.COBBLED_DEEPSLATE, 24, 1);
		buy(offers, Items.COAL, 16, 1);
		buy(offers, Items.RAW_COPPER, 12, 1);
		buy(offers, Items.RAW_IRON, 8, 1);
		buy(offers, Items.REDSTONE, 16, 1);
		buy(offers, Items.LAPIS_LAZULI, 12, 1);
		buy(offers, Items.RAW_GOLD, 4, 1);
		buy(offers, Items.DIAMOND, 1, 2);
		buy(offers, ModGear.RAW_HELIONITE, 1, 3);
		return offers;
	}

	private static void sell(List<MerchantOffer> offers, int emeralds, ItemLike item, int count) {
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), new ItemStack(item, count), STOCK, 2, 0.05F));
	}

	private static void buy(List<MerchantOffer> offers, ItemLike item, int count, int emeralds) {
		offers.add(new MerchantOffer(new ItemCost(item, count), new ItemStack(Items.EMERALD, emeralds), STOCK, 2, 0.05F));
	}
}
