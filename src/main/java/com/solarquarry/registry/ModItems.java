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

	public static final Item WRENCH = register("wrench", WrenchItem::new, new Item.Properties().stacksTo(1));
	public static final Item CIRCUIT_BOARD = register("circuit_board", Item::new, new Item.Properties());
	public static final Item ADVANCED_CIRCUIT = register("advanced_circuit", Item::new, new Item.Properties().rarity(Rarity.UNCOMMON));
	public static final Item PHOTOVOLTAIC_CELL = register("photovoltaic_cell", Item::new, new Item.Properties());
	public static final Item MACHINE_FRAME = register("machine_frame", Item::new, new Item.Properties());
	public static final Item DRILL_HEAD = register("drill_head", Item::new, new Item.Properties().stacksTo(16));
	public static final Item UPGRADE_BASE = register("upgrade_base", Item::new, new Item.Properties());

	public static final Item ORE_PROSPECTOR = register("ore_prospector", ProspectorItem::new,
		new Item.Properties().durability(128).repairable(net.minecraft.world.item.Items.COPPER_INGOT).rarity(Rarity.UNCOMMON));
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
	public static final Item LIQUID_REMOVER_UPGRADE = upgrade("liquid_remover_upgrade", UpgradeType.LIQUID_REMOVER, 0, Rarity.UNCOMMON);

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
