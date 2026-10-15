package com.solarquarry.client.screen;

import com.solarquarry.util.EnergyFormat;
import com.solarquarry.weather.WeatherMachineBlockEntity;
import com.solarquarry.weather.WeatherMachineMenu;
import com.solarquarry.weather.WeatherOperation;
import com.solarquarry.weather.WeatherStage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Weather Control Machine panel: overview (stage, current weather and time, energy flow, warnings), weather controls
 * (clear, rain, thunderstorm; one-time or sustained, duration), time controls (six presets, manual target,
 * immediate or gradual) and stop. Buttons are disabled when the request cannot start and say why in their tooltip;
 * thunderstorms and gradual changes ask for a second click to confirm. The server checks everything again.
 */
public class WeatherMachineScreen extends AbstractContainerScreen<WeatherMachineMenu> {
	private static final int WIDTH = 256;
	private static final int HEIGHT = 214;
	private static final String[] PRESET_KEYS = {"sunrise", "morning", "noon", "sunset", "night", "midnight"};
	private final List<IndustrialButton> weatherButtons = new ArrayList<>();
	private final List<IndustrialButton> timeButtons = new ArrayList<>();
	private IndustrialButton sustain;
	private IndustrialButton duration;
	private IndustrialButton gradual;
	private IndustrialButton setTime;
	private IndustrialButton stop;
	private int armed = -1;
	private long armedAt;

	public WeatherMachineScreen(WeatherMachineMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		this.titleLabelX = 8;
		this.titleLabelY = 6;
	}

	@Override
	protected void init() {
		super.init();
		int x = this.leftPos;
		int y = this.topPos;
		this.weatherButtons.clear();
		this.timeButtons.clear();
		String[] weather = {"clear", "rain", "thunder"};
		for (int i = 0; i < 3; i++) {
			int id = WeatherMachineBlockEntity.BUTTON_CLEAR + i;
			this.weatherButtons.add(this.addRenderableWidget(new IndustrialButton(x + 30 + i * 56, y + 92, 54, 18,
				Component.translatable("gui.solarquarry.weather." + weather[i]), b -> this.press(id, id == WeatherMachineBlockEntity.BUTTON_THUNDER))));
		}

		this.sustain = this.addRenderableWidget(new IndustrialButton(x + 30 + 168, y + 92, 52, 18, Component.empty(), b -> this.press(WeatherMachineBlockEntity.BUTTON_SUSTAIN, false)));
		this.duration = this.addRenderableWidget(new IndustrialButton(x + 30 + 168, y + 112, 52, 18, Component.empty(), b -> this.press(WeatherMachineBlockEntity.BUTTON_DURATION, false)));
		for (int i = 0; i < 6; i++) {
			int id = WeatherMachineBlockEntity.BUTTON_PRESET + i;
			this.timeButtons.add(this.addRenderableWidget(new IndustrialButton(x + 30 + i * 36, y + 136, 34, 18,
				Component.translatable("gui.solarquarry.time." + PRESET_KEYS[i]), b -> this.press(id, this.menu.isGradual()))));
		}

		this.gradual = this.addRenderableWidget(new IndustrialButton(x + 30, y + 158, 74, 18, Component.empty(), b -> this.press(WeatherMachineBlockEntity.BUTTON_GRADUAL, false)));
		this.addRenderableWidget(new IndustrialButton(x + 108, y + 158, 16, 18, Component.literal("-"), b -> this.nudge(-500)));
		this.addRenderableWidget(new IndustrialButton(x + 168, y + 158, 16, 18, Component.literal("+"), b -> this.nudge(500)));
		this.setTime = this.addRenderableWidget(new IndustrialButton(x + 188, y + 158, 60, 18, Component.translatable("gui.solarquarry.time.set"),
			b -> this.press(WeatherMachineBlockEntity.BUTTON_SET_TIME, this.menu.isGradual())));
		this.stop = this.addRenderableWidget(new IndustrialButton(x + 30, y + 186, 60, 18, Component.translatable("gui.solarquarry.weather.stop"),
			b -> this.press(WeatherMachineBlockEntity.BUTTON_STOP, false)).accent(0xFFE04B3A));
	}

	private void nudge(int ticks) {
		int target = Math.floorMod(this.menu.getManualTarget() + ticks, 24_000);
		this.press(WeatherMachineBlockEntity.BUTTON_MANUAL + target / 100, false);
	}

