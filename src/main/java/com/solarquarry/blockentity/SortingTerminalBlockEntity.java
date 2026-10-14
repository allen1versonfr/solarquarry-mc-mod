package com.solarquarry.blockentity;

import com.solarquarry.menu.SortingTerminalMenu;
import com.solarquarry.pipe.PipeRouter;
import com.solarquarry.pipe.SortRule;
import com.solarquarry.pipe.StorageScan;
import com.solarquarry.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Sorting Terminal: attached to an item-pipe network, it registers the network's inventories as destinations and
 * decides which items go where (see {@link SortRule} and {@link PipeRouter}). When a terminal is on a network, only
 * its registered destinations receive items; anything no destination accepts stays in the source machine.
 */
public class SortingTerminalBlockEntity extends BlockEntity implements ExtendedMenuProvider<BlockPos>, com.solarquarry.item.Dismantlable {
	public static final int MAX_DESTINATIONS = 16;
	private final List<SortRule> rules = new ArrayList<>();

	public SortingTerminalBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SORTING_TERMINAL, pos, state);
	}

	public List<SortRule> getRules() {
		return this.rules;
	}

	public @Nullable SortRule getRule(BlockPos pos) {
		long key = pos.asLong();
		for (SortRule rule : this.rules) {
			if (rule.pos() == key) {
				return rule;
			}
		}

		return null;
	}

	/** Registers an inventory as a destination (ALLOW with an empty list until configured). False when full or already registered. */
	public boolean register(BlockPos pos) {
		if (this.getRule(pos) != null || this.rules.size() >= MAX_DESTINATIONS) {
			return false;
		}

		this.rules.add(new SortRule(pos.asLong()));
		this.changed();
		return true;
	}

	public boolean unregister(BlockPos pos) {
		boolean removed = this.rules.removeIf(rule -> rule.pos() == pos.asLong());
		if (removed) {
			this.changed();
		}

		return removed;
	}

	/** Call after editing a rule: saves and drops cached routes so the next item uses the new rules. */
	public void changed() {
		this.setChanged();
		PipeRouter.invalidateCache();
	}

	/** Router rank for delivering {@code stack} into the inventory at {@code target}; 0 = not allowed. */
	public int rankFor(BlockPos target, ItemStack stack) {
		SortRule rule = this.getRule(target);
		if (rule == null) {
			return 0;
		}

		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		return rule.rank(id, tag -> hasTag(stack, tag));
	}

	private static boolean hasTag(ItemStack stack, String tag) {
		Identifier id = Identifier.tryParse(tag);
		return id != null && stack.is(TagKey.create(Registries.ITEM, id));
	}

	/** Inventories on the terminal's pipe network, nearest first. */
	public List<BlockPos> discover() {
		return this.level == null ? List.of() : StorageScan.inventoriesOnNetwork(this.level, this.worldPosition);
	}

	/** True if no registered destination would take an item of this kind (for the GUI warning). */
	public boolean hasFallback() {
		return this.rules.stream().anyMatch(rule -> rule.mode() == SortRule.Mode.FALLBACK);
	}

	public static ItemStack stackOf(String id) {
		Identifier key = Identifier.tryParse(id);
		if (key == null || id.isEmpty()) {
			return ItemStack.EMPTY;
		}

		Item item = BuiltInRegistries.ITEM.getOptional(key).orElse(null);
		return item == null ? ItemStack.EMPTY : new ItemStack(item);
	}

	public boolean stillValid(Player player) {
		return !this.isRemoved() && this.level != null && this.level.getBlockEntity(this.worldPosition) == this
			&& player.isWithinBlockInteractionRange(this.worldPosition, 4.0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ValueOutput.ValueOutputList list = output.childrenList("Destinations");
		for (SortRule rule : this.rules) {
			ValueOutput entry = list.addChild();
			entry.putLong("Pos", rule.pos());
			entry.putInt("Mode", rule.mode().ordinal());
			entry.putInt("Priority", rule.priority());
			for (int i = 0; i < SortRule.MAX_ITEMS; i++) {
				entry.putString("Item" + i, rule.items().get(i));
			}

			for (int i = 0; i < SortRule.MAX_TAGS; i++) {
				entry.putString("Tag" + i, rule.tags().get(i));
			}
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.rules.clear();
		for (ValueInput entry : input.childrenListOrEmpty("Destinations")) {
			if (this.rules.size() >= MAX_DESTINATIONS) {
				break;
			}

			long pos = entry.getLongOr("Pos", Long.MIN_VALUE);
			if (pos == Long.MIN_VALUE || this.getRule(BlockPos.of(pos)) != null) {
				continue;
			}

			SortRule rule = new SortRule(pos);
			rule.setMode(SortRule.Mode.byOrdinal(entry.getIntOr("Mode", 0)));
			rule.setPriority(entry.getIntOr("Priority", 0));
			for (int i = 0; i < SortRule.MAX_ITEMS; i++) {
				String id = entry.getStringOr("Item" + i, "");
				rule.setItem(i, stackOf(id).isEmpty() ? "" : id);
			}

			for (int i = 0; i < SortRule.MAX_TAGS; i++) {
				rule.setTag(i, entry.getStringOr("Tag" + i, ""));
			}

			this.rules.add(rule);
		}
	}

	@Override
	public net.minecraft.nbt.CompoundTag extractForPickup(net.minecraft.core.HolderLookup.Provider registries) {
		return this.saveCustomOnly(registries);
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.solarquarry.sorting_terminal");
	}

	@Override
	public BlockPos getScreenOpeningData(ServerPlayer player) {
		return this.worldPosition;
	}

	@Override
	public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new SortingTerminalMenu(containerId, inventory, this);
	}
}
