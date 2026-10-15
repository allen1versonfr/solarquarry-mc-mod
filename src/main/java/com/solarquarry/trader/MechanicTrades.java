package com.solarquarry.trader;

import com.solarquarry.SolarQuarry;
import com.solarquarry.registry.ModCreativeTab;
import com.solarquarry.registry.ModGear;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ItemLike;

/**
 * The Mechanic's price list, built from the game's own recipes so every Solar Quarry item is covered and priced
 * against what it costs to craft.
 *
 * <p>Each item's crafting value (in emeralds) is worked out from its recipe, recursively, down to base materials with
 * fixed values ({@link #baseValue}). The Mechanic sells for {@link #MARKUP} times that value, so buying is always a
 * shortcut, never cheaper than crafting. Sell offers never run out ({@link #UNLIMITED} uses, no price changes). The
 * few things he buys (ores and stone a quarry digs up) pay well below their base value and are never sold, so there is
 * no buy-sell loop. Spawn eggs, ore blocks and the two recipe-less structure finds are not for sale.
 */
public final class MechanicTrades {
	/** Bumped whenever the list changes; traders with an older list rebuild theirs. */
	public static final int VERSION = 3;
	public static final double MARKUP = 1.5;
	/** Uses per sell offer: never reached in practice, so sell offers never sell out. */
	public static final int UNLIMITED = Integer.MAX_VALUE;
	/** Buy offers: per item per day (restocked every morning). */
	public static final int BUY_STOCK = 16;
	/** Value of an ingredient nothing tells us about. */
	private static final double UNKNOWN = 0.5;

	private MechanicTrades() {
	}

	/** One priced sale: what the Mechanic sells and the crafting value it was priced from. */
	public record Sale(ItemStack result, ItemCost cost, java.util.Optional<ItemCost> costB, double value) {
		Sale(ItemStack result, ItemCost cost, double value) {
			this(result, cost, java.util.Optional.empty(), value);
		}

		/** Emeralds the whole sale costs (both price slots). */
		public int emeralds() {
			return emeraldCost(this.cost) + this.costB.map(MechanicTrades::emeraldCost).orElse(0);
		}
	}