	/** Sends a button press; costly operations need a second click within three seconds. */
	private void press(int id, boolean confirm) {
		if (confirm && (this.armed != id || System.currentTimeMillis() - this.armedAt > 3000)) {
			this.armed = id;
			this.armedAt = System.currentTimeMillis();
			return;
		}

		this.armed = -1;
		if (this.minecraft != null && this.minecraft.gameMode != null) {
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
		}
	}

	private long startCost(WeatherOperation op) {
		if (op == WeatherOperation.CLEAR && this.minecraft != null && this.minecraft.level != null) {
			if (this.minecraft.level.isThundering()) {
				return op.startCost() * 2;
			}

			if (this.minecraft.level.isRaining()) {
				return op.startCost() * 3 / 2;
			}
		}

		return op.startCost();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		boolean standby = this.menu.getStage() == WeatherStage.STANDBY;
		long energy = this.menu.getEnergy();
		WeatherOperation[] ops = {WeatherOperation.CLEAR, WeatherOperation.RAIN, WeatherOperation.THUNDER};
		for (int i = 0; i < 3; i++) {
			IndustrialButton b = this.weatherButtons.get(i);
			b.active = standby && energy >= this.startCost(ops[i]);
			b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(this.costTooltip(ops[i], standby, energy)));
			b.setMessage(Component.translatable(this.armed == WeatherMachineBlockEntity.BUTTON_CLEAR + i ? "gui.solarquarry.weather.confirm"
				: "gui.solarquarry.weather." + new String[] {"clear", "rain", "thunder"}[i]));
		}

