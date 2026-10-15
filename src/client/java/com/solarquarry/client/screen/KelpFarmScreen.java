package com.solarquarry.client.screen;

import com.solarquarry.construction.ConstructionState;
import com.solarquarry.kelpfarm.KelpFarmBlockEntity;
import com.solarquarry.kelpfarm.KelpFarmMenu;
import com.solarquarry.util.EnergyFormat;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Kelp Farm: status LCD (construction, filling, growth, harvest totals), energy cell, kelp / bone meal / output slots. */
public class KelpFarmScreen extends AbstractContainerScreen<KelpFarmMenu> {
	private static final int WIDTH = 256;
	private static final int HEIGHT = 218;
	private static final int LCD_X = 26;
	private static final int LCD_Y = 18;
	private static final int LCD_W = 180;
	private static final int LCD_H = 40;
	private static final int BAR_X = 8;
	private static final int BAR_Y = 18;

	public KelpFarmScreen(KelpFarmMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		this.titleLabelX = 8;
		this.titleLabelY = 6;
		this.inventoryLabelX = KelpFarmMenu.INVENTORY_X;
		this.inventoryLabelY = KelpFarmMenu.INVENTORY_Y - 11;
	}

	static int statusColor(KelpFarmBlockEntity.Status status) {
		return switch (status) {
			case IDLE -> 0xFF8A9199;
			case PLANTING, GROWING, HARVESTING -> 0xFF4CE06A;
			case BUILDING, FILLING -> 0xFF4CE0C8;
			case NO_KELP -> 0xFFE0B341;
			case NO_POWER -> 0xFFE04B3A;
			case OUTPUT_FULL, BUILD_BLOCKED, FILL_BLOCKED -> 0xFFE07A2E;
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
		for (var slot : this.menu.slots) {
			GuiDraw.slot(g, x + slot.x, y + slot.y);
		}

		// Growth gauge: tallest kelp against the basin height.
		int gx = x + 68;
		int gy = y + KelpFarmMenu.ROW_Y;
		GuiDraw.inset(g, gx, gy, 10, 52, 0xFF0E2A33);
		int fill = Math.min(KelpFarmBlockEntity.HEIGHT, this.menu.getTallest()) * 50 / KelpFarmBlockEntity.HEIGHT;
		g.fill(gx + 1, gy + 51 - fill, gx + 9, gy + 51, 0xFF3FA65A);
		GuiDraw.lamp(g, x + WIDTH - 16, y + 6, statusColor(this.menu.getStatus()), this.menu.getStatus() != KelpFarmBlockEntity.Status.IDLE);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFF1D8A6, true);
		g.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, GuiDraw.LABEL, false);
		KelpFarmBlockEntity.Status status = this.menu.getStatus();
		g.text(this.font, Component.translatable("gui.solarquarry.kelp_farm.status." + status.name().toLowerCase(Locale.ROOT)), LCD_X + 3, LCD_Y + 3, statusColor(status), false);
		g.text(this.font, Component.translatable("gui.solarquarry.kelp_farm.columns", this.menu.getColumns(), (KelpFarmBlockEntity.RADIUS * 2 + 1) * (KelpFarmBlockEntity.RADIUS * 2 + 1)),
			LCD_X + 3, LCD_Y + 14, GuiDraw.LCD_TEXT, false);
		BlockPos blocked = this.menu.getBlockedAt();
		Component line3;
		if (blocked != null) {
			line3 = Component.translatable("gui.solarquarry.construction.blocked_at", blocked.getX(), blocked.getY(), blocked.getZ());
		} else if (this.menu.getConstructionState() != ConstructionState.COMPLETED) {
			line3 = Component.translatable("gui.solarquarry.construction.progress", this.menu.getConstructionPercent());
		} else {
			line3 = Component.translatable("gui.solarquarry.kelp_farm.harvested", this.menu.getHarvested());
		}

		g.text(this.font, line3, LCD_X + 3, LCD_Y + 25, GuiDraw.LCD_DIM, false);
		g.text(this.font, Component.translatable("gui.solarquarry.kelp_farm.kelp"), KelpFarmMenu.KELP_X, KelpFarmMenu.ROW_Y - 9, GuiDraw.LABEL, false);
		g.text(this.font, Component.translatable("gui.solarquarry.tree_farm.output"), KelpFarmMenu.OUTPUT_X, KelpFarmMenu.ROW_Y - 9, GuiDraw.LABEL, false);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		super.extractTooltip(g, mouseX, mouseY);
		int rx = mouseX - this.leftPos;
		int ry = mouseY - this.topPos;
		if (rx >= BAR_X && rx < BAR_X + 12 && ry >= BAR_Y && ry < BAR_Y + 40) {
			g.setComponentTooltipForNextFrame(this.font, List.of(
				Component.translatable("gui.solarquarry.smelter.energy", EnergyFormat.format(this.menu.getEnergy()), EnergyFormat.format(this.menu.getCapacity())),
				Component.translatable("gui.solarquarry.kelp_farm.costs")
			), mouseX, mouseY);
		} else if (rx >= LCD_X && rx < LCD_X + LCD_W && ry >= LCD_Y && ry < LCD_Y + LCD_H) {
			g.setComponentTooltipForNextFrame(this.font, List.of(
				Component.translatable("gui.solarquarry.kelp_farm.status." + this.menu.getStatus().name().toLowerCase(Locale.ROOT) + ".hint"),
				Component.translatable("gui.solarquarry.kelp_farm.upgrades", this.menu.getSpeed(), this.menu.getGrowth())
			), mouseX, mouseY);
		} else if (rx >= 68 && rx < 78 && ry >= KelpFarmMenu.ROW_Y && ry < KelpFarmMenu.ROW_Y + 52) {
			g.setTooltipForNextFrame(this.font, Component.translatable("gui.solarquarry.kelp_farm.gauge", this.menu.getTallest(), KelpFarmBlockEntity.HEIGHT), mouseX, mouseY);
		}
	}
}
