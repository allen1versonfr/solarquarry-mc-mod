package com.solarquarry.client.screen;

import com.solarquarry.treefarm.TreeFarmBlockEntity;
import com.solarquarry.treefarm.TreeFarmMenu;
import com.solarquarry.util.EnergyFormat;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Tree Farm: status LCD, energy cell, sapling / bone meal / output slots and upgrades. */
public class TreeFarmScreen extends AbstractContainerScreen<TreeFarmMenu> {
	private static final int WIDTH = 256;
	private static final int HEIGHT = 218;
	private static final int LCD_X = 26;
	private static final int LCD_Y = 18;
	private static final int LCD_W = 180;
	private static final int LCD_H = 40;
	private static final int BAR_X = 8;
	private static final int BAR_Y = 18;

	public TreeFarmScreen(TreeFarmMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		this.titleLabelX = 8;
		this.titleLabelY = 6;
		this.inventoryLabelX = TreeFarmMenu.INVENTORY_X;
		this.inventoryLabelY = TreeFarmMenu.INVENTORY_Y - 11;
	}

	static int statusColor(TreeFarmBlockEntity.Status status) {
		return switch (status) {
			case IDLE -> 0xFF8A9199;
			case PLANTING, GROWING, HARVESTING -> 0xFF4CE06A;
			case BUILDING -> 0xFF4CE0C8;
			case NO_SAPLINGS, NO_GROUND, NO_ROOM -> 0xFFE0B341;
			case NO_POWER -> 0xFFE04B3A;
			case OUTPUT_FULL, BUILD_BLOCKED -> 0xFFE07A2E;
		};
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		int x = this.leftPos;
		int y = this.topPos;
		GuiDraw.panel(g, x, y, WIDTH, HEIGHT);
		GuiDraw.hazard(g, x + 4, y + 15, WIDTH - 8, 2);
		GuiDraw.energyBar(g, x + BAR_X, y + BAR_Y, 12, 40, this.menu.getEnergy() / (float) Math.max(1, this.menu.getCapacity()));
		GuiDraw.lcd(g, x + LCD_X, y + LCD_Y, LCD_W, LCD_H);
		for (int i = 0; i < TreeFarmBlockEntity.TOTAL_SLOTS + 36; i++) {
			var slot = this.menu.slots.get(i);
			GuiDraw.slot(g, x + slot.x, y + slot.y);
		}

		GuiDraw.lamp(g, x + WIDTH - 16, y + 6, statusColor(this.menu.getStatus()), this.menu.getStatus() != TreeFarmBlockEntity.Status.IDLE);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFF1D8A6, true);
		g.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, GuiDraw.LABEL, false);
		TreeFarmBlockEntity.Status status = this.menu.getStatus();
		g.text(this.font, Component.translatable("gui.solarquarry.tree_farm.status." + status.name().toLowerCase(Locale.ROOT)), LCD_X + 3, LCD_Y + 3, statusColor(status), false);
		int size = this.menu.getRadius() * 2 + 1;
		g.text(this.font, Component.translatable("gui.solarquarry.tree_farm.area", size, size, this.menu.getPlanted()), LCD_X + 3, LCD_Y + 14, GuiDraw.LCD_TEXT, false);
		net.minecraft.core.BlockPos blocked = this.menu.getBlockedAt();
		Component line3;
		if (blocked != null) {
			line3 = Component.translatable("gui.solarquarry.construction.blocked_at", blocked.getX(), blocked.getY(), blocked.getZ());
		} else if (this.menu.getConstructionState() != com.solarquarry.construction.ConstructionState.COMPLETED) {
			line3 = Component.translatable("gui.solarquarry.construction.progress", this.menu.getConstructionPercent());
		} else if (status == TreeFarmBlockEntity.Status.HARVESTING) {
			line3 = Component.translatable("gui.solarquarry.tree_farm.felling", this.menu.getFellingPercent());
		} else {
			line3 = Component.translatable("gui.solarquarry.tree_farm.totals", this.menu.getTreesFelled(), this.menu.getSaplingsPlanted());
		}
		g.text(this.font, line3, LCD_X + 3, LCD_Y + 25, GuiDraw.LCD_DIM, false);
		g.text(this.font, Component.translatable("gui.solarquarry.tree_farm.saplings"), TreeFarmMenu.SAPLING_X, TreeFarmMenu.SAPLING_Y - 9, GuiDraw.LABEL, false);
		g.text(this.font, Component.translatable("gui.solarquarry.tree_farm.output"), TreeFarmMenu.OUTPUT_X, TreeFarmMenu.SAPLING_Y - 9, GuiDraw.LABEL, false);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		super.extractTooltip(g, mouseX, mouseY);
		int rx = mouseX - this.leftPos;
		int ry = mouseY - this.topPos;
		if (rx >= BAR_X && rx < BAR_X + 12 && ry >= BAR_Y && ry < BAR_Y + 40) {
			g.setComponentTooltipForNextFrame(this.font, List.of(
				Component.translatable("gui.solarquarry.smelter.energy", EnergyFormat.format(this.menu.getEnergy()), EnergyFormat.format(this.menu.getCapacity())),
				Component.translatable("gui.solarquarry.tree_farm.costs"),
				Component.translatable("gui.solarquarry.tree_farm.speed", this.menu.getTicksPerOperation())
			), mouseX, mouseY);
		} else if (rx >= LCD_X && rx < LCD_X + LCD_W && ry >= LCD_Y && ry < LCD_Y + LCD_H) {
			g.setComponentTooltipForNextFrame(this.font, List.of(
				Component.translatable("gui.solarquarry.tree_farm.status." + this.menu.getStatus().name().toLowerCase(Locale.ROOT) + ".hint"),
				Component.translatable("gui.solarquarry.tree_farm.upgrades", this.menu.getSpeed(), this.menu.getGrowth(),
					Component.translatable(this.menu.hasHarvest() ? "gui.yes" : "gui.no"))
			), mouseX, mouseY);
		}
	}
}