		WeatherOperation timeOp = this.menu.isGradual() ? WeatherOperation.TIME_GRADUAL : WeatherOperation.TIME_PRESET;
		for (int i = 0; i < 6; i++) {
			IndustrialButton b = this.timeButtons.get(i);
			b.active = standby && energy >= timeOp.startCost();
			b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(this.costTooltip(timeOp, standby, energy)));
			b.setMessage(Component.translatable(this.armed == WeatherMachineBlockEntity.BUTTON_PRESET + i ? "gui.solarquarry.weather.confirm"
				: "gui.solarquarry.time." + PRESET_KEYS[i]));
		}

		this.setTime.active = standby && energy >= timeOp.startCost();
		this.setTime.setMessage(Component.translatable(this.armed == WeatherMachineBlockEntity.BUTTON_SET_TIME ? "gui.solarquarry.weather.confirm" : "gui.solarquarry.time.set"));
		this.sustain.active = standby;
		this.sustain.setMessage(Component.translatable(this.menu.isSustained() ? "gui.solarquarry.weather.sustained" : "gui.solarquarry.weather.one_time"));
		this.duration.active = this.menu.isSustained();
		this.duration.setMessage(Component.translatable("gui.solarquarry.weather.minutes", this.menu.getDurationMinutes()));
		this.gradual.setMessage(Component.translatable(this.menu.isGradual() ? "gui.solarquarry.time.gradual" : "gui.solarquarry.time.immediate"));
		this.stop.active = !standby && this.menu.getStage() != WeatherStage.SHUTDOWN;
	}

	private Component costTooltip(WeatherOperation op, boolean standby, long energy) {
		long cost = op == WeatherOperation.CLEAR || op == WeatherOperation.RAIN || op == WeatherOperation.THUNDER ? this.startCost(op) : op.startCost();
		var text = Component.translatable("gui.solarquarry.weather.cost", EnergyFormat.format(cost));
		if (op.upkeep() > 0 && (op.isTime() || this.menu.isSustained())) {
			long ticks = op.isTime() ? 250 : this.menu.getDurationMinutes() * 1200L;
			text.append("\n").append(Component.translatable("gui.solarquarry.weather.upkeep", op.upkeep(), EnergyFormat.format(op.totalCost(ticks))));
		}

		if (op == WeatherOperation.THUNDER) {
			text.append("\n").append(Component.translatable("gui.solarquarry.weather.thunder_warning").withStyle(ChatFormatting.GOLD));
		}

		if (!standby) {
			text.append("\n").append(Component.translatable("gui.solarquarry.weather.busy").withStyle(ChatFormatting.RED));
		} else if (energy < cost) {
			text.append("\n").append(Component.translatable("gui.solarquarry.weather.need", EnergyFormat.format(cost - energy)).withStyle(ChatFormatting.RED));
		}

		return text;
	}

	private static String clock(long ticks) {
		int t = (int) Math.floorMod(ticks + 6_000L, 24_000L); // tick 0 is 06:00
		return String.format(Locale.ROOT, "%02d:%02d", t / 1000, t % 1000 * 60 / 1000);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		int x = this.leftPos;
		int y = this.topPos;
		GuiDraw.panel(g, x, y, WIDTH, HEIGHT);
		GuiDraw.hazard(g, x + 4, y + 15, WIDTH - 8, 2);
		GuiDraw.energyBar(g, x + 10, y + 22, 12, 182, this.menu.getEnergy() / (float) Math.max(1, this.menu.getCapacity()));
		GuiDraw.lcd(g, x + 30, y + 22, WIDTH - 38, 66);
		GuiDraw.lcd(g, x + 126, y + 158, 40, 18);
		WeatherStage stage = this.menu.getStage();
		boolean active = stage != WeatherStage.STANDBY;
		GuiDraw.lamp(g, x + WIDTH - 16, y + 6, this.menu.isLowPower() ? 0xFFE04B3A : active ? 0xFF4CE06A : 0xFF8A9199, active);
		if (stage.isStartup()) {
			GuiDraw.hbar(g, x + 96, y + 190, 150, 8, (stage.ordinal() - 1 + this.menu.getStageProgress() / 100.0F) / 4.0F, 0xFFFFD34A);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFF1D8A6, true);
		WeatherStage stage = this.menu.getStage();
		WeatherOperation op = this.menu.getOperation();
		int lx = 34;
		int ly = 26;
		Component stageText = Component.translatable("gui.solarquarry.weather.stage." + stage.name().toLowerCase(Locale.ROOT));
		if (op != WeatherOperation.NONE) {
			stageText = Component.translatable("gui.solarquarry.weather.stage_op", stageText, Component.translatable("gui.solarquarry.weather.op." + op.name().toLowerCase(Locale.ROOT)));
		}

		g.text(this.font, stageText, lx, ly, GuiDraw.LCD_TEXT, false);
		var level = this.minecraft == null ? null : this.minecraft.level;
		Component now = level == null ? Component.empty() : Component.translatable("gui.solarquarry.weather.now",
			Component.translatable(level.isThundering() ? "gui.solarquarry.weather.is_thunder" : level.isRaining() ? "gui.solarquarry.weather.is_rain" : "gui.solarquarry.weather.is_clear"),
			clock(level.getDefaultClockTime()));
		g.text(this.font, now, lx, ly + 11, GuiDraw.LCD_TEXT, false);
		g.text(this.font, Component.translatable("gui.solarquarry.weather.energy", EnergyFormat.format(this.menu.getEnergy()), EnergyFormat.format(this.menu.getCapacity())),
			lx, ly + 22, GuiDraw.LCD_DIM, false);
		g.text(this.font, Component.translatable("gui.solarquarry.weather.flow", this.menu.getInputRate(), this.menu.getConsumption()), lx, ly + 33, GuiDraw.LCD_DIM, false);
		Component line;
		int color = GuiDraw.LCD_DIM;
		WeatherMachineBlockEntity.Error error = this.menu.getError();
		if (this.menu.isLowPower()) {
			line = Component.translatable("gui.solarquarry.weather.low_power");
			color = 0xFFE04B3A;
		} else if (stage == WeatherStage.STABILIZATION) {
			int s = this.menu.getRemainingSeconds();
			line = Component.translatable("gui.solarquarry.weather.holding", s / 60, String.format(Locale.ROOT, "%02d", s % 60));
		} else if (op == WeatherOperation.TIME_GRADUAL && stage == WeatherStage.OPERATION) {
			line = Component.translatable("gui.solarquarry.time.moving", clock(this.menu.getTargetTime()));
		} else if (stage == WeatherStage.STANDBY && error != WeatherMachineBlockEntity.Error.NONE) {
			line = Component.translatable("gui.solarquarry.weather.error." + error.name().toLowerCase(Locale.ROOT));
			color = 0xFFE07A2E;
		} else {
			line = Component.translatable(stage == WeatherStage.STANDBY ? "gui.solarquarry.weather.ready" : "gui.solarquarry.weather.working");
		}

		g.text(this.font, line, lx, ly + 47, color, false);
		g.text(this.font, Component.translatable("gui.solarquarry.weather.section_weather"), 30, 82, GuiDraw.LABEL, false);
		g.text(this.font, Component.translatable("gui.solarquarry.weather.section_time"), 30, 126, GuiDraw.LABEL, false);
		g.text(this.font, clock(this.menu.getManualTarget()), 131, 163, GuiDraw.LCD_TEXT, false);
	}
}
