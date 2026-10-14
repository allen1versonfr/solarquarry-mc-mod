package com.solarquarry.crafting;

import com.solarquarry.SolarQuarry;
import com.solarquarry.registry.ModCreativeTab;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;

/**
 * The Fabrication Bench's catalogue: every Solar Quarry crafting recipe, with its ingredients grouped and counted.
 * Crafting takes the ingredients straight from the player's inventory, on the server, with the game's own recipes.
 */
public final class BenchRecipes {
	/** Largest batch one click may ask for. */
	public static final int MAX_BATCH = 64;

	/** One catalogue entry: the recipe id, what it makes and what it needs. */
	public record Entry(String id, ItemStack result, List<Need> needs) {
	}

	/** One requirement: any of these items (item ids), this many. */
	public record Need(List<String> options, int count) {
	}

	private record Requirement(Ingredient ingredient, int count) {
	}

	private BenchRecipes() {
	}

	/** Catalogue in creative tab order. */
	public static List<Entry> catalogue(ServerLevel level) {
		List<Entry> entries = new ArrayList<>();
		for (RecipeHolder<?> holder : level.recipeAccess().getRecipes()) {
			if (!holder.id().identifier().getNamespace().equals(SolarQuarry.MOD_ID) || !(holder.value() instanceof CraftingRecipe recipe)) {
				continue;
			}

			ItemStack result = recipe.assemble(CraftingInput.EMPTY);
			if (result.isEmpty()) {
				continue;
			}

			List<Need> needs = new ArrayList<>();
			for (Requirement requirement : requirements(recipe)) {
				List<String> options = requirement.ingredient().items()
					.map(item -> BuiltInRegistries.ITEM.getKey(item.value()).toString())
					.limit(16)
					.toList();
				needs.add(new Need(options, requirement.count()));
			}

			entries.add(new Entry(holder.id().identifier().toString(), result, needs));
		}

		List<String> order = ModCreativeTab.ORDER;
		entries.sort(Comparator.comparingInt((Entry e) -> {
			int index = order.indexOf(BuiltInRegistries.ITEM.getKey(e.result().getItem()).getPath());
			return index < 0 ? Integer.MAX_VALUE : index;
		}).thenComparing(Entry::id));
		return entries;
	}

	private static List<Requirement> requirements(Recipe<?> recipe) {
		Map<Ingredient, Integer> counts = new LinkedHashMap<>();
		for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
			counts.merge(ingredient, 1, Integer::sum);
		}

		List<Requirement> list = new ArrayList<>(counts.size());
		counts.forEach((ingredient, count) -> list.add(new Requirement(ingredient, count)));
		return list;
	}

	private static @Nullable CraftingRecipe find(ServerLevel level, String id) {
		Identifier identifier = Identifier.tryParse(id);
		if (identifier == null || !identifier.getNamespace().equals(SolarQuarry.MOD_ID)) {
			return null;
		}

		ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, identifier);
		return level.recipeAccess().byKey(key).map(RecipeHolder::value).orElse(null) instanceof CraftingRecipe recipe ? recipe : null;
	}

	/**
	 * Crafts up to {@code times} of the recipe from the player's inventory.
	 * @return how many were crafted (stops early when the ingredients run out)
	 */
	public static int craft(ServerPlayer player, String id, int times) {
		ServerLevel level = player.level();
		CraftingRecipe recipe = find(level, id);
		if (recipe == null) {
			return 0;
		}

		List<Requirement> requirements = requirements(recipe);
		Inventory inventory = player.getInventory();
		int slots = Inventory.INVENTORY_SIZE;
		int crafted = 0;
		for (int round = 0; round < Math.clamp(times, 1, MAX_BATCH); round++) {
			int[] available = new int[slots];
			boolean[][] matches = new boolean[requirements.size()][slots];
			int[] needed = new int[requirements.size()];
			for (int slot = 0; slot < slots; slot++) {
				ItemStack stack = inventory.getItem(slot);
				available[slot] = stack.getCount();
				for (int r = 0; r < requirements.size(); r++) {
					matches[r][slot] = !stack.isEmpty() && requirements.get(r).ingredient().test(stack);
				}
			}

			for (int r = 0; r < requirements.size(); r++) {
				needed[r] = requirements.get(r).count();
			}

			int[] take = IngredientPlan.plan(available, matches, needed);
			if (take == null) {
				break;
			}

			for (int slot = 0; slot < slots; slot++) {
				if (take[slot] > 0) {
					ItemStack stack = inventory.getItem(slot);
					ItemStack remainder = stack.getItem().getCraftingRemainder() != null ? stack.getItem().getCraftingRemainder().create() : ItemStack.EMPTY;
					stack.shrink(take[slot]);
					if (!remainder.isEmpty()) {
						for (int i = 0; i < take[slot]; i++) {
							inventory.placeItemBackInInventory(remainder.copy());
						}
					}
				}
			}

			inventory.placeItemBackInInventory(recipe.assemble(CraftingInput.EMPTY));
			crafted++;
		}

		if (crafted > 0) {
			inventory.setChanged();
			player.containerMenu.broadcastChanges();
			level.playSound(null, player.blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.6F, 1.1F);
		}

		return crafted;
	}
}
