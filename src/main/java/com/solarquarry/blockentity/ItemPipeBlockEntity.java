package com.solarquarry.blockentity;

import com.mojang.logging.LogUtils;
import com.solarquarry.block.ItemPipeBlock;
import com.solarquarry.menu.FilterPipeMenu;
import com.solarquarry.pipe.FilterResult;
import com.solarquarry.pipe.PipeRouter;
import com.solarquarry.pipe.TravelingItem;
import com.solarquarry.registry.ModBlockEntities;
import com.solarquarry.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ItemPipeBlockEntity extends BlockEntity implements ExtendedMenuProvider<BlockPos> {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final int TRAVEL_TICKS = 10;
	public static final int CENTER_TICKS = TRAVEL_TICKS / 2;
	public static final int MAX_ITEMS = 8;
	public static final int MAX_BATCH = 16;
	public static final int FILTER_SLOTS = 9;
	public static final int TAG_SLOTS = 3;
	public static final int MAX_TAG_LENGTH = 96;
	public static final int MIN_PRIORITY = -5;
	public static final int MAX_PRIORITY = 5;
	private static final int RETRY_TICKS = 10;
	private static final int NO_ROUTE_RETRY_TICKS = 20;

	private final List<TravelingItem> items = new ArrayList<>();
	private final SimpleContainer filter = new SimpleContainer(FILTER_SLOTS) {
		@Override
		public void setChanged() {
			super.setChanged();
			ItemPipeBlockEntity.this.setChanged();
			PipeRouter.invalidateCache();
		}
	};
	private boolean whitelist;
	private boolean matchComponents;
	private int priority;
	private final String[] tags = {"", "", ""};
	@SuppressWarnings("unchecked")
	private final @Nullable TagKey<Item>[] tagKeys = new TagKey[TAG_SLOTS];
	private boolean syncDirty;
	private long lastSoundTick = -100;
	/** Client only: game time at which the last item snapshot was received. */
	public long clientSnapshotTime;

	private final ContainerData filterData = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> ItemPipeBlockEntity.this.whitelist ? 1 : 0;
				case 1 -> ItemPipeBlockEntity.this.matchComponents ? 1 : 0;
				case 2 -> ItemPipeBlockEntity.this.priority - MIN_PRIORITY;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int getCount() {
			return 3;
		}
	};

	public ItemPipeBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.ITEM_PIPE, pos, state);
	}

	public boolean isFilter() {
		return this.getBlockState().getBlock() instanceof ItemPipeBlock pipe && pipe.isFilter();
	}

	public List<TravelingItem> getItems() {
		return this.items;
	}

	public SimpleContainer getFilter() {
		return this.filter;
	}

	public ContainerData getFilterData() {
		return this.filterData;
	}

	public boolean isWhitelist() {
		return this.whitelist;
	}

	public void toggleWhitelist() {
		this.whitelist = !this.whitelist;
		this.setChanged();
		PipeRouter.invalidateCache();
	}

	public void toggleMatchComponents() {
		this.matchComponents = !this.matchComponents;
		this.setChanged();
		PipeRouter.invalidateCache();
	}

	public void setWhitelist(boolean value) {
		if (value != this.whitelist) {
			this.toggleWhitelist();
		}
	}

	public void setMatchComponents(boolean value) {
		if (value != this.matchComponents) {
			this.toggleMatchComponents();
		}
	}

	public boolean isMatchComponents() {
		return this.matchComponents;
	}

	public int getPriority() {
		return this.isFilter() ? this.priority : 0;
	}

	public void setPriority(int value) {
		int clamped = Math.max(MIN_PRIORITY, Math.min(MAX_PRIORITY, value));
		if (clamped != this.priority) {
			this.priority = clamped;
			this.setChanged();
			PipeRouter.invalidateCache();
		}
	}

	public String getTag(int index) {
		return index >= 0 && index < TAG_SLOTS ? this.tags[index] : "";
	}

	/**
	 * Sets one item-tag rule (for example {@code c:ores}). Empty clears it. Invalid identifiers are rejected.
	 * @return true if the value was accepted
	 */
	public boolean setTag(int index, String value) {
		if (index < 0 || index >= TAG_SLOTS || value == null) {
			return false;
		}

		String trimmed = value.trim();
		if (trimmed.startsWith("#")) {
			trimmed = trimmed.substring(1);
		}

		if (trimmed.length() > MAX_TAG_LENGTH) {
			return false;
		}

		if (!trimmed.isEmpty() && Identifier.tryParse(trimmed) == null) {
			return false;
		}

		this.tags[index] = trimmed;
		this.tagKeys[index] = trimmed.isEmpty() ? null : TagKey.create(Registries.ITEM, Identifier.parse(trimmed));
		this.syncDirty = true;
		this.setChanged();
		PipeRouter.invalidateCache();
		return true;
	}

	public FilterResult testFilter(ItemStack stack) {
		if (!this.isFilter()) {
			return FilterResult.PASS;
		}

		boolean listed = false;
		for (int i = 0; i < FILTER_SLOTS; i++) {
			ItemStack entry = this.filter.getItem(i);
			if (entry.isEmpty()) {
				continue;
			}

			if (this.matchComponents ? ItemStack.isSameItemSameComponents(entry, stack) : entry.is(stack.getItem())) {
				listed = true;
				break;
			}
		}

		for (int i = 0; i < TAG_SLOTS && !listed; i++) {
			TagKey<Item> key = this.tagKeys[i];
			if (key != null && stack.is(key)) {
				listed = true;
			}
		}

		if (this.whitelist) {
			return listed ? FilterResult.PASS_MATCHED : FilterResult.BLOCKED;
		}

		return listed ? FilterResult.BLOCKED : FilterResult.PASS;
	}

	public boolean hasRoom() {
		return this.items.size() < MAX_ITEMS;
	}

	/**
	 * Accepts a batch from a machine (all or nothing). The item only enters when a destination that
	 * accepts it exists, so the machine keeps anything that cannot be delivered right now.
	 */
	public boolean acceptFromSource(ItemStack stack, Direction entryFace) {
		if (this.level == null || stack.isEmpty() || !this.hasRoom()) {
			return false;
		}

		FilterResult result = this.testFilter(stack);
		if (result == FilterResult.BLOCKED) {
			return false;
		}

		List<Direction> route = PipeRouter.findRouteCached(this.level, this.worldPosition, stack, result == FilterResult.PASS_MATCHED);
		if (route == null || route.isEmpty()) {
			return false;
		}

		this.items.add(new TravelingItem(stack.copy(), entryFace, new java.util.ArrayList<>(route)));
		this.markForSync();
		return true;
	}

	/** Accepts an item handed over by a neighbouring pipe, keeping the route it is following. */
	private boolean acceptFromPipe(ItemStack stack, Direction entryFace, List<Direction> remainingRoute) {
		if (!this.hasRoom() || this.testFilter(stack) == FilterResult.BLOCKED) {
			return false;
		}

		this.items.add(new TravelingItem(stack, entryFace, remainingRoute));
		this.markForSync();
		return true;
	}

	private void markForSync() {
		this.syncDirty = true;
		this.setChanged();
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, ItemPipeBlockEntity pipe) {
		if (level instanceof ServerLevel serverLevel) {
			pipe.tickServer(serverLevel, state);
		}
	}

	private void tickServer(ServerLevel level, BlockState state) {
		if (!this.items.isEmpty()) {
			List<TravelingItem> snapshot = new ArrayList<>(this.items);
			for (TravelingItem item : snapshot) {
				if (this.tickItem(level, item)) {
					this.items.remove(item);
					this.markForSync();
				}
			}
		}

		if (this.syncDirty) {
			this.syncDirty = false;
			level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
		}
	}

	/** @return true when the item left this pipe */
	private boolean tickItem(ServerLevel level, TravelingItem item) {
		if (item.waiting) {
			if (--item.retry > 0) {
				return false;
			}

			item.waiting = false;
			this.markForSync();
			if (item.ticks < TRAVEL_TICKS) {
				// Was waiting for a route at the center.
				this.reroute(level, item);
				return false;
			}

			return this.tryLeave(level, item);
		}

		item.ticks++;
		if (item.ticks == CENTER_TICKS && (item.to == null || !this.isExitValid(level, item))) {
			this.reroute(level, item);
			return false;
		}

		if (item.ticks >= TRAVEL_TICKS) {
			item.ticks = TRAVEL_TICKS;
			return this.tryLeave(level, item);
		}

		return false;
	}

	private boolean isExitValid(ServerLevel level, TravelingItem item) {
		if (item.to == null) {
			return false;
		}

		BlockPos next = this.worldPosition.relative(item.to);
		if (!level.isLoaded(next)) {
			return false;
		}

		if (item.route.size() > 1) {
			return level.getBlockEntity(next) instanceof ItemPipeBlockEntity;
		}

		return ItemStorage.SIDED.find(level, next, item.to.getOpposite()) != null;
	}

	private boolean tryLeave(ServerLevel level, TravelingItem item) {
		if (item.to == null) {
			this.reroute(level, item);
			return false;
		}

		BlockPos next = this.worldPosition.relative(item.to);
		if (!level.isLoaded(next)) {
			this.waitThenRetry(item, RETRY_TICKS);
			return false;
		}

		if (item.route.size() > 1) {
			if (level.getBlockEntity(next) instanceof ItemPipeBlockEntity nextPipe
				&& nextPipe.acceptFromPipe(item.stack, item.to.getOpposite(), item.route.subList(1, item.route.size()))) {
				return true;
			}

			this.rerouteFromExit(level, item);
			return false;
		}

		Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, next, item.to.getOpposite());
		if (storage != null) {
			long inserted;
			try (Transaction tx = Transaction.openOuter()) {
				inserted = storage.insert(ItemVariant.of(item.stack), item.stack.getCount(), tx);
				tx.commit();
			}

			if (inserted > 0) {
				this.playDeliverSound(level);
				item.stack.shrink((int) inserted);
				if (item.stack.isEmpty()) {
					return true;
				}

				this.markForSync();
			}
		}

		// Destination full or gone: keep the item and look for another destination.
		this.rerouteFromExit(level, item);
		return false;
	}

	private void rerouteFromExit(ServerLevel level, TravelingItem item) {
		FilterResult own = this.testFilter(item.stack);
		List<Direction> route = PipeRouter.findRoute(level, this.worldPosition, item.stack, own == FilterResult.PASS_MATCHED);
		if (route == null || route.isEmpty()) {
			this.waitThenRetry(item, NO_ROUTE_RETRY_TICKS);
			return;
		}

		if (route.get(0) == item.to) {
			item.setRoute(route);
			this.waitThenRetry(item, RETRY_TICKS);
			return;
		}

		// Different exit: travel back through the center toward the new face.
		item.setRoute(route);
		item.ticks = CENTER_TICKS;
		this.markForSync();
	}

	private void reroute(ServerLevel level, TravelingItem item) {
		FilterResult own = this.testFilter(item.stack);
		List<Direction> route = PipeRouter.findRoute(level, this.worldPosition, item.stack, own == FilterResult.PASS_MATCHED);
		if (route == null || route.isEmpty()) {
			item.ticks = CENTER_TICKS;
			this.waitThenRetry(item, NO_ROUTE_RETRY_TICKS);
			return;
		}

		item.setRoute(route);
		this.markForSync();
	}

	private void waitThenRetry(TravelingItem item, int delay) {
		if (!item.waiting) {
			item.waiting = true;
			this.markForSync();
		}

		item.retry = delay;
	}

	private void playDeliverSound(ServerLevel level) {
		long now = level.getGameTime();
		if (now - this.lastSoundTick >= 30) {
			this.lastSoundTick = now;
			level.playSound(null, this.worldPosition, ModSounds.PIPE_DELIVER, SoundSource.BLOCKS, 0.25F, 0.9F + level.getRandom().nextFloat() * 0.3F);
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (this.level != null) {
			// Items in transit are never deleted: they drop when the pipe is broken.
			for (TravelingItem item : this.items) {
				Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, item.stack);
			}
		}

		this.items.clear();
	}

	// =====================================================================================
	// Menu (filter pipes only)
	// =====================================================================================

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.solarquarry.filter_pipe");
	}

	@Override
	public BlockPos getScreenOpeningData(ServerPlayer player) {
		return this.worldPosition;
	}

	@Override
	public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return this.isFilter() ? new FilterPipeMenu(containerId, inventory, this) : null;
	}

	public boolean stillValid(Player player) {
		return !this.isRemoved() && this.level != null && this.level.getBlockEntity(this.worldPosition) == this
			&& player.isWithinBlockInteractionRange(this.worldPosition, 4.0);
	}

	// =====================================================================================
	// Persistence and sync
	// =====================================================================================

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.items.clear();
		for (ValueInput entry : input.childrenListOrEmpty("Travel")) {
			ItemStack stack = entry.read("Stack", ItemStack.CODEC).orElse(ItemStack.EMPTY);
			if (stack.isEmpty()) {
				continue;
			}

			Direction from = Direction.from3DDataValue(entry.getIntOr("From", 0));
			List<Direction> route = new ArrayList<>();
			for (int ordinal : entry.getIntArray("Route").orElse(new int[0])) {
				route.add(Direction.from3DDataValue(ordinal));
			}

			TravelingItem item = new TravelingItem(stack, from, route);
			int to = entry.getIntOr("To", -1);
			item.to = to >= 0 ? Direction.from3DDataValue(to) : null;
			item.ticks = Math.max(0, Math.min(TRAVEL_TICKS, entry.getIntOr("Ticks", 0)));
			item.waiting = entry.getBooleanOr("Waiting", false);
			item.retry = entry.getIntOr("Retry", RETRY_TICKS);
			this.items.add(item);
		}

		input.child("Filter").ifPresent(filterInput -> {
			NonNullList<ItemStack> filterItems = NonNullList.withSize(FILTER_SLOTS, ItemStack.EMPTY);
			ContainerHelper.loadAllItems(filterInput, filterItems);
			for (int i = 0; i < FILTER_SLOTS; i++) {
				this.filter.setItem(i, filterItems.get(i));
			}
		});
		this.whitelist = input.getBooleanOr("Whitelist", this.whitelist);
		this.matchComponents = input.getBooleanOr("MatchComponents", this.matchComponents);
		this.priority = Math.max(MIN_PRIORITY, Math.min(MAX_PRIORITY, input.getIntOr("Priority", this.priority)));
		this.readTags(input);
		if (this.level != null && this.level.isClientSide()) {
			this.clientSnapshotTime = this.level.getGameTime();
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		this.writeItems(output);
		NonNullList<ItemStack> filterItems = NonNullList.withSize(FILTER_SLOTS, ItemStack.EMPTY);
		for (int i = 0; i < FILTER_SLOTS; i++) {
			filterItems.set(i, this.filter.getItem(i));
		}

		ContainerHelper.saveAllItems(output.child("Filter"), filterItems, true);
		output.putBoolean("Whitelist", this.whitelist);
		output.putBoolean("MatchComponents", this.matchComponents);
		output.putInt("Priority", this.priority);
		this.writeTags(output);
	}

	private void writeTags(ValueOutput output) {
		output.store("Tags", Codec.STRING.listOf(), List.of(this.tags));
	}

	private void readTags(ValueInput input) {
		input.read("Tags", Codec.STRING.listOf()).ifPresent(list -> {
			for (int i = 0; i < TAG_SLOTS; i++) {
				String value = i < list.size() ? list.get(i) : "";
				if (!this.setTag(i, value)) {
					this.setTag(i, "");
				}
			}
		});
	}

	private void writeItems(ValueOutput output) {
		ValueOutput.ValueOutputList list = output.childrenList("Travel");
		for (TravelingItem item : this.items) {
			ValueOutput entry = list.addChild();
			entry.store("Stack", ItemStack.CODEC, item.stack);
			entry.putInt("From", item.from.get3DDataValue());
			entry.putInt("To", item.to != null ? item.to.get3DDataValue() : -1);
			int[] route = new int[item.route.size()];
			for (int i = 0; i < route.length; i++) {
				route[i] = item.route.get(i).get3DDataValue();
			}

			entry.putIntArray("Route", route);
			entry.putInt("Ticks", item.ticks);
			entry.putBoolean("Waiting", item.waiting);
			entry.putInt("Retry", item.retry);
		}
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
			TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
			this.writeItems(output);
			if (this.isFilter()) {
				this.writeTags(output);
			}

			return output.buildResult();
		}
	}
}
