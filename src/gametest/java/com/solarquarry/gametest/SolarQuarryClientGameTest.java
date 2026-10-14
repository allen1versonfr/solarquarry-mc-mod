package com.solarquarry.gametest;

import com.solarquarry.block.QuarryBlock;
import com.solarquarry.blockentity.BatteryBlockEntity;
import com.solarquarry.blockentity.ItemPipeBlockEntity;
import com.solarquarry.blockentity.QuarryBlockEntity;
import com.solarquarry.client.screen.FilterPipeScreen;
import com.solarquarry.client.screen.QuarryScreen;
import com.solarquarry.client.screen.TerminalScreen;
import com.solarquarry.blockentity.TerminalBlockEntity;
import com.solarquarry.registry.ModBlocks;
import com.solarquarry.registry.ModItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Visual end-to-end check: builds a working quarry setup in a real client world and captures screenshots. */
public class SolarQuarryClientGameTest implements FabricClientGameTest {
	private static BlockPos quarryPos = BlockPos.ZERO;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder()
			.adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
			.create()) {
			context.waitTicks(40);
			singleplayer.getServer().runCommand("time set noon");
			singleplayer.getServer().runCommand("weather clear");
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				ServerLevel level = player.level();
				BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, player.blockPosition());
				quarryPos = ground.offset(0, 0, -4);
				build(level, quarryPos);
			});

			BlockPos q = quarryPos;
			singleplayer.getServer().runCommand("tp @a " + (q.getX() + 14) + " " + (q.getY() + 9) + " " + (q.getZ() - 6));
			context.waitTicks(20);
			context.getInput().lookAt(q.offset(0, 0, 8));
			context.waitTicks(120);
			context.takeScreenshot("01_quarry_running");
			context.waitTicks(160);
			context.takeScreenshot("02_quarry_later");

			singleplayer.getServer().runCommand("tp @a " + (q.getX() + 4) + " " + (q.getY() + 2) + " " + (q.getZ() - 3));
			context.waitTicks(10);
			context.getInput().lookAt(q.offset(3, 0, 0));
			context.waitTicks(30);
			context.takeScreenshot("03_pipes_and_power");

			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				if (player.level().getBlockEntity(quarryPos) instanceof QuarryBlockEntity quarry) {
					player.openMenu(quarry);
				}
			});
			context.waitForScreen(QuarryScreen.class);
			context.waitTicks(20);
			context.takeScreenshot("04_quarry_gui");
			context.clickScreenButton("gui.solarquarry.config");
			context.waitTicks(10);
			context.takeScreenshot("05_quarry_config");
			context.clickScreenButton("gui.solarquarry.back");
			context.clickScreenButton("gui.solarquarry.pause");
			context.waitTicks(10);
			context.takeScreenshot("06_quarry_paused");
			context.clickScreenButton("gui.solarquarry.resume");
			context.waitTicks(5);
			context.setScreen(() -> null);
			context.waitTicks(5);

			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				if (player.level().getBlockEntity(quarryPos.east(3)) instanceof ItemPipeBlockEntity pipe) {
					player.openMenu(pipe);
				}
			});
			context.waitForScreen(FilterPipeScreen.class);
			context.waitTicks(10);
			context.takeScreenshot("07_filter_gui");
			context.setScreen(() -> null);
			context.waitTicks(5);

			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				if (player.level().getBlockEntity(quarryPos.west().north()) instanceof TerminalBlockEntity terminal) {
					player.openMenu(terminal);
				}
			});
			context.waitForScreen(TerminalScreen.class);
			context.waitTicks(30);
			context.takeScreenshot("08_terminal_gui");
			context.setScreen(() -> null);
			context.waitTicks(5);
		}
	}

	private static void build(ServerLevel level, BlockPos q) {
		level.setBlock(q, ModBlocks.QUARRY.defaultBlockState().setValue(QuarryBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
		// Power: elite battery and a row of solar panels connected by cable.
		BlockPos battery = q.west(3);
		level.setBlock(battery, ModBlocks.BATTERY_ELITE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(q.west(1), ModBlocks.ENERGY_CABLE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(q.west(2), ModBlocks.ENERGY_CABLE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(battery.north(), ModBlocks.ENERGY_CABLE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(battery.north(2), ModBlocks.SOLAR_PANEL_BASIC.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(battery.north(2).east(), ModBlocks.SOLAR_PANEL_ADVANCED.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(battery.north(2).west(), ModBlocks.SOLAR_PANEL_ELITE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(q.west().north(), ModBlocks.REMOTE_TERMINAL.defaultBlockState(), Block.UPDATE_ALL);
		if (level.getBlockEntity(battery) instanceof BatteryBlockEntity cell) {
			cell.restoreEnergy(4_000_000L);
		}

		// Output: pipe -> pipe -> filter pipe (raw iron/coal) -> ore chest, plus a branch to a bulk chest.
		level.setBlock(q.east(1), ModBlocks.ITEM_PIPE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(q.east(2), ModBlocks.ITEM_PIPE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(q.east(3), ModBlocks.FILTER_PIPE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(q.east(4), Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(q.east(2).north(), ModBlocks.ITEM_PIPE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(q.east(2).north(2), ModBlocks.ITEM_PIPE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(q.east(2).north(3), Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		if (level.getBlockEntity(q.east(3)) instanceof ItemPipeBlockEntity filter) {
			filter.toggleWhitelist();
			filter.getFilter().setItem(0, new ItemStack(Items.DIRT));
			filter.setTag(0, "c:ores");
			filter.setPriority(2);
		}

		if (level.getBlockEntity(q) instanceof QuarryBlockEntity quarry) {
			quarry.setItem(QuarryBlockEntity.BUFFER_SLOTS, new ItemStack(ModItems.SPEED_UPGRADE, 2));
			quarry.setItem(QuarryBlockEntity.BUFFER_SLOTS + 1, new ItemStack(ModItems.ENERGY_CAPACITY_UPGRADE, 1));
			quarry.setItem(QuarryBlockEntity.BUFFER_SLOTS + 2, new ItemStack(ModItems.FORTUNE_UPGRADE, 2));
			quarry.start();
		}
	}
}
