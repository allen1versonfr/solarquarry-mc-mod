package com.solarquarry.registry;

import com.solarquarry.SolarQuarry;
import com.solarquarry.item.ConfigCardItem;
import com.solarquarry.item.ProspectorItem;
import com.solarquarry.item.UpgradeItem;
import com.solarquarry.item.WrenchItem;
import com.solarquarry.quarry.UpgradeType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public final class ModItems {
	public static final List<Item> ITEMS = new ArrayList<>();

	/** Energy stored on an item (Rocket Boots, prospectors, Scanner Helmet). */
	public static final net.minecraft.core.component.DataComponentType<Long> ENERGY = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
		SolarQuarry.id("energy"), net.minecraft.core.component.DataComponentType.<Long>builder()
			.persistent(com.mojang.serialization.Codec.LONG).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_LONG).build());

	/** Advanced Powered Pickaxe: area (3 x 3) mode on or off. */
	public static final net.minecraft.core.component.DataComponentType<Boolean> AREA_MODE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
		SolarQuarry.id("area_mode"), net.minecraft.core.component.DataComponentType.<Boolean>builder()
			.persistent(com.mojang.serialization.Codec.BOOL).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL).build());

	public static final Item WRENCH = register("wrench", WrenchItem::new, new Item.Properties().stacksTo(1));
	public static final Item CIRCUIT_BOARD = register("circuit_board", Item::new, new Item.Properties());
	public static final Item ADVANCED_CIRCUIT = register("advanced_circuit", Item::new, new Item.Properties().rarity(Rarity.UNCOMMON));
	public static final Item PHOTOVOLTAIC_CELL = register("photovoltaic_cell", Item::new, new Item.Properties());
	public static final Item MACHINE_FRAME = register("machine_frame", Item::new, new Item.Properties());
	public static final Item DRILL_HEAD = register("drill_head", Item::new, new Item.Properties().stacksTo(16));
	public static final Item UPGRADE_BASE = register("upgrade_base", Item::new, new Item.Properties());

	/** Powered tools (energy on the item, see {@link com.solarquarry.item.PoweredItem}). */
	public static final Item ORE_PROSPECTOR = register("ore_prospector", ProspectorItem::new,
		new Item.Properties().stacksTo(1).component(ENERGY, 0L).rarity(Rarity.UNCOMMON));
	public static final Item LIQUID_PROSPECTOR = register("liquid_prospector", com.solarquarry.item.LiquidProspectorItem::new,
		new Item.Properties().stacksTo(1).component(ENERGY, 0L).rarity(Rarity.UNCOMMON));
	public static final Item SLIME_PROSPECTOR = register("slime_prospector", com.solarquarry.item.SlimeProspectorItem::new,
		new Item.Properties().stacksTo(1).component(ENERGY, 0L).rarity(Rarity.UNCOMMON));
	public static final Item SCANNER_HELMET = register("scanner_helmet", com.solarquarry.item.ScannerHelmetItem::new,
		new Item.Properties().humanoidArmor(net.minecraft.world.item.equipment.ArmorMaterials.IRON, net.minecraft.world.item.equipment.ArmorType.HELMET)
			.component(ENERGY, 0L).rarity(Rarity.RARE));
	public static final Item CONFIG_CARD = register("config_card", ConfigCardItem::new, new Item.Properties().stacksTo(16));

	public static final Item SPEED_UPGRADE = upgrade("speed_upgrade", UpgradeType.SPEED, 0, Rarity.UNCOMMON);
	public static final Item ENERGY_CAPACITY_UPGRADE = upgrade("energy_capacity_upgrade", UpgradeType.ENERGY_CAPACITY, 0, Rarity.UNCOMMON);
	public static final Item ENERGY_EFFICIENCY_UPGRADE = upgrade("energy_efficiency_upgrade", UpgradeType.ENERGY_EFFICIENCY, 0, Rarity.UNCOMMON);
	public static final Item FORTUNE_UPGRADE = upgrade("fortune_upgrade", UpgradeType.FORTUNE, 0, Rarity.RARE);
	public static final Item SILK_TOUCH_UPGRADE = upgrade("silk_touch_upgrade", UpgradeType.SILK_TOUCH, 0, Rarity.RARE);
	public static final Item RANGE_UPGRADE = upgrade("range_upgrade", UpgradeType.RANGE, 1, Rarity.UNCOMMON);
	public static final Item RANGE_UPGRADE_2 = upgrade("range_upgrade_2", UpgradeType.RANGE, 2, Rarity.RARE);
	public static final Item VOID_UPGRADE = upgrade("void_upgrade", UpgradeType.VOID, 0, Rarity.UNCOMMON);
	public static final Item CHUNK_LOADER_UPGRADE = upgrade("chunk_loader_upgrade", UpgradeType.CHUNK_LOADER, 0, Rarity.RARE);
	public static final Item AUTO_SMELTER_UPGRADE = upgrade("auto_smelter_upgrade", UpgradeType.AUTO_SMELTER, 0, Rarity.RARE);
	public static final Item GROWTH_UPGRADE = upgrade("growth_upgrade", UpgradeType.GROWTH, 0, Rarity.UNCOMMON);
	public static final Item HARVEST_UPGRADE = upgrade("harvest_upgrade", UpgradeType.HARVEST, 0, Rarity.UNCOMMON);
	public static final Item SECURITY_UPGRADE = upgrade("security_upgrade", UpgradeType.SECURITY, 0, Rarity.RARE);
	public static final Item MINER_DRONE = register("miner_drone", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
	public static final Item LIQUID_REMOVER_UPGRADE = upgrade("liquid_remover_upgrade", UpgradeType.LIQUID_REMOVER, 0, Rarity.UNCOMMON);

	public static final Item LEATHER_ROCKET_BOOTS = boots(com.solarquarry.boots.RocketTier.LEATHER, Rarity.COMMON);
	public static final Item IRON_ROCKET_BOOTS = boots(com.solarquarry.boots.RocketTier.IRON, Rarity.UNCOMMON);
	public static final Item DIAMOND_ROCKET_BOOTS = boots(com.solarquarry.boots.RocketTier.DIAMOND, Rarity.RARE);
	public static final Item HELIONITE_ROCKET_BOOTS = boots(com.solarquarry.boots.RocketTier.HELIONITE, Rarity.EPIC);

	// ---- equipment found in structures (most can also be crafted) ----

	/** Iron-grade material with more durability: Industrial Power Hammer. */
	public static final net.minecraft.world.item.ToolMaterial HAMMER_MATERIAL = new net.minecraft.world.item.ToolMaterial(
		net.minecraft.tags.BlockTags.INCORRECT_FOR_IRON_TOOL, 600, 6.0F, 2.0F, 14, net.minecraft.tags.ItemTags.IRON_TOOL_MATERIALS);
	/** Between iron and diamond: mines what diamond mines, a little slower and less durable. */
	public static final net.minecraft.world.item.ToolMaterial REINFORCED_MATERIAL = new net.minecraft.world.item.ToolMaterial(
		net.minecraft.tags.BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1200, 7.0F, 2.5F, 12, net.minecraft.tags.ItemTags.IRON_TOOL_MATERIALS);

	public static final Item INDUSTRIAL_POWER_HAMMER = register("industrial_power_hammer", Item::new,
		new Item.Properties().sword(HAMMER_MATERIAL, 6.0F, -3.3F).rarity(Rarity.UNCOMMON));
	public static final Item REINFORCED_MINING_PICKAXE = register("reinforced_mining_pickaxe", Item::new,
		new Item.Properties().pickaxe(REINFORCED_MATERIAL, 1.0F, -2.8F).rarity(Rarity.UNCOMMON));
	public static final Item ADVANCED_POWERED_PICKAXE = register("advanced_powered_pickaxe", com.solarquarry.gear.PoweredPickaxeItem::new,
		new Item.Properties().pickaxe(net.minecraft.world.item.ToolMaterial.DIAMOND, 1.0F, -2.8F).component(ENERGY, 0L).component(AREA_MODE, false).rarity(Rarity.EPIC));
	public static final Item PORTABLE_POWER_CELL = register("portable_power_cell", p -> new com.solarquarry.gear.PowerCellItem(100_000, 25_000, p),
		new Item.Properties().stacksTo(1).component(ENERGY, 0L).rarity(Rarity.UNCOMMON));
	public static final Item PROTOTYPE_BATTERY = register("prototype_battery", p -> new com.solarquarry.gear.PowerCellItem(1_000_000, 100_000, p),
		new Item.Properties().stacksTo(1).component(ENERGY, 0L).rarity(Rarity.EPIC));
	public static final Item INDUSTRIAL_REPAIR_KIT = register("industrial_repair_kit", com.solarquarry.gear.RepairKitItem::new,
		new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
	public static final Item EMERGENCY_ENERGY_INJECTOR = register("emergency_energy_injector", com.solarquarry.gear.EnergyInjectorItem::new,
		new Item.Properties().stacksTo(16).rarity(Rarity.RARE));
	public static final Item MINING_HELMET = register("mining_helmet", com.solarquarry.gear.MiningHelmetItem::new,
		new Item.Properties().humanoidArmor(net.minecraft.world.item.equipment.ArmorMaterials.IRON, net.minecraft.world.item.equipment.ArmorType.HELMET).rarity(Rarity.UNCOMMON));
	public static final Item NIGHT_VISION_GOGGLES = register("night_vision_goggles", com.solarquarry.gear.GogglesItem::new,
		new Item.Properties().humanoidArmor(net.minecraft.world.item.equipment.ArmorMaterials.CHAINMAIL, net.minecraft.world.item.equipment.ArmorType.HELMET)
			.component(ENERGY, 0L).rarity(Rarity.RARE));
	public static final Item ADVANCED_MINING_SCANNER = register("advanced_mining_scanner", com.solarquarry.gear.MiningScannerItem::new,
		new Item.Properties().stacksTo(1).component(ENERGY, 0L).rarity(Rarity.RARE));

	private static Item boots(com.solarquarry.boots.RocketTier tier, Rarity rarity) {
		Item.Properties properties = new Item.Properties().humanoidArmor(tier.material(), net.minecraft.world.item.equipment.ArmorType.BOOTS).rarity(rarity)
			.component(ENERGY, 0L);
		if (tier == com.solarquarry.boots.RocketTier.HELIONITE) {
			properties = properties.fireResistant();
		}

		return register(tier.id() + "_rocket_boots", p -> new com.solarquarry.boots.RocketBootsItem(tier, p), properties);
	}

	private ModItems() {
	}

	private static Item upgrade(String name, UpgradeType type, int tier, Rarity rarity) {
		return register(name, p -> new UpgradeItem(type, tier, p), new Item.Properties().stacksTo(type.maxStack()).rarity(rarity));
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SolarQuarry.id(name));
		Item item = factory.apply(properties.setId(key));
		Registry.register(BuiltInRegistries.ITEM, key, item);
		ITEMS.add(item);
		return item;
	}

	public static void init() {
	}
}
