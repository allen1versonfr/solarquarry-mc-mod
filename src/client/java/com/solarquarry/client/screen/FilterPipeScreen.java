package com.solarquarry.client.screen;

import com.solarquarry.menu.FilterPipeMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class FilterPipeScreen extends AbstractContainerScreen<FilterPipeMenu> {
	private static final int WIDTH = 176;
	private static final int HEIGHT = 198;
	private IndustrialButton modeButton;
	private IndustrialButton componentsButton;

	public FilterPipeScreen(FilterPipeMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		this.titleLabelX = 8;
		this.titleLabelY = 6;
		this.inventoryLabelX = FilterPipeMenu.INVENTORY_X;
		this.inventoryLabelY = FilterPipeMenu.INVENTORY_Y - 11;
	}

	@Override
	protected void init() {
		super.init();
		this.modeButton = this.addRenderableWidget(new IndustrialButton(this.leftPos + 6, this.topPos + 26, 52, 16, Component.empty(),
			input -> this.press(FilterPipeMenu.BUTTON_TOGGLE_MODE)));
		this.componentsButton = this.addRenderableWidget(new IndustrialButton(this.leftPos + 6, this.topPos + 48, 52, 16, Component.empty(),
			input -> this.press(FilterPipeMenu.BUTTON_TOGGLE_COMPONENTS)).accent(0xFF47A7E0));
		this.updateLabels();
	}

	private void press(int id) {
		if (this.minecraft != null && this.minecraft.gameMode != null) {
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
		}
	}

	private void updateLabels() {
		if (this.modeButton == null) {
			return;
		}

		boolean whitelist = this.menu.isWhitelist();
		this.modeButton.setMessage(Component.translatable(whitelist ? "gui.solarquarry.whitelist" : "gui.solarquarry.blacklist"));
		this.modeButton.accent(whitelist ? 0xFF4CE06A : 0xFFE04B3A);
		this.componentsButton.setMessage(Component.translatable(this.menu.isMatchComponents() ? "gui.solarquarry.match_exact" : "gui.solarquarry.match_item"));
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		this.updateLabels();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		int x = this.leftPos;
		int y = this.topPos;
		GuiDraw.panel(g, x, y, WIDTH, HEIGHT);
		GuiDraw.hazard(g, x + 4, y + 17, WIDTH - 8, 3);
		boolean whitelist = this.menu.isWhitelist();
		GuiDraw.inset(g, x + FilterPipeMenu.FILTER_X - 4, y + FilterPipeMenu.FILTER_Y - 4, 62, 62, whitelist ? 0xFF13261A : 0xFF2A1416);
		for (int i = 0; i < 9; i++) {
			GuiDraw.slot(g, x + FilterPipeMenu.FILTER_X + (i % 3) * 18, y + FilterPipeMenu.FILTER_Y + (i / 3) * 18);
		}

		for (int i = 9; i < this.menu.slots.size(); i++) {
			var slot = this.menu.slots.get(i);
			GuiDraw.slot(g, x + slot.x, y + slot.y);
		}

		GuiDraw.lamp(g, x + WIDTH - 16, y + 6, whitelist ? 0xFF4CE06A : 0xFFE04B3A, true);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFF1D8A6, true);
		g.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, GuiDraw.LABEL, false);
		Component help = Component.translatable(this.menu.isWhitelist() ? "gui.solarquarry.filter_help_whitelist" : "gui.solarquarry.filter_help_blacklist");
		g.textWithWordWrap(this.font, help, 8, 82, WIDTH - 16, 0xFFAEB4BC, false);
	}
}
