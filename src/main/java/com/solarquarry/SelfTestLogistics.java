package com.solarquarry;

import com.solarquarry.blockentity.ItemPipeBlockEntity;
import com.solarquarry.registry.ModBlocks;
import com.solarquarry.splitter.FilteredSplitterBlock;
import com.solarquarry.splitter.FilteredSplitterBlockEntity;
import com.solarquarry.splitter.FilteredSplitterBlockEntity.OutputState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Self-tests for 4.0 logistics: the Filtered Splitter (and later the Storage Terminal). */
final class SelfTestLogistics {
	// Splitter fed by a pipe: matching items north, the rest east.
	static final BlockPos SPLIT = new BlockPos(205, 200, 205);
	static final BlockPos SPLIT_MATCH = SPLIT.north();
	static final BlockPos SPLIT_OTHER = SPLIT.east();
	static final BlockPos SPLIT_PIPE = SPLIT.south();
	// Splitter with a full output on one side and nothing on the other.
	static final BlockPos SPLIT2 = new BlockPos(212, 200, 205);
	static final BlockPos SPLIT2_MATCH = SPLIT2.north();
	static final BlockPos SPLIT2_OTHER = SPLIT2.east();

	private SelfTestLogistics() {
	}

	static void force(ServerLevel level, int x0, int z0, int x1, int z1) {
		for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
			for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
				level.setChunkForced(cx, cz, true);
				level.getChunk(cx, cz);
			}
		}
	}

	static void clear(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				for (int y = y0; y <= y1; y++) {
					level.setBlock(new BlockPos(x, y, z), y == y0 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	static int count(ServerLevel level, BlockPos pos, Item item) {
		int n = 0;
		if (level.getBlockEntity(pos) instanceof Container container) {
			for (int i = 0; i < container.getContainerSize(); i++) {
				if (container.getItem(i).is(item)) {
					n += container.getItem(i).getCount();
				}
			}
		}

		return n;
	}

	static int bufferCount(FilteredSplitterBlockEntity splitter, Item item) {
		int n = 0;
		for (int i = 0; i < splitter.getContainerSize(); i++) {
			if (splitter.getItem(i).is(item)) {
				n += splitter.getItem(i).getCount();
			}
		}

		return n;
	}

	private static FilteredSplitterBlockEntity splitter(ServerLevel level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof FilteredSplitterBlockEntity s ? s : null;
	}

	private static FilteredSplitterBlockEntity placeSplitter(ServerLevel level, BlockPos pos) {
		level.setBlock(pos, ModBlocks.FILTERED_SPLITTER.defaultBlockState(), Block.UPDATE_ALL);
		FilteredSplitterBlockEntity s = splitter(level, pos);
		s.orient(Direction.NORTH); // matching north, others east, input south
		return s;
	}

	// ---- Filtered Splitter ----

	static void setupSplitter(ServerLevel level) {
		force(level, 195, 195, 220, 215);
		clear(level, 198, 199, 200, 218, 202, 210);
		FilteredSplitterBlockEntity s = placeSplitter(level, SPLIT);
		level.setBlock(SPLIT_MATCH, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(SPLIT_OTHER, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(SPLIT_PIPE, ModBlocks.ITEM_PIPE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(SPLIT_PIPE.south(), ModBlocks.ITEM_PIPE.defaultBlockState(), Block.UPDATE_ALL);
		s.getFilter().setItem(0, new ItemStack(Items.RAW_IRON));
		s.setTag(0, "#minecraft:logs");

		FilteredSplitterBlockEntity s2 = placeSplitter(level, SPLIT2);
		s2.getFilter().setItem(0, new ItemStack(Items.RAW_IRON));
		level.setBlock(SPLIT2_MATCH, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		if (level.getBlockEntity(SPLIT2_MATCH) instanceof Container full) {
			for (int i = 0; i < full.getContainerSize(); i++) {
				full.setItem(i, new ItemStack(Items.DIRT, 64));
			}
		}

		// SPLIT2_OTHER stays empty: that output is disconnected.
		s2.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
		s2.setItem(1, new ItemStack(Items.RAW_IRON, 5));
	}

	static void feedSplitter(ServerLevel level) {
		ItemPipeBlockEntity feed = (ItemPipeBlockEntity) level.getBlockEntity(SPLIT_PIPE.south());
		boolean a = feed.acceptFromSource(new ItemStack(Items.COBBLESTONE, 16), Direction.SOUTH);
		SelfTest.check(a, "splitter: the pipe network routes items into the splitter's input side");
	}

	static void feedMore(ServerLevel level) {
		ItemPipeBlockEntity feed = (ItemPipeBlockEntity) level.getBlockEntity(SPLIT_PIPE.south());
		feed.acceptFromSource(new ItemStack(Items.COBBLESTONE, 14), Direction.SOUTH);
		feed.acceptFromSource(new ItemStack(Items.RAW_IRON, 16), Direction.SOUTH);
		feed.acceptFromSource(new ItemStack(Items.RAW_IRON, 4), Direction.SOUTH);
		feed.acceptFromSource(new ItemStack(Items.OAK_LOG, 10), Direction.SOUTH);
	}

	static void verifySplitter(ServerLevel level) {
		FilteredSplitterBlockEntity s = splitter(level, SPLIT);
		int iron = count(level, SPLIT_MATCH, Items.RAW_IRON);
		int logs = count(level, SPLIT_MATCH, Items.OAK_LOG);
		int cobble = count(level, SPLIT_OTHER, Items.COBBLESTONE);
		SolarQuarry.LOGGER.info("[SelfTest] splitter: matching chest {} iron {} logs, other chest {} cobble, buffer empty {}",
			iron, logs, cobble, s.isEmpty());
		SelfTest.check(iron == 20 && logs == 10 && count(level, SPLIT_MATCH, Items.COBBLESTONE) == 0,
			"splitter: listed items and tagged items go to the matching side (" + iron + " iron, " + logs + " logs)");
		SelfTest.check(cobble == 30 && count(level, SPLIT_OTHER, Items.RAW_IRON) == 0 && count(level, SPLIT_OTHER, Items.OAK_LOG) == 0,
			"splitter: everything else goes to the other side (" + cobble + " cobblestone)");
		SelfTest.check(s.isEmpty() && s.matchedCount() == 30 && s.otherCount() == 30, "splitter: nothing left behind, counters match (" + s.matchedCount() + "/" + s.otherCount() + ")");
		SelfTest.check(level.getBlockState(SPLIT).getValue(FilteredSplitterBlock.MATCH) == Direction.NORTH
			&& level.getBlockState(SPLIT).getValue(FilteredSplitterBlock.OTHER) == Direction.EAST, "splitter: the block shows its output sides");
		SelfTest.check(!s.canPlaceItemThroughFace(0, new ItemStack(Items.STONE), Direction.NORTH)
			&& !s.canPlaceItemThroughFace(0, new ItemStack(Items.STONE), Direction.EAST)
			&& s.canPlaceItemThroughFace(0, new ItemStack(Items.STONE), Direction.SOUTH)
			&& !s.canPlaceItemThroughFace(0, new ItemStack(Items.STONE), Direction.WEST)
			&& !s.canTakeItemThroughFace(0, new ItemStack(Items.STONE), Direction.SOUTH),
			"splitter: accepts only on its input side, never takes items back out of an output");

		// Blacklist mode swaps the sides.
		s.toggleWhitelist();
		SelfTest.check(s.outputFor(new ItemStack(Items.RAW_IRON)) == Direction.EAST && s.outputFor(new ItemStack(Items.DIRT)) == Direction.NORTH,
			"splitter: blacklist mode sends listed items to the other side");
		s.toggleWhitelist();

		// Settings survive save/load.
		CompoundTag tag = s.saveWithFullMetadata(level.registryAccess());
		BlockEntity copy = BlockEntity.loadStatic(SPLIT, level.getBlockState(SPLIT), tag, level.registryAccess());
		SelfTest.check(copy instanceof FilteredSplitterBlockEntity c && c.getFilter().getItem(0).is(Items.RAW_IRON) && c.getTag(0).equals("minecraft:logs")
			&& c.config().matchFace() == Direction.NORTH && c.config().otherFace() == Direction.EAST && c.config().inputFace() == Direction.SOUTH
			&& c.config().whitelist() && c.outputFor(new ItemStack(Items.SPRUCE_LOG)) == Direction.NORTH,
			"splitter: filter, tags and sides survive save/load");

		// Changing a side keeps outputs and input apart.
		s.cycleMatch();
		SelfTest.check(s.config().matchFace() != s.config().otherFace() && s.config().inputFace() != s.config().matchFace()
			&& level.getBlockState(SPLIT).getValue(FilteredSplitterBlock.MATCH) == s.config().matchFace(), "splitter: changing a side updates the block and never doubles up");
		s.resetToDefaults();
		SelfTest.check(s.getFilter().isEmpty() && s.getTag(0).isEmpty() && s.config().matchFace() == Direction.NORTH, "splitter: reset clears the filter and restores the sides");

		// Full and missing outputs: items wait, nothing is deleted.
		FilteredSplitterBlockEntity s2 = splitter(level, SPLIT2);
		SelfTest.check(bufferCount(s2, Items.COBBLESTONE) == 16 && bufferCount(s2, Items.RAW_IRON) == 5,
			"splitter: a full output and a missing output keep the items in the buffer (" + bufferCount(s2, Items.COBBLESTONE) + " + " + bufferCount(s2, Items.RAW_IRON) + ")");
		SelfTest.check(s2.matchState() == OutputState.FULL && s2.otherState() == OutputState.DISCONNECTED,
			"splitter: reports a full matching side and a disconnected other side (" + s2.matchState() + ", " + s2.otherState() + ")");
		if (level.getBlockEntity(SPLIT2_MATCH) instanceof Container full) {
			full.clearContent();
		}

		level.setBlock(SPLIT2_OTHER, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
	}

	static void verifySplitterRecovered(ServerLevel level) {
		FilteredSplitterBlockEntity s2 = splitter(level, SPLIT2);
		SelfTest.check(s2.isEmpty() && count(level, SPLIT2_MATCH, Items.RAW_IRON) == 5 && count(level, SPLIT2_OTHER, Items.COBBLESTONE) == 16,
			"splitter: resumes when there is room again, nothing lost");
		SelfTest.check(s2.matchState() != OutputState.FULL && s2.otherState() != OutputState.DISCONNECTED, "splitter: status clears after recovery");
	}
}
