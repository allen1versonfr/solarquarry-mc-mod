package com.solarquarry.smelting;

import java.util.Optional;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import org.jspecify.annotations.Nullable;

/** Furnace recipe lookups shared by the Auto-Smelter Upgrade and the Super Smelter. Always the game's own smelting recipes. */
public final class Smelting {
	/** Energy for each item the Auto-Smelter Upgrade smelts (before the server's energy multiplier). */
	public static final long AUTO_SMELT_COST = 50;

	/** One smelted item: the result (for one input item) and the recipe's experience per item. */
	public record Result(ItemStack output, float experience) {
	}

	private Smelting() {
	}

	/** The smelting result for one item of {@code input}, or null if no smelting recipe accepts it. */
	public static @Nullable Result smelt(ServerLevel level, ItemStack input) {
		if (input.isEmpty()) {
			return null;
		}

		SingleRecipeInput recipeInput = new SingleRecipeInput(input.copyWithCount(1));
		Optional<RecipeHolder<SmeltingRecipe>> recipe = level.recipeAccess().getRecipeFor(RecipeType.SMELTING, recipeInput, level);
		if (recipe.isEmpty()) {
			return null;
		}

		ItemStack output = recipe.get().value().assemble(recipeInput);
		return output.isEmpty() ? null : new Result(output, recipe.get().value().experience());
	}

	/**
	 * What the Auto-Smelter Upgrade processes: ores and raw metals only (c:ores, c:raw_materials), so stone, sand and
	 * cobblestone come out as they were mined.
	 */
	public static boolean autoSmelts(ItemStack stack) {
		return stack.is(ConventionalItemTags.ORES) || stack.is(ConventionalItemTags.RAW_MATERIALS);
	}
}