	/** Everything the Mechanic sells and buys. */
	public static List<MerchantOffer> offers(ServerLevel level) {
		List<MerchantOffer> offers = new ArrayList<>();
		for (Sale sale : sales(level)) {
			offers.add(new MerchantOffer(sale.cost(), sale.costB(), sale.result(), UNLIMITED, 1, 0.0F));
		}

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

	/** Items the Mechanic buys (never sold). */
	public static boolean isBought(Item item) {
		return item == Items.COBBLESTONE || item == Items.COBBLED_DEEPSLATE || item == Items.COAL || item == Items.RAW_COPPER || item == Items.RAW_IRON
			|| item == Items.REDSTONE || item == Items.LAPIS_LAZULI || item == Items.RAW_GOLD || item == Items.DIAMOND || item == ModGear.RAW_HELIONITE;
	}

	/**
	 * Whether a Solar Quarry item is for sale: not spawn eggs, ore blocks or what he buys, and not the two rare
	 * structure finds that have no recipe (Prototype Battery, Emergency Energy Injector): selling those would make a
	 * rare find ordinary and the injector a renewable energy source.
	 */
	public static boolean isForSale(Item item) {
		String path = BuiltInRegistries.ITEM.getKey(item).getPath();
		return !path.endsWith("_spawn_egg") && !path.endsWith("_ore") && !isBought(item)
			&& item != com.solarquarry.registry.ModItems.PROTOTYPE_BATTERY && item != com.solarquarry.registry.ModItems.EMERGENCY_ENERGY_INJECTOR;
	}

	/** Priced sale for every Solar Quarry item that is for sale, in creative tab order. */
	public static List<Sale> sales(ServerLevel level) {
		Values values = new Values(level);
		List<Sale> sales = new ArrayList<>();
		for (Item item : ModCreativeTab.orderedItems()) {
			if (!isForSale(item)) {
				continue;
			}

			double value = values.of(item);
			sales.add(price(item, value));
		}

		return sales;
	}

	/** Turns a crafting value into an offer: several cheap items per emerald, emeralds, or emerald blocks above 64 (both price slots when needed). */
	static Sale price(Item item, double value) {
		double unit = Math.max(0.01, value) * MARKUP;
		int maxStack = new ItemStack(item).getMaxStackSize();
		if (unit < 1.0) {
			int count = Math.clamp((int) Math.floor(1.0 / unit), 1, maxStack);
			return new Sale(new ItemStack(item, count), new ItemCost(Items.EMERALD, 1), value);
		}

		int emeralds = (int) Math.ceil(unit);
		if (emeralds <= 64) {
			return new Sale(new ItemStack(item), new ItemCost(Items.EMERALD, emeralds), value);
		}

		// Above 64 emerald blocks the rest goes into the trade's second price slot (two slots: up to 128 blocks).
		int blocks = Math.min(128, (int) Math.ceil(emeralds / 9.0));
		if (blocks <= 64) {
			return new Sale(new ItemStack(item), new ItemCost(Items.EMERALD_BLOCK, blocks), value);
		}

		return new Sale(new ItemStack(item), new ItemCost(Items.EMERALD_BLOCK, 64), java.util.Optional.of(new ItemCost(Items.EMERALD_BLOCK, blocks - 64)), value);
	}

	/** Emeralds a sale costs. */
	public static int emeraldCost(ItemCost cost) {
		return cost.itemStack().is(Items.EMERALD_BLOCK) ? cost.count() * 9 : cost.count();
	}

	private static void buy(List<MerchantOffer> offers, ItemLike item, int count, int emeralds) {
		offers.add(new MerchantOffer(new ItemCost(item, count), new ItemStack(Items.EMERALD, emeralds), BUY_STOCK, 2, 0.0F));
	}

	/** Fixed values of base materials (emeralds per item); everything else comes from recipes. */
	static double baseValue(Item item) {
		Map<Item, Double> base = BASE;
		Double v = base.get(item);
		if (v != null) {
			return v;
		}

		ItemStack stack = new ItemStack(item);
		if (stack.is(ItemTags.LOGS)) {
			return 0.12;
		}

		if (stack.is(ItemTags.PLANKS)) {
			return 0.03;
		}

		if (stack.is(ItemTags.WOOL)) {
			return 0.1;
		}

		if (stack.is(ItemTags.SAPLINGS)) {
			return 0.05;
		}

		return -1;
	}

	private static final Map<Item, Double> BASE = new HashMap<>();

	static {
		BASE.put(Items.COBBLESTONE, 0.03);
		BASE.put(Items.COBBLED_DEEPSLATE, 0.03);
		BASE.put(Items.STONE, 0.04);
		BASE.put(Items.DEEPSLATE, 0.04);
		BASE.put(Items.SMOOTH_STONE, 0.05);
		BASE.put(Items.SAND, 0.03);
		BASE.put(Items.RED_SAND, 0.03);
		BASE.put(Items.GRAVEL, 0.03);
		BASE.put(Items.DIRT, 0.02);
		BASE.put(Items.CLAY_BALL, 0.03);
		BASE.put(Items.GLASS, 0.06);
		BASE.put(Items.IRON_INGOT, 0.6);
		BASE.put(Items.IRON_NUGGET, 0.067);
		BASE.put(Items.RAW_IRON, 0.5);
		BASE.put(Items.GOLD_INGOT, 1.2);
		BASE.put(Items.GOLD_NUGGET, 0.134);
		BASE.put(Items.RAW_GOLD, 1.0);
		BASE.put(Items.COPPER_INGOT, 0.25);
		BASE.put(Items.RAW_COPPER, 0.2);
		BASE.put(Items.REDSTONE, 0.15);
		BASE.put(Items.LAPIS_LAZULI, 0.2);
		BASE.put(Items.DIAMOND, 4.0);
		BASE.put(Items.EMERALD, 1.0);
		BASE.put(Items.COAL, 0.12);
		BASE.put(Items.CHARCOAL, 0.12);
		BASE.put(Items.AMETHYST_SHARD, 0.4);
		BASE.put(Items.ENDER_PEARL, 1.5);
		BASE.put(Items.OBSIDIAN, 0.6);
		BASE.put(Items.LEATHER, 0.3);
		BASE.put(Items.STRING, 0.1);
		BASE.put(Items.STICK, 0.02);
		BASE.put(Items.SLIME_BALL, 0.3);
		BASE.put(Items.BONE_MEAL, 0.05);
		BASE.put(Items.SUGAR_CANE, 0.05);
		BASE.put(Items.KELP, 0.02);
		BASE.put(Items.FEATHER, 0.05);
		BASE.put(Items.FLINT, 0.05);
		BASE.put(Items.PRISMARINE_SHARD, 0.2);
		BASE.put(Items.ECHO_SHARD, 3.0);
		BASE.put(ModGear.RAW_HELIONITE, 4.0);
		BASE.put(ModGear.HELIONITE_INGOT, 5.0);
	}

	/** Recipe-based crafting values, memoised for one price-list build. */
	static final class Values {
		private final Map<Item, List<CraftingRecipe>> recipesByResult = new IdentityHashMap<>();
		private final Map<Item, Integer> resultCounts = new IdentityHashMap<>();
		private final Map<Item, Double> memo = new IdentityHashMap<>();
		private final Map<Ingredient, Double> ingredientMemo = new HashMap<>();
		private final Set<Item> inProgress = new HashSet<>();

		Values(ServerLevel level) {
			for (RecipeHolder<?> holder : level.recipeAccess().getRecipes()) {
				if (!(holder.value() instanceof CraftingRecipe recipe)) {
					continue;
				}

				ItemStack result;
				try {
					result = recipe.assemble(CraftingInput.EMPTY);
				} catch (RuntimeException e) {
					continue; // special recipes (dyeing, repairing) need real input
				}

				if (result.isEmpty() || recipe.placementInfo().isImpossibleToPlace()) {
					continue;
				}

				// Prefer Solar Quarry's own recipe for its items, but any recipe can price a vanilla part.
				if (BuiltInRegistries.ITEM.getKey(result.getItem()).getNamespace().equals(SolarQuarry.MOD_ID)
					&& !holder.id().identifier().getNamespace().equals(SolarQuarry.MOD_ID)) {
					continue;
				}

				this.recipesByResult.computeIfAbsent(result.getItem(), k -> new ArrayList<>()).add(recipe);
				this.resultCounts.putIfAbsent(result.getItem(), result.getCount());
			}
		}

		double of(Item item) {
			Double known = this.memo.get(item);
			if (known != null) {
				return known;
			}

			double base = baseValue(item);
			if (base >= 0) {
				this.memo.put(item, base);
				return base;
			}

			if (!this.inProgress.add(item)) {
				return Double.POSITIVE_INFINITY; // a cycle (ingot <-> block): price from the other side
			}

			double best = Double.POSITIVE_INFINITY;
			for (CraftingRecipe recipe : this.recipesByResult.getOrDefault(item, List.of())) {
				ItemStack result;
				try {
					result = recipe.assemble(CraftingInput.EMPTY);
				} catch (RuntimeException e) {
					continue;
				}

				double sum = 0;
				for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
					sum += this.ingredient(ingredient);
				}

				best = Math.min(best, sum / Math.max(1, result.getCount()));
			}

			this.inProgress.remove(item);
			double value = Double.isInfinite(best) ? UNKNOWN : best;
			this.memo.put(item, value);
			return value;
		}

		private double ingredient(Ingredient ingredient) {
			Double known = this.ingredientMemo.get(ingredient);
			if (known != null) {
				return known;
			}

			double best = Double.POSITIVE_INFINITY;
			for (Holder<Item> option : BuiltInRegistries.ITEM.listElements().filter(ingredient::acceptsItem).toList()) {
				best = Math.min(best, this.of(option.value()));
			}

			double value = Double.isInfinite(best) ? UNKNOWN : best;
			this.ingredientMemo.put(ingredient, value);
			return value;
		}
	}
}
