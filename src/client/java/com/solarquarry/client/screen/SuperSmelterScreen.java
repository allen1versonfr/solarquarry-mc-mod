package com.solarquarry.client.screen;

import com.solarquarry.smelting.SuperSmelterBlockEntity;
import com.solarquarry.smelting.SuperSmelterMenu;
import com.solarquarry.util.EnergyFormat;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Super Smelter: energy cell, three lanes with progress bars, status and power draw. */
public class SuperSmelterScreen extends AbstractContainerScreen<SuperSmelterMenu> {
	private static final int WIDTH = 176;
	private static final int HEIGHT = 186;
	private static final int BAR_X = 10;
	private static final int BAR_Y = 20;
	private static final int BAR_W = 12;
	private static final int BAR_H = 64;

	public SuperSmelterScreen(SuperSmelterMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		this.titleLabelX = 8;
		this.titleLabelY = 6;
		this.inventoryLabelX = 8;
		this.inventoryLabelY = SuperSmelterMenu.INVENTORY_Y - 11;
	}

	private static int statusColor(SuperSmelterBlockEntity.Status status) {
		return switch (status) {
			case IDLE -> 0xFF8A9199;
			case RUNNING -> 0xFF4CE06A;
			case NO_POWER -> 0xFFE04B3A;
			case OUTPUT_FULL -> 0xFFE07A2E;
		};
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		int x = this.leftPos;
		int y = this.topPos;
		GuiDraw.panel(g, x, y, WIDTH, HEIGHT);
		GuiDraw.hazard(g, x + 4, y + 15, WIDTH - 8, 2);
		long capacity = Math.max(1, this.menu.getCapacity());
		GuiDraw.energyBar(g, x + BAR_X, y + BAR_Y, BAR_W, BAR_H, this.menu.getEnergy() / (float) capacity);
		SuperSmelterBlockEntity.Status status = this.menu.getStatus();
		for (int lane = 0; lane < SuperSmelterBlockEntity.LANES; lane++) {
			int ly = y + SuperSmelterMenu.LANE_Y + lane * SuperSmelterMenu.LANE_STEP;
			GuiDraw.slot(g, x + SuperSmelterMenu.INPUT_X, ly);
			GuiDraw.slot(g, x + SuperSmelterMenu.OUTPUT_X, ly);
			float progress = this.menu.getProgress(lane);
			GuiDraw.hbar(g, x + SuperSmelterMenu.INPUT_X + 22, ly + 4, SuperSmelterMenu.OUTPUT_X - SuperSmelterMenu.INPUT_X - 26, 8, progress, 0xFFFF9A2E);
			boolean active = progress > 0.0F;
			GuiDraw.lamp(g, x + SuperSmelterMenu.OUTPUT_X + 24, ly + 4, active ? statusColor(status) : 0xFF555A60, active);
		}

		for (int i = SuperSmelterBlockEntity.SLOTS; i < this.menu.slots.size(); i++) {
			var slot = this.menu.slots.get(i);
			GuiDraw.slot(g, x + slot.x, y + slot.y);
		}

		GuiDraw.lamp(g, x + WIDTH - 16, y + 6, statusColor(status), status == SuperSmelterBlockEntity.Status.RUNNING);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFF1D8A6, true);
		g.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, GuiDraw.LABEL, false);
		SuperSmelterBlockEntity.Status status = this.menu.getStatus();
		Component statusText = Component.translatable("gui.solarquarry.smelter.status." + status.name().toLowerCase(java.util.Locale.ROOT));
		g.text(this.font, statusText, WIDTH - 22 - this.font.width(statusText), 6, statusColor(status), false);
		Component draw = Component.translatable("gui.solarquarry.smelter.draw", this.menu.getPowerDraw());
		g.text(this.font, draw, WIDTH - 8 - this.font.width(draw), this.inventoryLabelY, GuiDraw.LCD_DIM, false);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		super.extractTooltip(g, mouseX, mouseY);
		int rx = mouseX - this.leftPos;
		int ry = mouseY - this.topPos;
		if (rx >= BAR_X && rx < BAR_X + BAR_W && ry >= BAR_Y && ry < BAR_Y + BAR_H) {
			g.setComponentTooltipForNextFrame(this.font, List.of(
				Component.translatable("gui.solarquarry.smelter.energy", EnergyFormat.format(this.menu.getEnergy()), EnergyFormat.format(this.menu.getCapacity())),
				Component.translatable("gui.solarquarry.smelter.cost", SuperSmelterBlockEntity.LANE_POWER, SuperSmelterBlockEntity.LANE_POWER * SuperSmelterBlockEntity.COOK_TICKS),
				Component.translatable("gui.solarquarry.smelter.speed", 200 / SuperSmelterBlockEntity.COOK_TICKS)
			), mouseX, mouseY);
		}
	}
}
