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
			// Upgrade summary shows installed counts against their limits (2 speed upgrades are installed).
			context.runOnClient(client -> {
				if (!(client.gui.screen() instanceof QuarryScreen screen)) {
					throw new AssertionError("quarry screen not open");
				}

				String summary = screen.upgradeSummary().stream().map(net.minecraft.network.chat.Component::getString).reduce("", (a, b) -> a + "\n" + b);
				if (!summary.contains("Speed 2/4") || !summary.contains("Efficiency 0/4") || !summary.contains("Slots used: 4/6")) {
					throw new AssertionError("upgrade summary is missing n/max counts: " + summary);
				}
			});
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
			// Close the way the game does (tells the server), not just by dropping the screen.
			context.runOnClient(client -> client.player.closeContainer());
			context.waitTicks(5);

			// Recipe book: picking up an ingredient must unlock the mod's recipes for this player.
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				if (player.containerMenu != player.inventoryMenu) {
					throw new AssertionError("a menu is still open on the server: " + player.containerMenu);
				}

				player.getInventory().add(new ItemStack(Items.IRON_INGOT, 1));
			});
			context.waitTicks(20);
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				for (String id : java.util.List.of("quarry", "wrench", "item_pipe", "battery_basic", "remote_terminal", "ore_prospector")) {
					var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, com.solarquarry.SolarQuarry.id(id));
					if (!player.getRecipeBook().contains(key)) {
						throw new AssertionError("recipe " + id + " was not unlocked in the recipe book after picking up an iron ingot");
					}
				}
			});

			// Tooltips are added through Fabric's ItemTooltipCallback.
			context.runOnClient(client -> {
				var lines = new ItemStack(ModItems.WRENCH).getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(client.level), client.player,
					net.minecraft.world.item.TooltipFlag.NORMAL);
				String text = lines.stream().map(net.minecraft.network.chat.Component::getString).reduce("", (a, b) -> a + "\n" + b);
				if (!text.contains(net.minecraft.network.chat.Component.translatable("tooltip.solarquarry.wrench").getString())) {
					throw new AssertionError("wrench tooltip line missing: " + text);
				}

				// The server sent its settings on join; the chunk loader tooltip shows the server's upkeep.
				if (!com.solarquarry.config.SolarQuarryConfig.isFromServer()) {
					throw new AssertionError("the client never received the server's Solar Quarry settings");
				}

				var loader = new ItemStack(ModItems.CHUNK_LOADER_UPGRADE).getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(client.level), client.player,
					net.minecraft.world.item.TooltipFlag.NORMAL);
				String loaderText = loader.stream().map(net.minecraft.network.chat.Component::getString).reduce("", (a, b) -> a + "\n" + b);
				String upkeep = net.minecraft.network.chat.Component.translatable("tooltip.solarquarry.upgrade.chunk_loader.upkeep",
					com.solarquarry.config.SolarQuarryConfig.get().chunkLoaderUpkeep()).getString();
				if (!loaderText.contains(upkeep)) {
					throw new AssertionError("chunk loader tooltip is missing the upkeep line: " + loaderText);
				}
			});

			// Tunnel Bore: the gantry should follow the tunnel volume, not the old square area.
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				if (player.level().getBlockEntity(quarryPos) instanceof QuarryBlockEntity quarry) {
					quarry.setQuarryMode(com.solarquarry.quarry.QuarryMode.TUNNEL_BORE);
					quarry.setWidth(3);
					quarry.setHeight(3);
					quarry.setLength(24);
					quarry.start();
				}
			});
			singleplayer.getServer().runCommand("tp @a " + (q.getX() + 6) + " " + (q.getY() + 4) + " " + (q.getZ() - 3));
			context.waitTicks(10);
			context.getInput().lookAt(q.offset(0, 0, 10));
			context.waitTicks(120);
			context.takeScreenshot("09_tunnel_mode");

			// Sorting Terminal on the output network: the screen lists the chests reachable through the pipes.
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				ServerLevel level = player.level();
				BlockPos terminalPos = quarryPos.east().north();
				level.setBlock(terminalPos, ModBlocks.SORTING_TERMINAL.defaultBlockState(), Block.UPDATE_ALL);
				if (level.getBlockEntity(terminalPos) instanceof com.solarquarry.blockentity.SortingTerminalBlockEntity terminal) {
					terminal.register(quarryPos.east(4));
					player.openMenu(terminal);
				}
			});
			context.waitForScreen(com.solarquarry.client.screen.SortingTerminalScreen.class);
			context.waitTicks(40);
			context.runOnClient(client -> {
				if (!(client.gui.screen() instanceof com.solarquarry.client.screen.SortingTerminalScreen screen) || screen.getSnapshot() == null
					|| screen.getSnapshot().entries().size() < 2 || screen.getSnapshot().entries().stream().noneMatch(e -> e.registered())) {
					throw new AssertionError("sorting terminal screen did not receive its destination list");
				}
			});
			context.takeScreenshot("10_sorting_terminal");
			context.runOnClient(client -> client.player.closeContainer());
			context.waitTicks(5);

			// A quarry placed in the air with a Range II upgrade: legs reach the ground, the drill stays on the frame.
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				ServerLevel level = player.level();
				BlockPos high = quarryPos.offset(-48, 6, 0);
				level.setBlock(high, ModBlocks.QUARRY.defaultBlockState().setValue(QuarryBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
				if (level.getBlockEntity(high) instanceof QuarryBlockEntity floating) {
					floating.setItem(QuarryBlockEntity.BUFFER_SLOTS, new ItemStack(ModItems.RANGE_UPGRADE_2));
					floating.setItem(QuarryBlockEntity.BUFFER_SLOTS + 1, new ItemStack(ModItems.SPEED_UPGRADE, 4));
					floating.restoreEnergy(400_000L);
				}
			});
			context.waitTicks(3);
			singleplayer.getServer().runOnServer(server -> {
				ServerLevel level = server.getPlayerList().getPlayers().get(0).level();
				if (level.getBlockEntity(quarryPos.offset(-48, 6, 0)) instanceof QuarryBlockEntity floating) {
					floating.setWidth(64);
					floating.setLength(64);
					floating.start();
					if (floating.getWidth() != 64) {
						throw new AssertionError("Range II did not allow a 64 wide area: " + floating.getWidth());
					}
				}
			});
			BlockPos high = q.offset(-48, 6, 0);
			singleplayer.getServer().runCommand("tp @a " + (high.getX() + 40) + " " + (high.getY() + 12) + " " + (high.getZ() - 14));
			context.waitTicks(20);
			context.getInput().lookAt(high.offset(0, -6, 24));
			context.waitTicks(160);
			context.takeScreenshot("11_floating_range_quarry");
			singleplayer.getServer().runCommand("tp @a " + (high.getX() - 6) + " " + (high.getY() + 1) + " " + (high.getZ() - 6));
			context.waitTicks(20);
			context.getInput().lookAt(high.offset(0, -6, 2));
			context.waitTicks(40);
			context.takeScreenshot("12_quarry_legs_and_support");

			// Liquid Remover over a flooded pit: the pump arm drains ahead of the drill into a Liquid Tank; items go to a crate.
			BlockPos pond = q.offset(30, 0, 0);
			singleplayer.getServer().runOnServer(server -> buildPond(server.getPlayerList().getPlayers().get(0).level(), pond));
			singleplayer.getServer().runCommand("tp @a " + (pond.getX() + 9) + " " + (pond.getY() + 5) + " " + (pond.getZ() - 4));
			context.waitTicks(20);
			context.getInput().lookAt(pond.offset(0, -2, 3));
			context.waitTicks(120);
			context.takeScreenshot("13_liquid_remover_pumping");
			context.waitTicks(200);
			singleplayer.getServer().runOnServer(server -> {
				ServerLevel level = server.getPlayerList().getPlayers().get(0).level();
				if (!(level.getBlockEntity(pond.north(2)) instanceof com.solarquarry.fluid.LiquidTankBlockEntity tank) || tank.amount() <= 0) {
					throw new AssertionError("the liquid tank received no water from the quarry");
				}

				com.solarquarry.SolarQuarry.LOGGER.info("[ClientTest] liquid tank holds {} mB after pumping", tank.amount() * 1000 / net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BUCKET);
				// Top it up to half so the screenshot shows the level gauge clearly.
				long half = com.solarquarry.fluid.LiquidTankBlockEntity.CAPACITY / 2;
				try (var tx = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
					tank.storage.insert(tank.fluid(), Math.max(0, half - tank.amount()), tx);
					tx.commit();
				}
			});
			singleplayer.getServer().runCommand("tp @a " + (pond.getX() - 2) + " " + (pond.getY() + 1) + " " + (pond.getZ() - 4));
			context.waitTicks(10);
			context.getInput().lookAt(pond.north(2));
			context.waitTicks(20);
			context.takeScreenshot("14_liquid_tank_and_pipe");
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				if (player.level().getBlockEntity(pond.west()) instanceof com.solarquarry.storage.CrateBlockEntity crate) {
					player.openMenu(crate);
				}
			});
			context.waitForScreen(com.solarquarry.client.screen.CrateScreen.class);
			context.waitTicks(10);
			context.takeScreenshot("15_crate_screen");
			context.runOnClient(client -> client.player.closeContainer());
			context.waitTicks(5);

			// Super Smelter: two lanes working, the third idle.
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				BlockPos smelterPos = pond.west(2);
				player.level().setBlock(smelterPos, ModBlocks.SUPER_SMELTER.defaultBlockState(), Block.UPDATE_ALL);
				if (player.level().getBlockEntity(smelterPos) instanceof com.solarquarry.smelting.SuperSmelterBlockEntity smelter) {
					smelter.restoreEnergy(40_000L);
					smelter.setItem(0, new ItemStack(Items.RAW_IRON, 32));
					smelter.setItem(1, new ItemStack(Items.SAND, 16));
					player.openMenu(smelter);
				}
			});
			context.waitForScreen(com.solarquarry.client.screen.SuperSmelterScreen.class);
			context.waitTicks(80);
			context.runOnClient(client -> {
				if (!(client.gui.screen() instanceof com.solarquarry.client.screen.SuperSmelterScreen screen)
					|| screen.getMenu().getStatus() != com.solarquarry.smelting.SuperSmelterBlockEntity.Status.RUNNING || screen.getMenu().getPowerDraw() != 32) {
					throw new AssertionError("super smelter screen does not show two working lanes");
				}
			});
			context.takeScreenshot("16_super_smelter");
			context.runOnClient(client -> client.player.closeContainer());
			context.waitTicks(5);

			// Fabrication Bench: catalogue of every recipe; the player holds the ingredients for an item pipe.
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				BlockPos benchPos = pond.west(3);
				player.level().setBlock(benchPos, ModBlocks.FABRICATION_BENCH.defaultBlockState(), Block.UPDATE_ALL);
				player.getInventory().setItem(10, new ItemStack(Items.COPPER_INGOT, 6));
				player.getInventory().setItem(11, new ItemStack(Items.GLASS_PANE, 3));
				player.level().getBlockState(benchPos).useWithoutItem(player.level(), player,
					new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(benchPos), Direction.UP, benchPos, false));
			});
			context.waitForScreen(com.solarquarry.client.screen.FabricationBenchScreen.class);
			context.waitTicks(20);
			context.runOnClient(client -> {
				if (com.solarquarry.client.screen.FabricationBenchScreen.catalogue.size() < 40) {
					throw new AssertionError("bench catalogue incomplete: " + com.solarquarry.client.screen.FabricationBenchScreen.catalogue.size());
				}
			});
			context.takeScreenshot("17_fabrication_bench");
			context.runOnClient(client -> client.player.closeContainer());
			context.waitTicks(5);
		}
	}

	private static void buildPond(ServerLevel level, BlockPos p) {
		BlockPos.betweenClosed(p.offset(-5, 0, -3), p.offset(5, 8, 9)).forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS));
		BlockPos.betweenClosed(p.offset(-3, -6, 0), p.offset(3, -1, 6)).forEach(pos -> level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS));
		BlockPos.betweenClosed(p.offset(-2, -3, 1), p.offset(2, -1, 5)).forEach(pos -> level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL));
		level.setBlock(p.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(p, ModBlocks.QUARRY.defaultBlockState().setValue(QuarryBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
		level.setBlock(p.north(), ModBlocks.FLUID_PIPE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(p.north(2), ModBlocks.LIQUID_TANK.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(p.west(), ModBlocks.CRATE_REINFORCED.defaultBlockState(), Block.UPDATE_ALL);
		if (level.getBlockEntity(p) instanceof QuarryBlockEntity quarry) {
			quarry.setItem(QuarryBlockEntity.BUFFER_SLOTS, new ItemStack(ModItems.LIQUID_REMOVER_UPGRADE));
			quarry.setItem(QuarryBlockEntity.BUFFER_SLOTS + 1, new ItemStack(ModItems.SPEED_UPGRADE, 1));
			quarry.restoreEnergy(400_000L);
			quarry.setWidth(5);
			quarry.setLength(5);
			quarry.setTargetY(p.getY() - 6);
			quarry.start();
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
			quarry.setItem(QuarryBlockEntity.BUFFER_SLOTS + 3, new ItemStack(ModItems.CHUNK_LOADER_UPGRADE, 1));
			quarry.setItem(QuarryBlockEntity.TORCH_SLOT, new ItemStack(Items.TORCH, 32));
			quarry.start();
		}
	}
}
