package com.solarquarry.client.screen;

import com.solarquarry.blockentity.QuarryBlockEntity;
import com.solarquarry.menu.QuarryMenu;
import com.solarquarry.network.QuarryConfigPayload;
import com.solarquarry.quarry.QuarryMode;
import com.solarquarry.quarry.QuarryStatus;
import com.solarquarry.util.EnergyFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Quarry control panel. The status page shows live machine data; the configuration page changes mode, area and depth
 * and edits the void/skip/ore lists. Every change is sent to the server, which validates it.
 */
public class QuarryScreen extends AbstractContainerScreen<QuarryMenu> {
	private static final int WIDTH = 344;
	private static final int HEIGHT = 210;
	private static final int BUTTON_X = 246;
	private static final int BUTTON_W = 90;
	private static final int LCD_X = 28;
	private static final int LCD_Y = 22;
	private static final int LCD_W = 152;
	private static final int LCD_H = 86;
	private static final int BAY_X = 186;
	private static final int BAY_Y = 22;
	private static final int BAY_W = 56;
	private static final int BAY_H = 86;
	private static final int HINT_Y = 184;
	private static final int ROW_LABEL_X = 10;
	private static final int ROW_MINUS_X = 74;
	private static final int ROW_PLUS_X = 150;

	private IndustrialButton startButton;
	private IndustrialButton pauseButton;
	private IndustrialButton resumeButton;
	private IndustrialButton configButton;
	private IndustrialButton modeButton;
	private IndustrialButton listButton;
	private IndustrialButton oresOnlyButton;
	private IndustrialButton restartButton;
	private IndustrialButton xpButton;
	private final List<IndustrialButton> configWidgets = new ArrayList<>();
	private final IndustrialButton[] minus = new IndustrialButton[3];
	private final IndustrialButton[] plus = new IndustrialButton[3];

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
		this.configWidgets.clear();
		int x = this.leftPos;
		int y = this.topPos;
		int bx = x + BUTTON_X;
		this.startButton = this.addRenderableWidget(new IndustrialButton(bx, y + 22, BUTTON_W, 18, Component.translatable("gui.solarquarry.start"),
			input -> this.pressButton(QuarryMenu.BUTTON_START)).accent(0xFF4CE06A));
		this.startButton.setTooltip(Tooltip.create(Component.translatable("gui.solarquarry.start.tooltip")));
		this.pauseButton = this.addRenderableWidget(new IndustrialButton(bx, y + 44, BUTTON_W, 18, Component.translatable("gui.solarquarry.pause"),
			input -> this.pressButton(QuarryMenu.BUTTON_PAUSE)).accent(0xFFE0B341));
		this.pauseButton.setTooltip(Tooltip.create(Component.translatable("gui.solarquarry.pause.tooltip")));
		this.resumeButton = this.addRenderableWidget(new IndustrialButton(bx, y + 66, BUTTON_W, 18, Component.translatable("gui.solarquarry.resume"),
			input -> this.pressButton(QuarryMenu.BUTTON_RESUME)).accent(0xFF4CE06A));
		this.resumeButton.setTooltip(Tooltip.create(Component.translatable("gui.solarquarry.resume.tooltip")));
		this.configButton = this.addRenderableWidget(new IndustrialButton(bx, y + 88, BUTTON_W, 18, Component.translatable("gui.solarquarry.config"),
			input -> this.toggleConfig()).accent(0xFF47A7E0));
		this.xpButton = this.addRenderableWidget(new IndustrialButton(x + QuarryMenu.TORCH_X + 19, y + QuarryMenu.TORCH_Y, 18, 18, Component.translatable("gui.solarquarry.xp"),
			input -> this.pressButton(QuarryMenu.BUTTON_COLLECT_XP)).accent(0xFF7CE04B));

		// Configuration page.
		this.modeButton = this.config(new IndustrialButton(x + 8, y + 23, 172, 14, Component.empty(), this::cycleMode).accent(0xFF47A7E0));
		for (int row = 0; row < 3; row++) {
			int ry = y + 40 + row * 16;
			final int r = row;
			this.minus[row] = this.config(new IndustrialButton(x + ROW_MINUS_X, ry, 18, 14, Component.literal("-"), input -> this.adjust(r, -1, input)));
			this.plus[row] = this.config(new IndustrialButton(x + ROW_PLUS_X, ry, 18, 14, Component.literal("+"), input -> this.adjust(r, 1, input)));
		}

