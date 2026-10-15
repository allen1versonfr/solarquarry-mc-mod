package com.solarquarry.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Spawn Scanner Helmet: while worn and charged it shows the light at your feet and whether that light lets hostile
 * mobs spawn (the HUD and the red markers on nearby dark ground are drawn on the client from the real light values).
 * It burns {@link #DRAIN} E per tick while worn; the server takes the energy, and an empty helmet shows nothing.
 */
public class ScannerHelmetItem extends Item implements TooltipLines, PoweredItem {
	public static final long CAPACITY = 40_000;
	public static final long DRAIN = 2;
	/** Marker radius in blocks (horizontal) around the wearer. */
	public static final int RADIUS = 8;

	public ScannerHelmetItem(Properties properties) {
		super(properties);
	}

	@Override
	public long capacity(ItemStack stack) {
		return CAPACITY;
	}

	public static ItemStack worn(Player player) {
		ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
		return head.getItem() instanceof ScannerHelmetItem ? head : ItemStack.EMPTY;
	}

	/** Whether the helmet's display is on for this player (worn and charged). */
	public static boolean active(Player player) {
		ItemStack helmet = worn(player);
		return !helmet.isEmpty() && PoweredItem.energy(helmet) >= DRAIN;
	}

	/** Server: drain the energy of every worn helmet. */
	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			drain(player);
		}
	}

	/** One tick of power use for one player; returns true if the display is powered this tick. */
	public static boolean drain(Player player) {
		ItemStack helmet = worn(player);
		if (helmet.isEmpty() || player.isSpectator()) {
			return false;
		}

		return PoweredItem.use(helmet, DRAIN);
	}

	/**
	 * The light part of the hostile spawning rules at {@code pos}: block light at or below the dimension's limit (0 in
	 * the Overworld) and combined light no higher than 7, the most the game's random sky-light test can accept.
	 * Block type, space, biome and mob caps are separate rules, so "true" means "light allows", not "a mob will spawn".
	 */
	public static boolean lightAllowsSpawn(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
		int blockLight = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos);
		if (blockLight > level.dimensionType().monsterSpawnBlockLightLimit()) {
			return false;
		}

		return level.getMaxLocalRawBrightness(pos) <= 7;
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return !stack.isDamaged() || super.isBarVisible(stack);
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return stack.isDamaged() ? super.getBarWidth(stack) : PoweredItem.barWidth(stack);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return stack.isDamaged() ? super.getBarColor(stack) : PoweredItem.barColor(stack);
	}

	@Override
	public void addTooltipLines(ItemStack stack, Consumer<Component> builder) {
		builder.accept(PoweredItem.chargeLine(stack, DRAIN));
		builder.accept(Component.translatable("tooltip.solarquarry.scanner_helmet.drain", DRAIN, Math.round(CAPACITY / (double) DRAIN / 20.0 / 60.0)).withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("tooltip.solarquarry.scanner_helmet.use").withStyle(ChatFormatting.DARK_GRAY));
	}
}
