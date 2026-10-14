package com.solarquarry.registry;

import com.solarquarry.SolarQuarry;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.MapColor;

/**
 * Helionite: an Overworld ore deep in the deepslate layers, with tools and armour that match netherite's numbers
 * (durability, damage, speed, defence, toughness, knockback resistance, enchantability, fire resistance).
 */
public final class ModGear {
	public static final List<Item> ITEMS = new ArrayList<>();

	public static final TagKey<Item> HELIONITE_TOOL_MATERIALS = TagKey.create(Registries.ITEM, SolarQuarry.id("helionite_tool_materials"));
	public static final TagKey<Item> REPAIRS_HELIONITE_ARMOR = TagKey.create(Registries.ITEM, SolarQuarry.id("repairs_helionite_armor"));
	public static final ResourceKey<EquipmentAsset> HELIONITE_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, SolarQuarry.id("helionite"));

	/** Same numbers as {@link ToolMaterial#NETHERITE}. */
	public static final ToolMaterial HELIONITE_TOOLS = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2031, 9.0F, 4.0F, 15, HELIONITE_TOOL_MATERIALS);
	/** Same numbers as {@link ArmorMaterials#NETHERITE}. */
	public static final ArmorMaterial HELIONITE_ARMOR = new ArmorMaterial(37, ArmorMaterials.makeDefense(3, 6, 8, 3, 11), 15,
		SoundEvents.ARMOR_EQUIP_NETHERITE, 3.0F, 0.1F, REPAIRS_HELIONITE_ARMOR, HELIONITE_ASSET);

	public static final ResourceKey<PlacedFeature> HELIONITE_ORE_PLACED = ResourceKey.create(Registries.PLACED_FEATURE, SolarQuarry.id("ore_helionite"));

	public static final Block HELIONITE_ORE = block("helionite_ore", p -> new DropExperienceBlock(UniformInt.of(3, 7), p),
		BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(3.0F, 3.0F), Rarity.COMMON);
	public static final Block DEEPSLATE_HELIONITE_ORE = block("deepslate_helionite_ore", p -> new DropExperienceBlock(UniformInt.of(3, 7), p),
		BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(4.5F, 3.0F)
			.sound(SoundType.DEEPSLATE), Rarity.COMMON);
	public static final Block HELIONITE_BLOCK = block("helionite_block", Block::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).requiresCorrectToolForDrops().strength(50.0F, 1200.0F).sound(SoundType.NETHERITE_BLOCK),
		Rarity.UNCOMMON);

	public static final Item RAW_HELIONITE = item("raw_helionite", Item::new, new Item.Properties());
	public static final Item HELIONITE_INGOT = item("helionite_ingot", Item::new, new Item.Properties().fireResistant().rarity(Rarity.UNCOMMON));

	public static final Item HELIONITE_SWORD = item("helionite_sword", Item::new, new Item.Properties().sword(HELIONITE_TOOLS, 3.0F, -2.4F).fireResistant());
	public static final Item HELIONITE_PICKAXE = item("helionite_pickaxe", Item::new, new Item.Properties().pickaxe(HELIONITE_TOOLS, 1.0F, -2.8F).fireResistant());
	public static final Item HELIONITE_AXE = item("helionite_axe", p -> new net.minecraft.world.item.AxeItem(HELIONITE_TOOLS, 5.0F, -3.0F, p), new Item.Properties().fireResistant());
	public static final Item HELIONITE_SHOVEL = item("helionite_shovel", p -> new net.minecraft.world.item.ShovelItem(HELIONITE_TOOLS, 1.5F, -3.0F, p), new Item.Properties().fireResistant());
	public static final Item HELIONITE_HOE = item("helionite_hoe", p -> new net.minecraft.world.item.HoeItem(HELIONITE_TOOLS, -4.0F, 0.0F, p), new Item.Properties().fireResistant());

	public static final Item HELIONITE_HELMET = item("helionite_helmet", Item::new, new Item.Properties().humanoidArmor(HELIONITE_ARMOR, ArmorType.HELMET).fireResistant());
	public static final Item HELIONITE_CHESTPLATE = item("helionite_chestplate", Item::new, new Item.Properties().humanoidArmor(HELIONITE_ARMOR, ArmorType.CHESTPLATE).fireResistant());
	public static final Item HELIONITE_LEGGINGS = item("helionite_leggings", Item::new, new Item.Properties().humanoidArmor(HELIONITE_ARMOR, ArmorType.LEGGINGS).fireResistant());
	public static final Item HELIONITE_BOOTS = item("helionite_boots", Item::new, new Item.Properties().humanoidArmor(HELIONITE_ARMOR, ArmorType.BOOTS).fireResistant());

	private ModGear() {
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties, Rarity rarity) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, SolarQuarry.id(name));
		Block block = factory.apply(properties.setId(blockKey));
		Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, SolarQuarry.id(name));
		BlockItem item = new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix().rarity(rarity));
		item.registerBlocks(Item.BY_BLOCK, item);
		Registry.register(BuiltInRegistries.ITEM, itemKey, item);
		ITEMS.add(item);
		return block;
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SolarQuarry.id(name));
		Item item = factory.apply(properties.setId(key));
		Registry.register(BuiltInRegistries.ITEM, key, item);
		ITEMS.add(item);
		return item;
	}

	public static void init() {
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_ORES, HELIONITE_ORE_PLACED);
	}
}
