package com.solarquarry.splitter;

import com.solarquarry.blockentity.ItemPipeBlockEntity;
import com.solarquarry.item.Dismantlable;
import com.solarquarry.registry.ModBlockEntities;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Filtered Splitter: takes items from pipes, hoppers or machines on its input side and sends the items its filter
 * selects out of one face and everything else out of another. It holds a small buffer; an item only leaves when the
 * neighbour accepts it, so a full or missing output never deletes anything (the item waits and the GUI says why).
 * Work per tick is bounded ({@link #ITEMS_PER_PUSH} per output every {@link #PUSH_INTERVAL} ticks), so an item
 * circling through a loop of pipes can never cause unbounded processing.
 */
public class FilteredSplitterBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, ExtendedMenuProvider<BlockPos>, Dismantlable {
	public static final int BUFFER_SLOTS = 9;
	public static final int FILTER_SLOTS = 18;
	public static final int TAG_SLOTS = 2;
	public static final int MAX_TAG_LENGTH = 96;
	public static final int PUSH_INTERVAL = 4;
	public static final int ITEMS_PER_PUSH = 16;
	public static final int DATA_COUNT = 9;
	private static final int[] ALL_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8};
	private static final int[] NO_SLOTS = {};

	/** What happened at an output the last time an item was sent to it. */
	public enum OutputState {
		IDLE,
		OK,
		FULL,
		DISCONNECTED
	}

	private NonNullList<ItemStack> items = NonNullList.withSize(BUFFER_SLOTS, ItemStack.EMPTY);
	private final SimpleContainer filter = new SimpleContainer(FILTER_SLOTS) {
		@Override
		public void setChanged() {
			super.setChanged();
			FilteredSplitterBlockEntity.this.markConfigChanged();
		}
	};
	private final SplitterConfig config = new SplitterConfig();
	private final String[] tags = {"", ""};
	@SuppressWarnings("unchecked")
	private final @Nullable TagKey<Item>[] tagKeys = new TagKey[TAG_SLOTS];
	private OutputState matchState = OutputState.IDLE;
	private OutputState otherState = OutputState.IDLE;
	private long matchedCount;
	private long otherCount;
	private int cooldown;
	/** False until the splitter was oriented once; a splitter placed from a wrenched item keeps its settings. */
	private boolean configured;
	/** Where the player looked when placing it: reset puts the matching output back here. */
	private Direction front = Direction.NORTH;
	private boolean syncDirty;

	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			FilteredSplitterBlockEntity s = FilteredSplitterBlockEntity.this;
			return switch (index) {
				case 0 -> s.config.whitelist() ? 1 : 0;
				case 1 -> s.config.matchComponents() ? 1 : 0;
				case 2 -> s.config.matchFace().ordinal();
				case 3 -> s.config.otherFace().ordinal();
				case 4 -> s.config.inputFace() == null ? 6 : s.config.inputFace().ordinal();
				case 5 -> s.matchState.ordinal();
				case 6 -> s.otherState.ordinal();
				case 7 -> (int) Math.min(0x7FFF, s.matchedCount);
				case 8 -> (int) Math.min(0x7FFF, s.otherCount);
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	public FilteredSplitterBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.FILTERED_SPLITTER, pos, state);
	}

	// ---- configuration ----

	public SplitterConfig config() {
		return this.config;
	}

	public SimpleContainer getFilter() {
		return this.filter;
	}

	public ContainerData getDataAccess() {
		return this.data;
	}

	public String getTag(int index) {
		return index >= 0 && index < TAG_SLOTS ? this.tags[index] : "";
	}

	/** Sets an item tag rule ("#c:ores" or "c:ores"); returns false for an invalid id (the old value is kept). */
	public boolean setTag(int index, String value) {
		if (index < 0 || index >= TAG_SLOTS || value == null) {
			return false;
		}

		String trimmed = value.trim();
		if (trimmed.startsWith("#")) {
			trimmed = trimmed.substring(1);
		}

		if (trimmed.length() > MAX_TAG_LENGTH || (!trimmed.isEmpty() && Identifier.tryParse(trimmed) == null)) {
			return false;
		}

		this.tags[index] = trimmed;
		this.tagKeys[index] = trimmed.isEmpty() ? null : TagKey.create(Registries.ITEM, Identifier.parse(trimmed));
		this.markConfigChanged();
		return true;
	}

	public void toggleWhitelist() {
		this.config.setWhitelist(!this.config.whitelist());
		this.markConfigChanged();
	}

	public void toggleMatchComponents() {
		this.config.setMatchComponents(!this.config.matchComponents());
		this.markConfigChanged();
	}

	public void cycleMatch() {
		this.config.cycleMatch();
		this.facesChanged();
	}

	public void cycleOther() {
		this.config.cycleOther();
		this.facesChanged();
	}

	public void cycleInput() {
		this.config.cycleInput();
		this.facesChanged();
	}

	/** Back to the placement defaults: filter cleared, whitelist, faces relative to the block's front. */
	public void resetToDefaults() {
		for (int i = 0; i < FILTER_SLOTS; i++) {
			this.filter.setItem(i, ItemStack.EMPTY);
		}

		for (int i = 0; i < TAG_SLOTS; i++) {
			this.setTag(i, "");
		}

		this.config.orient(this.front());
		this.matchedCount = 0;
		this.otherCount = 0;
		this.facesChanged();
	}

	public void clearFilter() {
		for (int i = 0; i < FILTER_SLOTS; i++) {
			this.filter.setItem(i, ItemStack.EMPTY);
		}

		this.markConfigChanged();
	}

	/** Called by the block when it is placed: faces relative to where the player looked. */
	public void orient(Direction look) {
		if (this.configured) {
			this.facesChanged(); // keep the saved faces; only bring the block state in line
			return;
		}

		this.front = look;
		this.config.orient(look);
		this.facesChanged();
	}

	private Direction front() {
		return this.front;
	}

	private void facesChanged() {
		this.markConfigChanged();
		if (this.level != null && !this.level.isClientSide()) {
			BlockState state = this.getBlockState();
			if (state.hasProperty(FilteredSplitterBlock.MATCH)) {
				BlockState updated = state.setValue(FilteredSplitterBlock.MATCH, this.config.matchFace()).setValue(FilteredSplitterBlock.OTHER, this.config.otherFace());
				if (updated != state) {
					this.level.setBlock(this.worldPosition, updated, Block.UPDATE_ALL);
				}
			}

			com.solarquarry.pipe.PipeRouter.invalidateCache(); // which faces accept items changed
		}
	}

	private void markConfigChanged() {
		this.configured = true;
		this.syncDirty = true;
		this.setChanged();
	}

	/** Whether the filter lists this item (item templates or tags). */
	public boolean isListed(ItemStack stack) {
		for (int i = 0; i < FILTER_SLOTS; i++) {
			ItemStack entry = this.filter.getItem(i);
			if (!entry.isEmpty() && (this.config.matchComponents() ? ItemStack.isSameItemSameComponents(entry, stack) : entry.is(stack.getItem()))) {
				return true;
			}
		}

		for (TagKey<Item> key : this.tagKeys) {
			if (key != null && stack.is(key)) {
				return true;
			}
		}

		return false;
	}

	/** The face this item will leave through. */
	public Direction outputFor(ItemStack stack) {
		return this.config.outputFor(this.isListed(stack));
	}

	public OutputState matchState() {
		return this.matchState;
	}

	public OutputState otherState() {
		return this.otherState;
	}

	public long matchedCount() {
		return this.matchedCount;
	}

	public long otherCount() {
		return this.otherCount;
	}

	// ---- tick ----

	public static void serverTick(Level level, BlockPos pos, BlockState state, FilteredSplitterBlockEntity splitter) {
		if (level instanceof ServerLevel server) {
			splitter.tick(server);
		}
	}

	private void tick(ServerLevel level) {
		if (--this.cooldown <= 0) {
			this.cooldown = PUSH_INTERVAL;
			this.push(level);
		}

		if (this.syncDirty && level.getGameTime() % 5 == 0) {
			this.syncDirty = false;
			level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/** Sends up to {@link #ITEMS_PER_PUSH} items to each output. Items that cannot leave stay in the buffer. */
	private void push(ServerLevel level) {
		OutputState newMatch = this.matchState == OutputState.OK ? OutputState.IDLE : this.matchState;
		OutputState newOther = this.otherState == OutputState.OK ? OutputState.IDLE : this.otherState;
		int budgetMatch = ITEMS_PER_PUSH;
		int budgetOther = ITEMS_PER_PUSH;
		boolean sawMatch = false;
		boolean sawOther = false;
		for (int i = 0; i < BUFFER_SLOTS; i++) {
			ItemStack stack = this.items.get(i);
			if (stack.isEmpty()) {
				continue;
			}

			Direction face = this.outputFor(stack);
			boolean matching = face == this.config.matchFace();
			int budget = matching ? budgetMatch : budgetOther;
			if (matching) {
				sawMatch = true;
			} else {
				sawOther = true;
			}

			if (budget <= 0) {
				continue;
			}

			int before = stack.getCount();
			OutputState result = this.sendOut(level, face, stack, budget);
			int moved = before - stack.getCount();
			if (moved > 0) {
				this.setChanged();
			}

			if (matching) {
				budgetMatch -= moved;
				this.matchedCount += moved;
				newMatch = moved > 0 ? OutputState.OK : result;
			} else {
				budgetOther -= moved;
				this.otherCount += moved;
				newOther = moved > 0 ? OutputState.OK : result;
			}
		}

		if (!sawMatch && newMatch != OutputState.OK) {
			newMatch = this.peek(level, this.config.matchFace()) ? OutputState.IDLE : OutputState.DISCONNECTED;
		}

		if (!sawOther && newOther != OutputState.OK) {
			newOther = this.peek(level, this.config.otherFace()) ? OutputState.IDLE : OutputState.DISCONNECTED;
		}

		if (newMatch != this.matchState || newOther != this.otherState) {
			this.matchState = newMatch;
			this.otherState = newOther;
			this.syncDirty = true;
		}
	}

	/** Whether something that can take items is attached to this face (loaded chunks only). */
	private boolean peek(ServerLevel level, Direction face) {
		BlockPos target = this.worldPosition.relative(face);
		if (!level.isLoaded(target)) {
			return false;
		}

		BlockEntity be = level.getBlockEntity(target);
		if (be instanceof ItemPipeBlockEntity) {
			return true;
		}

		Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, target, face.getOpposite());
		return storage != null && storage.supportsInsertion();
	}

	/** Moves part of {@code stack} into the neighbour on {@code face}; shrinks the stack by what was accepted. */
	private OutputState sendOut(ServerLevel level, Direction face, ItemStack stack, int budget) {
		BlockPos target = this.worldPosition.relative(face);
		if (!level.isLoaded(target)) {
			return OutputState.DISCONNECTED;
		}

		BlockEntity be = level.getBlockEntity(target);
		if (be instanceof ItemPipeBlockEntity pipe) {
			// Pipes take a whole batch or nothing, and only when a destination for it exists.
			int count = Math.min(Math.min(stack.getCount(), budget), pipe.tier().batch());
			if (pipe.acceptFromSource(stack.copyWithCount(count), face.getOpposite())) {
				stack.shrink(count);
				return OutputState.OK;
			}

			return OutputState.FULL;
		}

		Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, target, face.getOpposite());
		if (storage == null || !storage.supportsInsertion()) {
			return OutputState.DISCONNECTED;
		}

		try (Transaction tx = Transaction.openOuter()) {
			long inserted = storage.insert(ItemVariant.of(stack), Math.min(stack.getCount(), budget), tx);
			if (inserted > 0) {
				tx.commit();
				stack.shrink((int) inserted);
				return OutputState.OK;
			}
		}

		return OutputState.FULL;
	}

	// ---- container ----

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.solarquarry.filtered_splitter");
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return this.items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	public int getContainerSize() {
		return BUFFER_SLOTS;
	}

	@Override
	public int[] getSlotsForFace(Direction side) {
		return this.config.acceptsFrom(side) ? ALL_SLOTS : NO_SLOTS;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
		return this.config.acceptsFrom(side);
	}

	/** Nothing is pulled out by automation: items only leave through the outputs. */
	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		return false;
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return new FilteredSplitterMenu(containerId, inventory, this);
	}

	@Override
	public BlockPos getScreenOpeningData(ServerPlayer player) {
		return this.worldPosition;
	}

	// ---- persistence and sync ----

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.items = NonNullList.withSize(BUFFER_SLOTS, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, this.items);
		this.readConfig(input);
		this.matchedCount = input.getLongOr("Matched", 0);
		this.otherCount = input.getLongOr("Other", 0);
	}

	private void readConfig(ValueInput input) {
		Direction match = Direction.from3DDataValue(input.getIntOr("MatchFace", Direction.NORTH.get3DDataValue()));
		Direction other = Direction.from3DDataValue(input.getIntOr("OtherFace", Direction.EAST.get3DDataValue()));
		int in = input.getIntOr("InputFace", -1);
		this.config.set(match, other, in < 0 || in > 5 ? null : Direction.from3DDataValue(in));
		this.configured = input.getBooleanOr("Configured", false);
		this.front = Direction.from3DDataValue(input.getIntOr("Front", match.get3DDataValue()));
		this.config.setWhitelist(input.getBooleanOr("Whitelist", true));
		this.config.setMatchComponents(input.getBooleanOr("MatchComponents", false));
		for (int i = 0; i < TAG_SLOTS; i++) {
			String tag = input.getStringOr("Tag" + i, "");
			this.tags[i] = "";
			this.tagKeys[i] = null;
			if (!tag.isEmpty() && Identifier.tryParse(tag) != null) {
				this.tags[i] = tag;
				this.tagKeys[i] = TagKey.create(Registries.ITEM, Identifier.parse(tag));
			}
		}

		input.child("Filter").ifPresent(child -> {
			NonNullList<ItemStack> list = NonNullList.withSize(FILTER_SLOTS, ItemStack.EMPTY);
			ContainerHelper.loadAllItems(child, list);
			for (int i = 0; i < FILTER_SLOTS; i++) {
				this.filter.getItems().set(i, list.get(i));
			}
		});
		this.matchState = OutputState.values()[Math.clamp(input.getIntOr("MatchState", 0), 0, OutputState.values().length - 1)];
		this.otherState = OutputState.values()[Math.clamp(input.getIntOr("OtherState", 0), 0, OutputState.values().length - 1)];
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items);
		this.writeConfig(output);
		output.putLong("Matched", this.matchedCount);
		output.putLong("Other", this.otherCount);
	}

	private void writeConfig(ValueOutput output) {
		output.putInt("MatchFace", this.config.matchFace().get3DDataValue());
		output.putInt("OtherFace", this.config.otherFace().get3DDataValue());
		output.putInt("InputFace", this.config.inputFace() == null ? -1 : this.config.inputFace().get3DDataValue());
		output.putBoolean("Configured", this.configured);
		output.putInt("Front", this.front.get3DDataValue());
		output.putBoolean("Whitelist", this.config.whitelist());
		output.putBoolean("MatchComponents", this.config.matchComponents());
		for (int i = 0; i < TAG_SLOTS; i++) {
			output.putString("Tag" + i, this.tags[i]);
		}

		ContainerHelper.saveAllItems(output.child("Filter"), this.filter.getItems(), true);
		output.putInt("MatchState", this.matchState.ordinal());
		output.putInt("OtherState", this.otherState.ordinal());
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		this.writeConfig(output);
		return output.buildResult();
	}

	@Override
	public CompoundTag extractForPickup(HolderLookup.Provider registries) {
		CompoundTag data = this.saveCustomOnly(registries);
		this.items.clear();
		this.setChanged();
		return data;
	}
}
