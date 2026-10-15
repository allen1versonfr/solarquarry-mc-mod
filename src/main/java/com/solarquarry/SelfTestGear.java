package com.solarquarry;

import com.solarquarry.gear.EnergyInjectorItem;
import com.solarquarry.gear.PoweredPickaxeItem;
import com.solarquarry.gear.RepairKitItem;
import com.solarquarry.gear.WornGear;
import com.solarquarry.item.PoweredItem;
import com.solarquarry.registry.ModItems;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Self-tests for the Checkpoint K equipment: power cell, repair kit, energy injector, worn gear, area pickaxe. */
final class SelfTestGear {
	private static final BlockPos WALL = new BlockPos(430, 150, 430);

	private SelfTestGear() {
	}

	private static FakePlayer player(ServerLevel level, String name) {
		FakePlayer player = FakePlayer.get(level, new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(name.getBytes()), name));
		player.getInventory().clearContent();
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			player.setItemSlot(slot, ItemStack.EMPTY);
		}

		player.removeAllEffects();
		return player;
	}

	static void verifyItems(ServerLevel level) {
		FakePlayer player = player(level, "GearTest");
		player.snapTo(WALL.getX() + 0.5, 120, WALL.getZ() + 0.5, 0, 0);

		// Portable Power Cell: moves energy into powered items in the inventory, never into other cells.
		ItemStack cell = new ItemStack(ModItems.PORTABLE_POWER_CELL);
		PoweredItem.setEnergy(cell, PoweredItem.capacityOf(cell));
		ItemStack scanner = new ItemStack(ModItems.ORE_PROSPECTOR);
		ItemStack otherCell = new ItemStack(ModItems.PORTABLE_POWER_CELL);
		player.setItemInHand(InteractionHand.MAIN_HAND, cell);
		player.getInventory().setItem(5, scanner);
		player.getInventory().setItem(6, otherCell);
		long before = PoweredItem.energy(cell);
		InteractionResult gave = cell.getItem().use(level, player, InteractionHand.MAIN_HAND);
		long moved = PoweredItem.energy(scanner);
		SelfTest.check(gave == InteractionResult.SUCCESS && moved > 0 && PoweredItem.energy(cell) == before - moved && PoweredItem.energy(otherCell) == 0,
			"power cell: charges powered gear from its own store, energy conserved, other cells untouched (" + moved + " E)");
		player.getInventory().clearContent();

		// Industrial Repair Kit: repairs the most worn Solar Quarry item, one kit per use; vanilla items are not touched.
		ItemStack hammer = new ItemStack(ModItems.INDUSTRIAL_POWER_HAMMER);
		hammer.setDamageValue(500);
		ItemStack pick = new ItemStack(ModItems.REINFORCED_MINING_PICKAXE);
		pick.setDamageValue(100);
		ItemStack vanilla = new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE);
		vanilla.setDamageValue(200);
		ItemStack kits = new ItemStack(ModItems.INDUSTRIAL_REPAIR_KIT, 2);
		player.setItemInHand(InteractionHand.MAIN_HAND, kits);
		player.getInventory().setItem(5, hammer);
		player.getInventory().setItem(6, pick);
		player.getInventory().setItem(7, vanilla);
		kits.getItem().use(level, player, InteractionHand.MAIN_HAND);
		SelfTest.check(hammer.getDamageValue() == 500 - RepairKitItem.REPAIR && pick.getDamageValue() == 100 && vanilla.getDamageValue() == 200 && kits.getCount() == 1,
			"repair kit: repairs the most worn Solar Quarry item by " + RepairKitItem.REPAIR + " and is used up; vanilla tools are not repaired");
		player.getInventory().clearContent();

		// Emergency Energy Injector: one-shot charge into the emptiest powered item.
		ItemStack injector = new ItemStack(ModItems.EMERGENCY_ENERGY_INJECTOR);
		ItemStack boots = new ItemStack(ModItems.IRON_ROCKET_BOOTS);
		ItemStack fullTool = new ItemStack(ModItems.ORE_PROSPECTOR);
		PoweredItem.setEnergy(fullTool, PoweredItem.capacityOf(fullTool));
		player.setItemInHand(InteractionHand.MAIN_HAND, injector);
		player.setItemSlot(EquipmentSlot.FEET, boots);
		player.getInventory().setItem(5, fullTool);
		injector.getItem().use(level, player, InteractionHand.MAIN_HAND);
		ItemStack charged = player.getItemBySlot(EquipmentSlot.FEET);
		SelfTest.check(PoweredItem.energy(charged) == EnergyInjectorItem.AMOUNT && injector.isEmpty(),
			"energy injector: adds " + EnergyInjectorItem.AMOUNT + " E to the item that needs it most and is used up");
		player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
		player.getInventory().clearContent();

		// Mining Helmet: Haste below y=50 only.
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.MINING_HELMET));
		player.snapTo(WALL.getX() + 0.5, WornGear.MINING_Y + 30, WALL.getZ() + 0.5, 0, 0);
		int high = WornGear.apply(player);
		player.snapTo(WALL.getX() + 0.5, WornGear.MINING_Y - 20, WALL.getZ() + 0.5, 0, 0);
		int low = WornGear.apply(player);
		SelfTest.check((high & 1) == 0 && (low & 1) == 1 && player.hasEffect(MobEffects.HASTE), "mining helmet: Haste only below y=" + WornGear.MINING_Y);
		player.removeAllEffects();

		// Night-Vision Goggles: work while charged, pay per second, nothing when empty.
		ItemStack goggles = new ItemStack(ModItems.NIGHT_VISION_GOGGLES);
		player.setItemSlot(EquipmentSlot.HEAD, goggles);
		int none = WornGear.apply(player);
		PoweredItem.setEnergy(player.getItemBySlot(EquipmentSlot.HEAD), 1_000);
		int on = WornGear.apply(player);
		long left = PoweredItem.energy(player.getItemBySlot(EquipmentSlot.HEAD));
		SelfTest.check((none & 2) == 0 && (on & 2) == 2 && left == 1_000 - WornGear.GOGGLES_DRAIN * 20 && player.hasEffect(MobEffects.NIGHT_VISION),
			"night-vision goggles: Night Vision only while charged, " + WornGear.GOGGLES_DRAIN + " E per tick worn");
		player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		player.removeAllEffects();

		verifyPickaxe(level, player);
	}

	private static void verifyPickaxe(ServerLevel level, FakePlayer player) {
		int cx = WALL.getX() >> 4;
		int cz = WALL.getZ() >> 4;
		level.setChunkForced(cx, cz, true);
		player.setGameMode(GameType.SURVIVAL);
		// A 5 x 5 stone wall facing north, with bedrock in the square and an obsidian block (harder) next to the centre.
		for (int x = -2; x <= 2; x++) {
			for (int y = -2; y <= 2; y++) {
				level.setBlock(WALL.offset(x, y, 0), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}

		level.setBlock(WALL.offset(1, 1, 0), Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(WALL.offset(-1, 0, 0), Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_CLIENTS);
		player.snapTo(WALL.getX() + 0.5, WALL.getY() - 1, WALL.getZ() - 2.5, 0, 0); // facing south, at the wall

		ItemStack pickaxe = new ItemStack(ModItems.ADVANCED_POWERED_PICKAXE);
		PoweredItem.setEnergy(pickaxe, 10_000);
		player.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);
		player.setShiftKeyDown(true);
		pickaxe.getItem().use(level, player, InteractionHand.MAIN_HAND);
		player.setShiftKeyDown(false);
		boolean areaOn = PoweredPickaxeItem.areaMode(pickaxe);
		player.gameMode.destroyBlock(WALL);
		int broken = 0;
		for (BlockPos pos : PoweredPickaxeItem.area(WALL, player)) {
			if (level.getBlockState(pos).isAir()) {
				broken++;
			}
		}

		boolean kept = level.getBlockState(WALL.offset(1, 1, 0)).is(Blocks.BEDROCK) && level.getBlockState(WALL.offset(-1, 0, 0)).is(Blocks.OBSIDIAN);
		boolean outerKept = level.getBlockState(WALL.offset(2, 0, 0)).is(Blocks.STONE) && level.getBlockState(WALL.offset(0, 2, 0)).is(Blocks.STONE);
		long spent = 10_000 - PoweredItem.energy(pickaxe);
		SolarQuarry.LOGGER.info("[SelfTest] pickaxe: area {}, centre {}, {} of 8 around broken, {} E", areaOn, level.getBlockState(WALL).isAir(), broken, spent);
		SelfTest.check(areaOn && level.getBlockState(WALL).isAir() && broken == 6 && kept && outerKept && spent == 6 * PoweredPickaxeItem.AREA_COST,
			"powered pickaxe: area mode breaks the 3 x 3 square facing the player, " + PoweredPickaxeItem.AREA_COST
				+ " E per extra block, never bedrock or harder blocks (" + broken + " extra, " + spent + " E)");

		// Without energy: a normal pickaxe.
		for (int x = -2; x <= 2; x++) {
			for (int y = -2; y <= 2; y++) {
				level.setBlock(WALL.offset(x, y, 0), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}

		PoweredItem.setEnergy(pickaxe, 0);
		player.gameMode.destroyBlock(WALL);
		int extra = 0;
		for (BlockPos pos : PoweredPickaxeItem.area(WALL, player)) {
			if (level.getBlockState(pos).isAir()) {
				extra++;
			}
		}

		SelfTest.check(level.getBlockState(WALL).isAir() && extra == 0, "powered pickaxe: without energy it breaks only the block mined");
		for (BlockPos pos : BlockPos.betweenClosed(WALL.offset(-2, -2, 0), WALL.offset(2, 2, 0))) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}

		player.getInventory().clearContent();
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		level.setChunkForced(cx, cz, false);
	}
}