		this.oresOnlyButton = this.config(new IndustrialButton(x + 8, y + 89, 108, 16, Component.empty(),
			input -> this.send(QuarryConfigPayload.ORES_ONLY, this.menu.isOresOnly() ? 0 : 1)).accent(0xFFE0B341));
		this.oresOnlyButton.setTooltip(Tooltip.create(Component.translatable("gui.solarquarry.ores_only.tooltip")));
		this.restartButton = this.config(new IndustrialButton(x + 120, y + 89, 60, 16, Component.translatable("gui.solarquarry.restart"),
			input -> this.pressButton(QuarryMenu.BUTTON_RESTART)).accent(0xFFE04B3A));
		this.restartButton.setTooltip(Tooltip.create(Component.translatable("gui.solarquarry.restart.tooltip")));
		this.listButton = this.config(new IndustrialButton(x + BAY_X, y + BAY_Y, BAY_W, 14, Component.empty(), this::cycleList).accent(0xFFE0B341));
		this.updateButtons();
	}

	private IndustrialButton config(IndustrialButton button) {
		this.addRenderableWidget(button);
		this.configWidgets.add(button);
		return button;
	}

	private void pressButton(int id) {
		if (this.minecraft != null && this.minecraft.gameMode != null) {
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
		}
	}

	private void send(int setting, int value) {
		ClientPlayNetworking.send(new QuarryConfigPayload(this.menu.getPos(), setting, value));
	}

	private void toggleConfig() {
		this.menu.setConfigPage(!this.menu.isConfigPage());
		this.updateButtons();
	}

	private void cycleMode(InputWithModifiers input) {
		QuarryMode current = this.menu.getQuarryMode();
		QuarryMode next = input.hasShiftDown() ? current.previous() : current.next();
		this.send(QuarryConfigPayload.MODE, next.ordinal());
	}

	private void cycleList(InputWithModifiers input) {
		QuarryBlockEntity.ListKind[] kinds = QuarryBlockEntity.ListKind.values();
		this.menu.setListKind(kinds[(this.menu.getListKind().ordinal() + 1) % kinds.length]);
		this.updateButtons();
	}

	/** Row 0 = width, row 1 = length, row 2 = target depth (downward modes) or height (upward/tunnel modes). */
	private void adjust(int row, int direction, InputWithModifiers input) {
		QuarryMode mode = this.menu.getQuarryMode();
		switch (row) {
			case 0 -> {
				int step = input.hasShiftDown() ? 4 : 1;
				this.send(QuarryConfigPayload.WIDTH, clamp(this.menu.getAreaWidth() + direction * step, 1, this.menu.getMaxAreaSize()));
			}
			case 1 -> {
				int step = input.hasShiftDown() ? (mode == QuarryMode.TUNNEL_BORE ? 16 : 4) : 1;
				this.send(QuarryConfigPayload.LENGTH, clamp(this.menu.getAreaLength() + direction * step, 1, this.menu.getMaxLength()));
			}
			default -> {
				int step = input.hasShiftDown() ? 10 : 1;
				if (mode.usesTargetDepth()) {
					this.send(QuarryConfigPayload.TARGET_Y, clamp(this.menu.getTargetY() + direction * step, this.menu.getMinY(), this.menu.getPos().getY()));
				} else {
					this.send(QuarryConfigPayload.HEIGHT, clamp(this.menu.getAreaHeight() + direction * step, 1, this.menu.getMaxHeight()));
				}
			}
		}
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private void updateButtons() {
		if (this.startButton == null) {
			return;
		}

		QuarryBlockEntity.Mode mode = this.menu.getMode();
		QuarryStatus status = this.menu.getStatus();
		this.startButton.active = mode == QuarryBlockEntity.Mode.IDLE || status == QuarryStatus.COMPLETE;
		this.pauseButton.active = mode == QuarryBlockEntity.Mode.RUNNING && status != QuarryStatus.COMPLETE;
		this.resumeButton.active = mode == QuarryBlockEntity.Mode.PAUSED;
		boolean config = this.menu.isConfigPage();
		this.configButton.setMessage(Component.translatable(config ? "gui.solarquarry.back" : "gui.solarquarry.config"));
		int xp = this.menu.getStoredXp();
		this.xpButton.visible = !config;
		this.xpButton.active = xp > 0;
		this.xpButton.setTooltip(Tooltip.create(Component.translatable("gui.solarquarry.xp.tooltip", xp)));
		for (IndustrialButton widget : this.configWidgets) {
			widget.visible = config;
		}

		QuarryMode quarryMode = this.menu.getQuarryMode();
		this.modeButton.setMessage(Component.translatable("gui.solarquarry.mode_button", Component.translatable(quarryMode.translationKey())));
		this.modeButton.setTooltip(Tooltip.create(Component.translatable(quarryMode.descriptionKey())
			.append("\n").append(Component.translatable("gui.solarquarry.mode_button.tooltip").withStyle(ChatFormatting.GRAY))));
		this.oresOnlyButton.visible = config && quarryMode == QuarryMode.ORE_PRIORITY;
		this.oresOnlyButton.setMessage(Component.translatable(this.menu.isOresOnly() ? "gui.solarquarry.ores_only.on" : "gui.solarquarry.ores_only.off"));
		this.listButton.setMessage(Component.translatable("gui.solarquarry.list." + this.menu.getListKind().name().toLowerCase(Locale.ROOT)));
		this.listButton.setTooltip(Tooltip.create(Component.translatable("gui.solarquarry.list." + this.menu.getListKind().name().toLowerCase(Locale.ROOT) + ".tooltip")));

		if (config) {
			this.minus[0].active = this.menu.getAreaWidth() > 1;
			this.plus[0].active = this.menu.getAreaWidth() < this.menu.getMaxAreaSize();
			this.minus[1].active = this.menu.getAreaLength() > 1;
			this.plus[1].active = this.menu.getAreaLength() < this.menu.getMaxLength();
			if (quarryMode.usesTargetDepth()) {
				this.minus[2].active = this.menu.getTargetY() > this.menu.getMinY();
				this.plus[2].active = this.menu.getTargetY() < this.menu.getPos().getY();
			} else {
				this.minus[2].active = this.menu.getAreaHeight() > 1;
				this.plus[2].active = this.menu.getAreaHeight() < this.menu.getMaxHeight();
			}
		}
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		this.updateButtons();
	}

	// =====================================================================================
	// Drawing
	// =====================================================================================

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		int x = this.leftPos;
		int y = this.topPos;
		GuiDraw.panel(g, x, y, WIDTH, HEIGHT);
		GuiDraw.hazard(g, x + 4, y + 17, WIDTH - 8, 3);

		QuarryStatus status = this.menu.getStatus();
		GuiDraw.lamp(g, x + WIDTH - 16, y + 6, status.color(), status != QuarryStatus.IDLE);

		boolean config = this.menu.isConfigPage();
		if (config) {
			GuiDraw.lcd(g, x + 8, y + LCD_Y, LCD_W + 20, LCD_H);
		} else {
			long energy = this.menu.getEnergy();
			long capacity = Math.max(1, this.menu.getEnergyCapacity());
			GuiDraw.energyBar(g, x + 8, y + LCD_Y, 14, LCD_H, (float) energy / capacity);
			GuiDraw.lcd(g, x + LCD_X, y + LCD_Y, LCD_W, LCD_H);
		}

		// Upgrade bay or list editor.
		GuiDraw.inset(g, x + BAY_X, y + BAY_Y, BAY_W, BAY_H, 0xFF2A2F35);
		if (config) {
			boolean voidList = this.menu.getListKind() == QuarryBlockEntity.ListKind.VOID;
			int tint = voidList && this.menu.hasVoidUpgrade() ? 0xFF2B1416 : 0xFF1A1D20;
			for (int i = 0; i < QuarryBlockEntity.FILTER_SLOTS; i++) {
				int sx = x + QuarryMenu.LIST_X + (i % 3) * 18;
				int sy = y + QuarryMenu.LIST_Y + (i / 3) * 18;
				GuiDraw.inset(g, sx - 1, sy - 1, 18, 18, tint);
			}
		} else {
			for (int i = 0; i < QuarryBlockEntity.UPGRADE_SLOTS; i++) {
				GuiDraw.slot(g, x + QuarryMenu.UPGRADE_X + (i % 3) * 18, y + QuarryMenu.UPGRADE_Y + (i / 3) * 18);
			}

			GuiDraw.slot(g, x + QuarryMenu.TORCH_X, y + QuarryMenu.TORCH_Y);
			GuiDraw.slot(g, x + QuarryMenu.DRONE_X, y + QuarryMenu.TORCH_Y);
		}

		for (Slot slot : this.menu.slots) {
			if (slot.index < QuarryMenu.UPGRADE_START || slot.index >= QuarryMenu.PLAYER_START) {
				GuiDraw.slot(g, x + slot.x, y + slot.y);
			}
		}

		GuiDraw.hazard(g, x + 4, y + 111, WIDTH - 8, 3);
		GuiDraw.inset(g, x + QuarryMenu.BUFFER_X - 2, y + QuarryMenu.BUFFER_Y - 12, 164, 10, 0xFF2A2F35);
		GuiDraw.inset(g, x + QuarryMenu.INVENTORY_X - 2, y + QuarryMenu.INVENTORY_Y - 12, 164, 10, 0xFF2A2F35);
		g.fill(x + 171, y + 116, x + 172, y + HEIGHT - 6, GuiDraw.STEEL_DARK);
		g.fill(x + 172, y + 116, x + 173, y + HEIGHT - 6, GuiDraw.STEEL_LIGHT);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFF1D8A6, true);
		QuarryStatus status = this.menu.getStatus();
		Component statusText = Component.translatable(status.translationKey());
		g.text(this.font, statusText, WIDTH - 20 - this.font.width(statusText), 6, status.color(), true);

		int used = this.menu.getUsedBufferSlots();
		Component bufferLabel = Component.translatable("gui.solarquarry.buffer_count", used, QuarryBlockEntity.BUFFER_SLOTS);
		g.text(this.font, bufferLabel, QuarryMenu.BUFFER_X, QuarryMenu.BUFFER_Y - 11, used >= QuarryBlockEntity.BUFFER_SLOTS ? 0xFFFF7A6A : GuiDraw.LABEL, false);
		g.text(this.font, this.playerInventoryTitle, QuarryMenu.INVENTORY_X, QuarryMenu.INVENTORY_Y - 11, GuiDraw.LABEL, false);

		if (this.menu.isConfigPage()) {
			this.drawConfigPage(g);
		} else {
			g.text(this.font, Component.translatable("gui.solarquarry.upgrades"), BAY_X + 3, BAY_Y + 3, GuiDraw.LABEL, false);
			this.drawStatusPage(g, status);
		}

		this.drawHint(g, status);
	}

	private void drawStatusPage(GuiGraphicsExtractor g, QuarryStatus status) {
		int lx = LCD_X + 4;
		int ly = LCD_Y + 3;
		QuarryMode mode = this.menu.getQuarryMode();
		float progress = this.menu.getProgressPermille() / 10.0F;
		this.lcdLine(g, lx, ly, "gui.solarquarry.lcd.status", Component.translatable(status.translationKey()), status.color());
		this.lcdLine(g, lx, ly + 9, "gui.solarquarry.lcd.mode", Component.translatable(mode.translationKey()), GuiDraw.LCD_TEXT);
		this.lcdLine(g, lx, ly + 18, "gui.solarquarry.lcd.progress", Component.literal(String.format(Locale.ROOT, "%.1f%%", progress)), GuiDraw.LCD_TEXT);
		GuiDraw.hbar(g, LCD_X + 100, ly + 18, 48, 7, progress / 100.0F, 0xFF4CE06A);

		Component position;
		if (mode == QuarryMode.ORE_PRIORITY && this.menu.getOreQueueSize() > 0) {
			position = Component.translatable("gui.solarquarry.lcd.ores_queued", this.menu.getOreQueueSize());
		} else if (mode == QuarryMode.TUNNEL_BORE) {
			position = Component.translatable("gui.solarquarry.lcd.slice", this.menu.getCursorForward(), this.menu.getAreaLength());
		} else {
			position = Component.translatable("gui.solarquarry.lcd.cell", this.menu.getCursorLateral() + 1, this.menu.getAreaWidth(),
				this.menu.getCursorForward(), this.menu.getAreaLength());
		}

		this.lcdLine(g, lx, ly + 27, "gui.solarquarry.lcd.position", position, GuiDraw.LCD_TEXT);
		Component depth = mode.usesTargetDepth()
			? Component.literal("Y " + this.menu.getCurrentY() + " → " + this.menu.getTargetY())
			: Component.translatable("gui.solarquarry.lcd.y_range", this.menu.getCurrentY(), this.menu.getPos().getY(), this.menu.getPos().getY() + this.menu.getAreaHeight() - 1);
		this.lcdLine(g, lx, ly + 36, "gui.solarquarry.lcd.depth", depth, GuiDraw.LCD_TEXT);
		Component area = mode.usesHeight()
			? Component.literal(this.menu.getAreaWidth() + " × " + this.menu.getAreaLength() + " × " + this.menu.getAreaHeight())
			: Component.literal(this.menu.getAreaWidth() + " × " + this.menu.getAreaLength());
		this.lcdLine(g, lx, ly + 45, "gui.solarquarry.lcd.area", area, GuiDraw.LCD_TEXT);
		this.lcdLine(g, lx, ly + 54, "gui.solarquarry.lcd.speed",
			Component.translatable("gui.solarquarry.lcd.rate", String.format(Locale.ROOT, "%.1f", this.menu.getMeasuredBlocksPerSecond()),
				String.format(Locale.ROOT, "%.1f", this.menu.getBlocksPerMinute() / 60.0)), GuiDraw.LCD_TEXT);
		this.lcdLine(g, lx, ly + 63, "gui.solarquarry.lcd.energy",
			Component.literal(EnergyFormat.format(this.menu.getEnergy()) + " / " + EnergyFormat.format(this.menu.getEnergyCapacity())), 0xFFFFE066);
		int input = this.menu.getAverageInput();
		int use = this.menu.getAverageUse();
		this.lcdLine(g, lx, ly + 72, "gui.solarquarry.lcd.power",
			Component.translatable("gui.solarquarry.lcd.power_flow", input, use), input >= use ? GuiDraw.LCD_TEXT : 0xFFFFB066);
	}

	private void lcdLine(GuiGraphicsExtractor g, int x, int y, String labelKey, Component value, int valueColor) {
		g.text(this.font, Component.translatable(labelKey), x, y, GuiDraw.LCD_DIM, false);
		g.text(this.font, value, x + 42, y, valueColor, false);
	}

	private void drawConfigPage(GuiGraphicsExtractor g) {
		QuarryMode mode = this.menu.getQuarryMode();
		this.configRow(g, 0, "gui.solarquarry.cfg.width", this.menu.getAreaWidth() + " / " + this.menu.getMaxAreaSize());
		this.configRow(g, 1, mode == QuarryMode.TUNNEL_BORE ? "gui.solarquarry.cfg.tunnel_length" : "gui.solarquarry.cfg.length",
			this.menu.getAreaLength() + " / " + this.menu.getMaxLength());
		if (mode.usesTargetDepth()) {
			this.configRow(g, 2, "gui.solarquarry.cfg.target_y", "Y " + this.menu.getTargetY());
		} else {
			this.configRow(g, 2, mode == QuarryMode.TUNNEL_BORE ? "gui.solarquarry.cfg.tunnel_height" : "gui.solarquarry.cfg.clear_height",
				this.menu.getAreaHeight() + " / " + this.menu.getMaxHeight());
		}

		if (mode != QuarryMode.ORE_PRIORITY) {
			g.text(this.font, Component.translatable("gui.solarquarry.cfg.restart_note"), 10, 93, 0xFF3D7A5E, false);
		}

		QuarryBlockEntity.ListKind kind = this.menu.getListKind();
		Component note = switch (kind) {
			case VOID -> Component.translatable(this.menu.hasVoidUpgrade() ? "gui.solarquarry.list.void.on" : "gui.solarquarry.list.void.off");
			case SKIP -> Component.translatable("gui.solarquarry.list.skip.note");
			case ORES -> Component.translatable(this.isListEmpty(kind) ? "gui.solarquarry.list.ores.all" : "gui.solarquarry.list.ores.custom");
		};
		g.centeredText(this.font, note, BAY_X + BAY_W / 2, BAY_Y + 74, kind == QuarryBlockEntity.ListKind.VOID && this.menu.hasVoidUpgrade() ? 0xFFFF7A6A : GuiDraw.LCD_DIM);
	}

	private boolean isListEmpty(QuarryBlockEntity.ListKind kind) {
		int start = QuarryMenu.LIST_START + kind.ordinal() * QuarryBlockEntity.FILTER_SLOTS;
		for (int i = 0; i < QuarryBlockEntity.FILTER_SLOTS; i++) {
			if (!this.menu.slots.get(start + i).getItem().isEmpty()) {
				return false;
			}
		}

		return true;
	}

	private void configRow(GuiGraphicsExtractor g, int row, String labelKey, String value) {
		int ry = 40 + row * 16 + 3;
		g.text(this.font, Component.translatable(labelKey), ROW_LABEL_X, ry, GuiDraw.LCD_DIM, false);
		g.centeredText(this.font, Component.literal(value), (ROW_MINUS_X + 18 + ROW_PLUS_X) / 2, ry, 0xFFFFE066);
	}

	/** Bottom-left line: what the player should do next, or the mined total when all is well. */
	private void drawHint(GuiGraphicsExtractor g, QuarryStatus status) {
		Component hint;
		int color;
		if (status == QuarryStatus.WAITING_FOR_POWER) {
			hint = Component.translatable(status.hintKey(), this.menu.getEnergyPerBlock());
			color = 0xFFFF8A7A;
		} else if (status.isProblem()) {
			hint = Component.translatable(status.hintKey());
			color = 0xFFFFB066;
		} else if (status.isWorking() && !this.menu.isOutputConnected() && this.menu.getUsedBufferSlots() > QuarryBlockEntity.BUFFER_SLOTS * 2 / 3) {
			hint = Component.translatable("gui.solarquarry.hint.no_output");
			color = 0xFFFFB066;
		} else if (status == QuarryStatus.COMPLETE || status == QuarryStatus.IDLE || status == QuarryStatus.PAUSED || status == QuarryStatus.SCANNING) {
			hint = Component.translatable(status.hintKey());
			color = GuiDraw.LCD_DIM;
		} else {
			hint = Component.translatable("gui.solarquarry.mined_total", this.menu.getBlocksMined());
			color = GuiDraw.LCD_DIM;
		}

		g.textWithWordWrap(this.font, hint, QuarryMenu.BUFFER_X, HINT_Y, 160, color, false);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		super.extractTooltip(g, mouseX, mouseY);
		int x = this.leftPos;
		int y = this.topPos;
		boolean config = this.menu.isConfigPage();
		if (!config && mouseX >= x + 8 && mouseX < x + 22 && mouseY >= y + LCD_Y && mouseY < y + LCD_Y + LCD_H) {
			g.setComponentTooltipForNextFrame(this.font, List.of(
				Component.translatable("gui.solarquarry.energy"),
				Component.literal(EnergyFormat.format(this.menu.getEnergy()) + " / " + EnergyFormat.format(this.menu.getEnergyCapacity())).withStyle(ChatFormatting.YELLOW),
				Component.translatable("gui.solarquarry.energy.flow", this.menu.getAverageInput(), this.menu.getAverageUse()).withStyle(ChatFormatting.GRAY)
			), mouseX, mouseY);
			return;
		}

		boolean overBay = mouseX >= x + BAY_X && mouseX < x + BAY_X + BAY_W && mouseY >= y + BAY_Y && mouseY < y + BAY_Y + 16;
		if (!config && overBay && this.hoveredSlot == null) {
			g.setComponentTooltipForNextFrame(this.font, this.upgradeSummary(), mouseX, mouseY);
		}
	}

	/** Effects of the installed upgrades, shown when hovering the upgrade bay header. */
	public List<Component> upgradeSummary() {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("gui.solarquarry.upgrades.summary").withStyle(ChatFormatting.GOLD));
		lines.add(Component.translatable("gui.solarquarry.upgrades.speed", this.menu.getBlocksPerMinute()));
		lines.add(Component.translatable("gui.solarquarry.upgrades.cost", this.menu.getEnergyPerBlock(), this.menu.getTypicalPowerDraw()));
		lines.add(Component.translatable("gui.solarquarry.upgrades.capacity", EnergyFormat.format(this.menu.getEnergyCapacity())));
		lines.add(Component.translatable("gui.solarquarry.upgrades.area", this.menu.getMaxAreaSize(), this.menu.getMaxAreaSize()));
		int fortune = 0;
		for (int i = 0; i < QuarryBlockEntity.UPGRADE_SLOTS; i++) {
			ItemStack stack = this.menu.slots.get(QuarryMenu.UPGRADE_START + i).getItem();
			if (stack.getItem() instanceof com.solarquarry.item.UpgradeItem upgrade && upgrade.type() == com.solarquarry.quarry.UpgradeType.FORTUNE) {
				fortune = Math.min(stack.getCount(), 3);
			}
		}

		if (this.menu.hasSilkTouch()) {
			lines.add(Component.translatable("gui.solarquarry.upgrades.silk"));
		} else if (fortune > 0) {
			lines.add(Component.translatable("gui.solarquarry.upgrades.fortune", fortune));
		}

		if (this.menu.hasVoidUpgrade()) {
			lines.add(Component.translatable("gui.solarquarry.upgrades.void"));
		}

		if (this.menu.slots.get(QuarryMenu.DRONE_SLOT_INDEX).hasItem()) {
			lines.add(Component.translatable("gui.solarquarry.upgrades.miner_drone", com.solarquarry.blockentity.QuarryBlockEntity.MINER_RANGE).withStyle(ChatFormatting.GOLD));
		}

		if (this.menu.hasSecurity()) {
			lines.add(Component.translatable("gui.solarquarry.upgrades.security", this.menu.getDroneCount(),
				com.solarquarry.blockentity.QuarryBlockEntity.MAX_DRONES, com.solarquarry.blockentity.QuarryBlockEntity.SECURITY_RADIUS).withStyle(ChatFormatting.RED));
		}

		if (this.menu.hasAutoSmelter()) {
			lines.add(Component.translatable("gui.solarquarry.upgrades.auto_smelter").withStyle(ChatFormatting.GOLD));
		}

		if (this.menu.hasLiquidRemover()) {
			int tenths = this.menu.getPumpTenths();
			lines.add(Component.translatable("gui.solarquarry.upgrades.liquid", tenths / 10 + "." + tenths % 10,
				com.solarquarry.blockentity.QuarryBlockEntity.PUMP_CAPACITY / net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BUCKET));
			if (this.menu.hasVoidUpgrade()) {
				lines.add(Component.translatable("gui.solarquarry.upgrades.liquid.void").withStyle(ChatFormatting.DARK_PURPLE));
			} else if (!this.menu.isFluidPipeConnected()) {
				lines.add(Component.translatable("gui.solarquarry.upgrades.liquid.no_pipe").withStyle(ChatFormatting.RED));
			}
		}

		if (this.menu.hasChunkLoader()) {
			lines.add(this.menu.isChunkLoadingDisabled() ? Component.translatable("gui.solarquarry.upgrades.chunk_loader.disabled").withStyle(ChatFormatting.RED)
				: this.menu.isChunkLoadingActive() ? Component.translatable("gui.solarquarry.upgrades.chunk_loader.active").withStyle(ChatFormatting.GREEN)
				: Component.translatable("gui.solarquarry.upgrades.chunk_loader.idle"));
		}

		// Installed counts against each type's limit, and slot use.
		java.util.EnumMap<com.solarquarry.quarry.UpgradeType, Integer> counts = new java.util.EnumMap<>(com.solarquarry.quarry.UpgradeType.class);
		int usedSlots = 0;
		for (int i = 0; i < QuarryBlockEntity.UPGRADE_SLOTS; i++) {
			ItemStack stack = this.menu.slots.get(QuarryMenu.UPGRADE_START + i).getItem();
			if (stack.getItem() instanceof com.solarquarry.item.UpgradeItem upgrade) {
				usedSlots++;
				int amount = upgrade.type() == com.solarquarry.quarry.UpgradeType.RANGE ? upgrade.tier() : stack.getCount();
				counts.merge(upgrade.type(), amount, Math::max);
			}
		}

		lines.add(Component.translatable("gui.solarquarry.upgrades.installed", usedSlots, QuarryBlockEntity.UPGRADE_SLOTS).withStyle(ChatFormatting.GOLD));
		for (com.solarquarry.quarry.UpgradeType type : com.solarquarry.quarry.UpgradeType.values()) {
			if (!type.fitsQuarry()) {
				continue;
			}

			int n = counts.getOrDefault(type, 0);
			int max = type == com.solarquarry.quarry.UpgradeType.RANGE ? 2 : type.maxStack();
			lines.add(Component.translatable("gui.solarquarry.upgrades.count", Component.translatable("upgrade.solarquarry." + type.id()), n, max)
				.withStyle(n >= max ? ChatFormatting.GREEN : n > 0 ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
		}

		lines.add(Component.translatable("gui.solarquarry.upgrades.rules").withStyle(ChatFormatting.DARK_GRAY));
		return lines;
	}
}
