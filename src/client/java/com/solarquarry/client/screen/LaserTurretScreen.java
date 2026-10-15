package com.solarquarry.client.screen;

import com.solarquarry.turret.LaserTurretBlockEntity;
import com.solarquarry.turret.LaserTurretMenu;
import com.solarquarry.turret.TurretTier;
import com.solarquarry.util.EnergyFormat;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;

/** Laser Turret control panel: power and targeting buttons, energy, status, target, range and costs. */
public class LaserTurretScreen extends AbstractContainerScreen<LaserTurretMenu> {
	private static final int WIDTH = 200;
	private static final int HEIGHT = 132;
	private IndustrialButton power;
	private IndustrialButton mode;

	public LaserTurretScreen(LaserTurretMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		this.titleLabelX = 8;
		this.titleLabelY = 6;
	}

	@Override
	protected void init() {
		super.init();
		this.power = this.addRenderableWidget(new IndustrialButton(this.leftPos + 30, this.topPos + 104, 66, 20, Component.empty(),
			b -> this.press(LaserTurretBlockEntity.BUTTON_POWER)));
		this.mode = this.addRenderableWidget(new IndustrialButton(this.leftPos + 102, this.topPos + 104, 90, 20, Component.empty(),
			b -> this.press(LaserTurretBlockEntity.BUTTON_MODE)));
	}

	private void press(int id) {
		if (this.minecraft != null && this.minecraft.gameMode != null) {
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
		}
	}

	private static int statusColor(LaserTurretBlockEntity.Status status) {
		return switch (status) {
			case OFF -> 0xFF8A9199;
			case IDLE -> 0xFF4FB4E0;
			case FIRING -> 0xFFE5534B;
			case NO_POWER -> 0xFFE07A2E;
			case DAMAGED -> 0xFF6E747C;
		};
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		boolean off = this.menu.getStatus() == LaserTurretBlockEntity.Status.OFF;
		this.power.setMessage(Component.translatable(off ? "gui.solarquarry.turret.turn_on" : "gui.solarquarry.turret.turn_off"));
		this.power.accent(off ? 0xFF4CE06A : 0xFFE04B3A);
		this.mode.setMessage(Component.translatable("gui.solarquarry.turret.mode." + this.menu.getMode().name().toLowerCase(Locale.ROOT)));
		boolean damaged = this.menu.getStatus() == LaserTurretBlockEntity.Status.DAMAGED;
		this.power.active = !damaged;
		this.mode.active = !damaged;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		int x = this.leftPos;
		int y = this.topPos;
		GuiDraw.panel(g, x, y, WIDTH, HEIGHT);
		GuiDraw.hazard(g, x + 4, y + 15, WIDTH - 8, 2);
		GuiDraw.energyBar(g, x + 10, y + 22, 12, 100, this.menu.getEnergy() / (float) Math.max(1, this.menu.getCapacity()));
		GuiDraw.lcd(g, x + 30, y + 22, WIDTH - 38, 76);
		LaserTurretBlockEntity.Status status = this.menu.getStatus();
		GuiDraw.lamp(g, x + WIDTH - 16, y + 6, statusColor(status), status == LaserTurretBlockEntity.Status.FIRING);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFF1D8A6, true);
		LaserTurretBlockEntity.Status status = this.menu.getStatus();
		Component statusText = Component.translatable("gui.solarquarry.turret.status." + status.name().toLowerCase(Locale.ROOT));
		g.text(this.font, statusText, WIDTH - 22 - this.font.width(statusText), 6, statusColor(status), false);
		TurretTier tier = this.menu.getTier();
		int lx = 34;
		int ly = 26;
		g.text(this.font, Component.translatable("gui.solarquarry.turret.energy", EnergyFormat.format(this.menu.getEnergy()), EnergyFormat.format(this.menu.getCapacity())), lx, ly, GuiDraw.LCD_TEXT, false);
		g.text(this.font, Component.translatable("gui.solarquarry.turret.stats", tier.range(), String.format(Locale.ROOT, "%.0f", tier.damage()),
			String.format(Locale.ROOT, "%.1f", 20.0 / tier.cooldown())), lx, ly + 11, GuiDraw.LCD_DIM, false);
		g.text(this.font, Component.translatable("gui.solarquarry.turret.cost", tier.shotCost()), lx, ly + 22, GuiDraw.LCD_DIM, false);
		Entity target = this.minecraft != null && this.minecraft.level != null && this.menu.getTargetId() >= 0 ? this.minecraft.level.getEntity(this.menu.getTargetId()) : null;
		Component targetText = target != null ? Component.translatable("gui.solarquarry.turret.target", target.getName())
			: Component.translatable("gui.solarquarry.turret.no_target");
		g.text(this.font, targetText, lx, ly + 33, target != null ? 0xFFFF8A80 : GuiDraw.LCD_DIM, false);
		int input = this.menu.getInputRate();
		Component supply = input > 0 ? Component.translatable("gui.solarquarry.turret.supply", input)
			: Component.translatable("gui.solarquarry.turret.no_supply");
		g.text(this.font, supply, lx, ly + 44, input > 0 ? GuiDraw.LCD_TEXT : 0xFFE07A2E, false);
		Component warning = switch (status) {
			case NO_POWER -> Component.translatable("gui.solarquarry.turret.warn.power");
			case DAMAGED -> Component.translatable("gui.solarquarry.turret.warn.damaged");
			case OFF -> Component.translatable("gui.solarquarry.turret.warn.off");
			default -> Component.translatable("gui.solarquarry.turret.shots", this.menu.getShots());
		};
		g.text(this.font, warning, lx, ly + 58, status == LaserTurretBlockEntity.Status.NO_POWER ? 0xFFE04B3A : GuiDraw.LCD_DIM, false);
	}
}
