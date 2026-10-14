package com.solarquarry.client.screen;

import com.solarquarry.blockentity.QuarryBlockEntity;
import com.solarquarry.menu.QuarryMenu;
import com.solarquarry.network.QuarryConfigPayload;
import com.solarquarry.quarry.QuarryStatus;
import com.solarquarry.util.EnergyFormat;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class QuarryScreen extends AbstractContainerScreen<QuarryMenu> {
	private static final int WIDTH = 344;
	private static final int HEIGHT = 198;
	private static final int BUTTON_X = 246;
	private static final int BUTTON_W = 90;
	private static final int LCD_X = 28;
	private static final int LCD_Y = 22;
	private static final int LCD_W = 152;
	private static final int LCD_H = 76;

	private IndustrialButton startButton;
	private IndustrialButton pauseButton;
	private IndustrialButton resumeButton;
	private IndustrialButton configButton;
	private IndustrialButton minusButton;
	private IndustrialButton plusButton;
	private IndustrialButton restartButton;

	public QuarryScreen(QuarryMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		this.titleLabelX = 10;
		this.titleLabelY = 6;
		this.inventoryLabelX = QuarryMenu.INVENTORY_X;
		this.inventoryLabelY = QuarryMenu.INVENTORY_Y - 11;
	}

	@Override
	protected void init() {
		super.init();
		int x = this.leftPos;
		int y = this.topPos;
		int bx = x + BUTTON_X;
		this.startButton = this.addRenderableWidget(new IndustrialButton(bx, y + 22, BUTTON_W, 16, Component.translatable("gui.solarquarry.start"),
			input -> this.pressButton(QuarryMenu.BUTTON_START)).accent(0xFF4CE06A));
		this.pauseButton = this.addRenderableWidget(new IndustrialButton(bx, y + 42, BUTTON_W, 16, Component.translatable("gui.solarquarry.pause"),
			input -> this.pressButton(QuarryMenu.BUTTON_PAUSE)).accent(0xFFE0B341));
		this.resumeButton = this.addRenderableWidget(new IndustrialButton(bx, y + 62, BUTTON_W, 16, Component.translatable("gui.solarquarry.resume"),
			input -> this.pressButton(QuarryMenu.BUTTON_RESUME)).accent(0xFF4CE06A));
		this.configButton = this.addRenderableWidget(new IndustrialButton(bx, y + 82, BUTTON_W, 16, Component.translatable("gui.solarquarry.config"),
			input -> this.toggleConfig()).accent(0xFF47A7E0));

		this.minusButton = this.addRenderableWidget(new IndustrialButton(x + LCD_X + 6, y + LCD_Y + 30, 20, 14, Component.literal("-"),
			input -> this.adjustTarget(input, -1)));
		this.plusButton = this.addRenderableWidget(new IndustrialButton(x + LCD_X + 72, y + LCD_Y + 30, 20, 14, Component.literal("+"),
			input -> this.adjustTarget(input, 1)));
		this.restartButton = this.addRenderableWidget(new IndustrialButton(x + LCD_X + 6, y + LCD_Y + 54, 86, 14, Component.translatable("gui.solarquarry.restart"),
			input -> this.pressButton(QuarryMenu.BUTTON_RESTART)).accent(0xFFE04B3A));
		this.updateButtons();
	}

	private void pressButton(int id) {
		if (this.minecraft != null && this.minecraft.gameMode != null) {
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
		}
	}

	private void toggleConfig() {
		this.menu.setConfigPage(!this.menu.isConfigPage());
		this.updateButtons();
	}

	private void adjustTarget(InputWithModifiers input, int direction) {
		int step = input.hasShiftDown() ? 10 : 1;
		int target = this.menu.getTargetY() + direction * step;
		target = Math.max(this.menu.getMinY(), Math.min(this.menu.getPos().getY(), target));
		ClientPlayNetworking.send(new QuarryConfigPayload(this.menu.getPos(), target));
	}

	private void updateButtons() {
		if (this.startButton == null) {
			return;
		}

		QuarryBlockEntity.Mode mode = this.menu.getMode();
		QuarryStatus status = this.menu.getStatus();
		this.startButton.active = mode == QuarryBlockEntity.Mode.IDLE || status == QuarryStatus.FINISHED;
		this.pauseButton.active = mode == QuarryBlockEntity.Mode.RUNNING && status != QuarryStatus.FINISHED;
		this.resumeButton.active = mode == QuarryBlockEntity.Mode.PAUSED;
		boolean config = this.menu.isConfigPage();
		this.configButton.setMessage(Component.translatable(config ? "gui.solarquarry.back" : "gui.solarquarry.config"));
		this.minusButton.visible = config;
		this.plusButton.visible = config;
		this.restartButton.visible = config;
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		this.updateButtons();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		int x = this.leftPos;
		int y = this.topPos;
		GuiDraw.panel(g, x, y, WIDTH, HEIGHT);
		GuiDraw.hazard(g, x + 4, y + 17, WIDTH - 8, 3);

		QuarryStatus status = this.menu.getStatus();
		GuiDraw.lamp(g, x + WIDTH - 16, y + 6, status.color(), status != QuarryStatus.IDLE);

		long energy = this.menu.getEnergy();
		long capacity = Math.max(1, this.menu.getEnergyCapacity());
		GuiDraw.energyBar(g, x + 8, y + LCD_Y, 14, LCD_H, (float) energy / capacity);
		GuiDraw.lcd(g, x + LCD_X, y + LCD_Y, LCD_W, LCD_H);

		// Upgrade bay.
		GuiDraw.inset(g, x + 186, y + 22, 54, 76, 0xFF2A2F35);
		for (int i = 0; i < QuarryBlockEntity.UPGRADE_SLOTS; i++) {
			GuiDraw.slot(g, x + QuarryMenu.UPGRADE_X + (i % 2) * 18, y + QuarryMenu.UPGRADE_Y + (i / 2) * 18);
		}

		for (Slot slot : this.menu.slots) {
			if (slot.index < QuarryMenu.UPGRADE_START || slot.index >= QuarryMenu.PLAYER_START) {
				GuiDraw.slot(g, x + slot.x, y + slot.y);
			}
		}

		if (this.menu.isConfigPage()) {
			for (int i = 0; i < QuarryBlockEntity.FILTER_SLOTS; i++) {
				int sx = x + QuarryMenu.FILTER_X + (i % 3) * 18;
				int sy = y + QuarryMenu.FILTER_Y + (i / 3) * 18;
				GuiDraw.inset(g, sx - 1, sy - 1, 18, 18, this.menu.hasVoidUpgrade() ? 0xFF2B1416 : 0xFF1A1D20);
			}
		}

		// Section plates and the divider between the machine buffer and the player inventory.
		GuiDraw.inset(g, x + QuarryMenu.BUFFER_X - 2, y + QuarryMenu.BUFFER_Y - 12, 164, 10, 0xFF2A2F35);
		GuiDraw.inset(g, x + QuarryMenu.INVENTORY_X - 2, y + QuarryMenu.INVENTORY_Y - 12, 164, 10, 0xFF2A2F35);
		GuiDraw.hazard(g, x + 4, y + 101, WIDTH - 8, 3);
		g.fill(x + 171, y + 106, x + 172, y + HEIGHT - 6, GuiDraw.STEEL_DARK);
		g.fill(x + 172, y + 106, x + 173, y + HEIGHT - 6, GuiDraw.STEEL_LIGHT);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFF1D8A6, true);
		QuarryStatus status = this.menu.getStatus();
		Component statusText = Component.translatable(status.translationKey());
		g.text(this.font, statusText, WIDTH - 20 - this.font.width(statusText), 6, status.color(), true);
		g.text(this.font, Component.translatable("gui.solarquarry.upgrades"), 189, 25, GuiDraw.LABEL, false);
		g.text(this.font, Component.translatable("gui.solarquarry.buffer"), QuarryMenu.BUFFER_X, QuarryMenu.BUFFER_Y - 11, GuiDraw.LABEL, false);
		g.text(this.font, this.playerInventoryTitle, QuarryMenu.INVENTORY_X, QuarryMenu.INVENTORY_Y - 11, GuiDraw.LABEL, false);
		Component mined = Component.translatable("gui.solarquarry.mined_total", this.menu.getBlocksMined());
		g.text(this.font, mined, QuarryMenu.BUFFER_X, QuarryMenu.HOTBAR_Y + 5, GuiDraw.LCD_DIM, false);

		if (this.menu.isConfigPage()) {
			this.drawConfigPage(g);
		} else {
			this.drawStatusPage(g, status);
		}
	}

	private void drawStatusPage(GuiGraphicsExtractor g, QuarryStatus status) {
		int lx = LCD_X + 4;
		int ly = LCD_Y + 3;
		float progress = this.menu.getProgressPermille() / 10.0F;
		int area = this.menu.getAreaSize();
		this.lcdLine(g, lx, ly, "gui.solarquarry.lcd.status", Component.translatable(status.translationKey()), status.color());
		this.lcdLine(g, lx, ly + 9, "gui.solarquarry.lcd.progress", Component.literal(String.format(Locale.ROOT, "%.1f%%", progress)), GuiDraw.LCD_TEXT);
		GuiDraw.hbar(g, LCD_X + 100, ly + 9, 48, 7, progress / 100.0F, 0xFF4CE06A);
		this.lcdLine(g, lx, ly + 18, "gui.solarquarry.lcd.position",
			Component.literal("X " + this.menu.getCursorX() + "  Z " + this.menu.getCursorZ()), GuiDraw.LCD_TEXT);
		this.lcdLine(g, lx, ly + 27, "gui.solarquarry.lcd.depth",
			Component.literal("Y " + this.menu.getCurrentY() + " → " + this.menu.getTargetY()), GuiDraw.LCD_TEXT);
		this.lcdLine(g, lx, ly + 36, "gui.solarquarry.lcd.area", Component.literal(area + " x " + area), GuiDraw.LCD_TEXT);
		this.lcdLine(g, lx, ly + 45, "gui.solarquarry.lcd.speed",
			Component.translatable("gui.solarquarry.blocks_per_minute", this.menu.getBlocksPerMinute()), GuiDraw.LCD_TEXT);
		this.lcdLine(g, lx, ly + 54, "gui.solarquarry.lcd.energy",
			Component.literal(EnergyFormat.format(this.menu.getEnergy()) + " / " + EnergyFormat.format(this.menu.getEnergyCapacity())), 0xFFFFE066);
		this.lcdLine(g, lx, ly + 63, "gui.solarquarry.lcd.usage",
			Component.translatable("gui.solarquarry.energy_per_block", this.menu.getEnergyPerBlock(), this.menu.getBlocksMined()), GuiDraw.LCD_DIM);
	}

	private void lcdLine(GuiGraphicsExtractor g, int x, int y, String labelKey, Component value, int valueColor) {
		Component label = Component.translatable(labelKey);
		g.text(this.font, label, x, y, GuiDraw.LCD_DIM, false);
		g.text(this.font, value, x + 46, y, valueColor, false);
	}

	private void drawConfigPage(GuiGraphicsExtractor g) {
		int lx = LCD_X + 6;
		g.text(this.font, Component.translatable("gui.solarquarry.target_depth"), lx, LCD_Y + 5, GuiDraw.LCD_DIM, false);
		g.text(this.font, Component.translatable("gui.solarquarry.target_range", this.menu.getMinY(), this.menu.getPos().getY()), lx, LCD_Y + 16, 0xFF3D7A5E, false);
		g.centeredText(this.font, Component.literal("Y " + this.menu.getTargetY()), LCD_X + 49, LCD_Y + 33, 0xFFFFE066);
		g.text(this.font, Component.translatable("gui.solarquarry.shift_hint"), lx, LCD_Y + 45, 0xFF3D7A5E, false);
		boolean voiding = this.menu.hasVoidUpgrade();
		g.text(this.font, Component.translatable(voiding ? "gui.solarquarry.void_filter" : "gui.solarquarry.void_filter_off"),
			QuarryMenu.FILTER_X - 2, LCD_Y + 4, voiding ? 0xFFFF7A6A : GuiDraw.LCD_DIM, false);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		super.extractTooltip(g, mouseX, mouseY);
		int x = this.leftPos;
		int y = this.topPos;
		if (mouseX >= x + 8 && mouseX < x + 22 && mouseY >= y + LCD_Y && mouseY < y + LCD_Y + LCD_H) {
			g.setComponentTooltipForNextFrame(this.font, List.of(
				Component.translatable("gui.solarquarry.energy"),
				Component.literal(EnergyFormat.format(this.menu.getEnergy()) + " / " + EnergyFormat.format(this.menu.getEnergyCapacity()))
			), mouseX, mouseY);
		}
	}
}
