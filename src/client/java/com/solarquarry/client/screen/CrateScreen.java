package com.solarquarry.client.screen;

import com.solarquarry.storage.CrateMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Storage crate: one 9 x 6 page at a time, with page buttons for the larger tiers. */
public class CrateScreen extends AbstractContainerScreen<CrateMenu> {
	private static final int WIDTH = 176;
	private static final int HEIGHT = CrateMenu.HOTBAR_Y + 24;
	private IndustrialButton previous;
	private IndustrialButton next;

	public CrateScreen(CrateMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		this.titleLabelX = 8;
		this.titleLabelY = 6;
		this.inventoryLabelX = 8;
		this.inventoryLabelY = CrateMenu.INVENTORY_Y - 11;
	}

	@Override
	protected void init() {
		super.init();
		if (this.menu.tier().pages() > 1) {
			this.previous = this.addRenderableWidget(new IndustrialButton(this.leftPos + WIDTH - 52, this.topPos + 3, 14, 12, Component.literal("<"),
				input -> this.press(CrateMenu.BUTTON_PREVIOUS)));
			this.next = this.addRenderableWidget(new IndustrialButton(this.leftPos + WIDTH - 18, this.topPos + 3, 14, 12, Component.literal(">"),
				input -> this.press(CrateMenu.BUTTON_NEXT)));
		}
	}

	private void press(int id) {
		if (this.minecraft != null && this.minecraft.gameMode != null) {
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
		}
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		if (this.previous != null) {
			this.previous.active = this.menu.page() > 0;
			this.next.active = this.menu.page() < this.menu.tier().pages() - 1;
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		int x = this.leftPos;
		int y = this.topPos;
		GuiDraw.panel(g, x, y, WIDTH, HEIGHT);
		GuiDraw.hazard(g, x + 4, y + 15, WIDTH - 8, 2);
		for (int i = 0; i < this.menu.slots.size(); i++) {
			var slot = this.menu.slots.get(i);
			GuiDraw.slot(g, x + slot.x, y + slot.y);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFF1D8A6, true);
		g.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, GuiDraw.LABEL, false);
		if (this.menu.tier().pages() > 1) {
			g.centeredText(this.font, Component.literal((this.menu.page() + 1) + "/" + this.menu.tier().pages()), WIDTH - 27, 6, GuiDraw.LCD_TEXT);
		}
	}
}
