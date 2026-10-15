package com.solarquarry.client.screen;

import com.solarquarry.charging.ChargingStationBlockEntity;
import com.solarquarry.charging.ChargingStationMenu;
import com.solarquarry.item.PoweredItem;
import com.solarquarry.util.EnergyFormat;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Charging Station: station energy, the item's charge bar between the slots, rate and status with warnings. */
public class ChargingStationScreen extends AbstractContainerScreen<ChargingStationMenu> {
	private static final int WIDTH = 176;
	private static final int HEIGHT = 174;
	private static final int BAR_X = 10;
	private static final int BAR_Y = 20;
	private static final int BAR_W = 12;
	private static final int BAR_H = 58;
	private static final int ITEM_BAR_X = ChargingStationMenu.INPUT_X + 22;
	private static final int ITEM_BAR_W = ChargingStationMenu.OUTPUT_X - ChargingStationMenu.INPUT_X - 26;

	public ChargingStationScreen(ChargingStationMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		this.titleLabelX = 8;
		this.titleLabelY = 6;
		this.inventoryLabelX = 8;
		this.inventoryLabelY = ChargingStationMenu.INVENTORY_Y - 11;
	}

	private static int statusColor(ChargingStationBlockEntity.Status status) {
		return switch (status) {
			case EMPTY -> 0xFF8A9199;
			case CHARGING -> 0xFF4CE06A;
			case NO_POWER -> 0xFFE04B3A;
			case DONE -> 0xFF4FB4E0;
			case OUTPUT_BLOCKED -> 0xFFE07A2E;
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
		GuiDraw.slot(g, x + ChargingStationMenu.INPUT_X, y + ChargingStationMenu.SLOT_Y);
		GuiDraw.slot(g, x + ChargingStationMenu.OUTPUT_X, y + ChargingStationMenu.SLOT_Y);
		ItemStack shown = this.menu.getShownItem();
		float charge = shown.isEmpty() ? 0.0F : PoweredItem.energy(shown) / (float) Math.max(1L, PoweredItem.capacityOf(shown));
		GuiDraw.hbar(g, x + ITEM_BAR_X, y + ChargingStationMenu.SLOT_Y + 4, ITEM_BAR_W, 8, charge, 0xFFF2C230);
		ChargingStationBlockEntity.Status status = this.menu.getStatus();
		GuiDraw.lamp(g, x + WIDTH - 16, y + 6, statusColor(status), status == ChargingStationBlockEntity.Status.CHARGING);
		GuiDraw.lcd(g, x + 30, y + 58, WIDTH - 38, 20);
		for (int i = ChargingStationBlockEntity.SLOTS; i < this.menu.slots.size(); i++) {
			var slot = this.menu.slots.get(i);
			GuiDraw.slot(g, x + slot.x, y + slot.y);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFF1D8A6, true);
		g.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, GuiDraw.LABEL, false);
		ChargingStationBlockEntity.Status status = this.menu.getStatus();
		Component statusText = Component.translatable("gui.solarquarry.charging.status." + status.name().toLowerCase(java.util.Locale.ROOT));
		g.text(this.font, statusText, WIDTH - 22 - this.font.width(statusText), 6, statusColor(status), false);
		ItemStack shown = this.menu.getShownItem();
		Component line1;
		Component line2;
		if (shown.isEmpty()) {
			line1 = Component.translatable("gui.solarquarry.charging.insert");
			line2 = Component.translatable("gui.solarquarry.charging.supported");
		} else {
			line1 = Component.translatable("gui.solarquarry.charging.item", EnergyFormat.format(PoweredItem.energy(shown)), EnergyFormat.format(PoweredItem.capacityOf(shown)));
			line2 = status == ChargingStationBlockEntity.Status.NO_POWER ? Component.translatable("gui.solarquarry.charging.no_power")
				: status == ChargingStationBlockEntity.Status.OUTPUT_BLOCKED ? Component.translatable("gui.solarquarry.charging.blocked")
				: Component.translatable("gui.solarquarry.charging.rate", this.menu.getRate(), ChargingStationBlockEntity.rateFor(shown));
		}

		g.text(this.font, line1, 33, 60, GuiDraw.LCD_TEXT, false);
		g.text(this.font, line2, 33, 69, status == ChargingStationBlockEntity.Status.NO_POWER ? 0xFFE04B3A : GuiDraw.LCD_DIM, false);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		super.extractTooltip(g, mouseX, mouseY);
		int rx = mouseX - this.leftPos;
		int ry = mouseY - this.topPos;
		if (rx >= BAR_X && rx < BAR_X + BAR_W && ry >= BAR_Y && ry < BAR_Y + BAR_H) {
			g.setComponentTooltipForNextFrame(this.font, List.of(
				Component.translatable("gui.solarquarry.charging.energy", EnergyFormat.format(this.menu.getEnergy()), EnergyFormat.format(this.menu.getCapacity())),
				Component.translatable("gui.solarquarry.charging.max_rate", ChargingStationBlockEntity.MAX_RATE)
			), mouseX, mouseY);
		}
	}
}
