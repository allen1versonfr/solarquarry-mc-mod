package com.solarquarry.client.screen;

import com.solarquarry.menu.TerminalMenu;
import com.solarquarry.network.TerminalActionPayload;
import com.solarquarry.network.TerminalSnapshotPayload;
import com.solarquarry.quarry.QuarryMode;
import com.solarquarry.quarry.QuarryStatus;
import com.solarquarry.util.EnergyFormat;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/**
 * Remote terminal: a list of the quarries on this terminal's energy network and a detail panel with controls
 * for the selected one. Data comes from server snapshots; buttons send validated requests.
 */
public class TerminalScreen extends AbstractContainerScreen<TerminalMenu> {
	private static final int WIDTH = 320;
	private static final int HEIGHT = 196;
	private static final int LIST_X = 8;
	private static final int LIST_Y = 22;
	private static final int LIST_W = 128;
	private static final int ROW_H = 24;
	private static final int ROWS = 7;
	private static final int DETAIL_X = 142;
	private static final long STALE_MS = 3000;

	private @Nullable TerminalSnapshotPayload snapshot;
	private long receivedAt;
	private int scroll;
	private @Nullable BlockPos selected;
	private final IndustrialButton[] actions = new IndustrialButton[7];

	public TerminalScreen(TerminalMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		this.titleLabelX = 10;
		this.titleLabelY = 6;
	}

	/** Called from the network handler on the client thread. */
	public void accept(TerminalSnapshotPayload payload) {
		if (!payload.terminal().equals(this.menu.getPos())) {
			return;
		}

		this.snapshot = payload;
		this.receivedAt = System.currentTimeMillis();
		if (this.selected == null && !payload.machines().isEmpty()) {
			this.selected = payload.machines().getFirst().pos();
		}

		this.updateButtons();
	}

	@Override
	protected void init() {
		super.init();
		int x = this.leftPos + DETAIL_X;
		int y = this.topPos + HEIGHT - 50;
		String[] keys = {"gui.solarquarry.start", "gui.solarquarry.pause", "gui.solarquarry.resume", "gui.solarquarry.restart",
			"gui.solarquarry.terminal.mode", "gui.solarquarry.terminal.deeper", "gui.solarquarry.terminal.shallower"};
		int[] codes = {TerminalActionPayload.START, TerminalActionPayload.PAUSE, TerminalActionPayload.RESUME, TerminalActionPayload.RESTART,
			TerminalActionPayload.MODE_NEXT, TerminalActionPayload.DEPTH_DOWN, TerminalActionPayload.DEPTH_UP};
		int[] colors = {0xFF4CE06A, 0xFFE0B341, 0xFF4CE06A, 0xFFE04B3A, 0xFF47A7E0, 0xFF47A7E0, 0xFF47A7E0};
		for (int i = 0; i < this.actions.length; i++) {
			int col = i < 4 ? i : i - 4;
			int row = i < 4 ? 0 : 1;
			int w = i < 4 ? 41 : 55;
			int bx = x + (i < 4 ? col * 43 : col * 57);
			final int code = codes[i];
			this.actions[i] = this.addRenderableWidget(new IndustrialButton(bx, y + row * 20, w, 18, Component.translatable(keys[i]),
				input -> this.send(code)).accent(colors[i]));
		}

		this.actions[5].setTooltip(Tooltip.create(Component.translatable("gui.solarquarry.terminal.deeper.tooltip")));
		this.actions[6].setTooltip(Tooltip.create(Component.translatable("gui.solarquarry.terminal.shallower.tooltip")));
		this.actions[4].setTooltip(Tooltip.create(Component.translatable("gui.solarquarry.terminal.mode.tooltip")));
		this.updateButtons();
	}

	private void send(int action) {
		TerminalSnapshotPayload.Entry entry = this.selectedEntry();
		if (entry != null && entry.authorized()) {
			ClientPlayNetworking.send(new TerminalActionPayload(this.menu.getPos(), entry.pos(), action));
		}
	}

	private TerminalSnapshotPayload.@Nullable Entry selectedEntry() {
		if (this.snapshot == null || this.selected == null) {
			return null;
		}

		for (TerminalSnapshotPayload.Entry entry : this.snapshot.machines()) {
			if (entry.pos().equals(this.selected)) {
				return entry;
			}
		}

		return null;
	}

	private boolean stale() {
		return this.snapshot == null || System.currentTimeMillis() - this.receivedAt > STALE_MS;
	}

	private void updateButtons() {
		if (this.actions[0] == null) {
			return;
		}

		TerminalSnapshotPayload.Entry entry = this.selectedEntry();
		boolean usable = entry != null && entry.authorized() && !this.stale();
		for (IndustrialButton button : this.actions) {
			button.visible = entry != null;
			button.active = usable;
		}

		if (usable) {
			QuarryStatus status = QuarryStatus.byOrdinal(entry.status());
			int run = entry.runMode();
			this.actions[0].active = run == 0 || status == QuarryStatus.COMPLETE;
			this.actions[1].active = run == 1 && status != QuarryStatus.COMPLETE;
			this.actions[2].active = run == 2;
		}
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		this.updateButtons();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (this.snapshot != null) {
			int mx = (int) event.x() - this.leftPos - LIST_X;
			int my = (int) event.y() - this.topPos - LIST_Y;
			if (mx >= 0 && mx < LIST_W && my >= 0 && my < ROWS * ROW_H) {
				int index = this.scroll + my / ROW_H;
				List<TerminalSnapshotPayload.Entry> list = this.snapshot.machines();
				if (index < list.size()) {
					this.selected = list.get(index).pos();
					this.updateButtons();
					return true;
				}
			}
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (this.snapshot != null) {
			int max = Math.max(0, this.snapshot.machines().size() - ROWS);
			this.scroll = Math.max(0, Math.min(max, this.scroll - (int) Math.signum(scrollY)));
			return true;
		}

		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		int x = this.leftPos;
		int y = this.topPos;
		GuiDraw.panel(g, x, y, WIDTH, HEIGHT);
		GuiDraw.hazard(g, x + 4, y + 17, WIDTH - 8, 3);
		GuiDraw.lcd(g, x + LIST_X, y + LIST_Y, LIST_W, ROWS * ROW_H);
		GuiDraw.lcd(g, x + DETAIL_X, y + LIST_Y, WIDTH - DETAIL_X - 8, HEIGHT - LIST_Y - 54);
		boolean live = !this.stale();
		GuiDraw.lamp(g, x + WIDTH - 16, y + 6, live ? 0xFF4CE06A : 0xFFE04B3A, true);

		if (this.snapshot == null) {
			return;
		}

		List<TerminalSnapshotPayload.Entry> list = this.snapshot.machines();
		for (int row = 0; row < ROWS && this.scroll + row < list.size(); row++) {
			TerminalSnapshotPayload.Entry entry = list.get(this.scroll + row);
			int ry = y + LIST_Y + row * ROW_H;
			if (entry.pos().equals(this.selected)) {
				g.fill(x + LIST_X + 1, ry + 1, x + LIST_X + LIST_W - 1, ry + ROW_H - 1, 0x3347A7E0);
			}

			QuarryStatus status = QuarryStatus.byOrdinal(entry.status());
			GuiDraw.lamp(g, x + LIST_X + 4, ry + 4, status.color(), status != QuarryStatus.IDLE);
			GuiDraw.hbar(g, x + LIST_X + 4, ry + 15, LIST_W - 10, 5, entry.progress() / 1000.0F, 0xFF4CE06A);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFF1D8A6, true);
		Component link = Component.translatable(this.stale() ? "gui.solarquarry.terminal.stale" : "gui.solarquarry.terminal.live");
		g.text(this.font, link, WIDTH - 20 - this.font.width(link), 6, this.stale() ? 0xFFFF7A6A : 0xFF4CE06A, true);

		if (this.snapshot == null) {
			g.text(this.font, Component.translatable("gui.solarquarry.terminal.connecting"), LIST_X + 4, LIST_Y + 4, GuiDraw.LCD_DIM, false);
			return;
		}

		List<TerminalSnapshotPayload.Entry> list = this.snapshot.machines();
		if (list.isEmpty()) {
			g.textWithWordWrap(this.font, Component.translatable("gui.solarquarry.terminal.empty"), LIST_X + 4, LIST_Y + 4, LIST_W - 8, GuiDraw.LCD_DIM, false);
		}

		for (int row = 0; row < ROWS && this.scroll + row < list.size(); row++) {
			TerminalSnapshotPayload.Entry entry = list.get(this.scroll + row);
			int ry = LIST_Y + row * ROW_H;
			QuarryStatus status = QuarryStatus.byOrdinal(entry.status());
			BlockPos p = entry.pos();
			g.text(this.font, Component.literal(p.getX() + " " + p.getY() + " " + p.getZ()), LIST_X + 14, ry + 3, GuiDraw.LCD_TEXT, false);
			Component statusText = Component.translatable(status.translationKey());
			g.text(this.font, statusText, LIST_X + LIST_W - 4 - this.font.width(statusText), ry + 3, status.color(), false);
		}

		if (list.size() > ROWS) {
			g.text(this.font, Component.literal((this.scroll + 1) + "-" + Math.min(list.size(), this.scroll + ROWS) + "/" + list.size()), LIST_X, LIST_Y + ROWS * ROW_H + 3, GuiDraw.LCD_DIM, false);
		}

		this.drawDetails(g);
	}

	private void drawDetails(GuiGraphicsExtractor g) {
		TerminalSnapshotPayload.Entry e = this.selectedEntry();
		int x = DETAIL_X + 4;
		int y = LIST_Y + 4;
		if (e == null) {
			g.text(this.font, Component.translatable("gui.solarquarry.terminal.select"), x, y, GuiDraw.LCD_DIM, false);
			return;
		}

		QuarryStatus status = QuarryStatus.byOrdinal(e.status());
		QuarryMode mode = QuarryMode.byOrdinal(e.quarryMode());
		this.line(g, x, y, "gui.solarquarry.lcd.status", Component.translatable(status.translationKey()), status.color());
		this.line(g, x, y + 10, "gui.solarquarry.lcd.mode", Component.literal(Component.translatable(mode.translationKey()).getString() + "  " + e.width() + "×" + e.length()), GuiDraw.LCD_TEXT);
		this.line(g, x, y + 20, "gui.solarquarry.lcd.progress", Component.literal(String.format(Locale.ROOT, "%.1f%%  Y %d → %d", e.progress() / 10.0F, e.currentY(), e.targetY())), GuiDraw.LCD_TEXT);
		this.line(g, x, y + 30, "gui.solarquarry.lcd.energy", Component.literal(EnergyFormat.format(e.energy()) + " / " + EnergyFormat.format(e.capacity())), 0xFFFFE066);
		this.line(g, x, y + 40, "gui.solarquarry.lcd.power", Component.translatable("gui.solarquarry.lcd.power_flow", e.powerIn(), e.powerUse()), GuiDraw.LCD_TEXT);
		this.line(g, x, y + 50, "gui.solarquarry.terminal.buffer", Component.literal(e.bufferUsed() + "/27  ·  " + e.blocksMined() + " mined"), e.bufferUsed() >= 27 ? 0xFFFF7A6A : GuiDraw.LCD_TEXT);
		Component storage = e.inventories() == 0
			? Component.translatable(e.outputConnected() ? "gui.solarquarry.terminal.storage_unknown" : "gui.solarquarry.terminal.no_output")
			: Component.translatable("gui.solarquarry.terminal.storage", e.inventories(), e.usedSlots(), e.totalSlots());
		this.line(g, x, y + 60, "gui.solarquarry.terminal.storage_label", storage, e.lowSpace() || e.inventories() == 0 ? 0xFFFFB066 : GuiDraw.LCD_TEXT);

		Component warning = null;
		if (!e.authorized()) {
			warning = Component.translatable("gui.solarquarry.terminal.locked", e.owner());
		} else if (status.isProblem()) {
			warning = Component.translatable(status.hintKey(), "?");
		} else if (e.lowSpace()) {
			warning = Component.translatable("gui.solarquarry.terminal.low_space");
		} else if (!e.outputConnected()) {
			warning = Component.translatable("gui.solarquarry.hint.no_output");
		}

		if (warning != null) {
			g.textWithWordWrap(this.font, warning, x, y + 72, WIDTH - DETAIL_X - 16, e.authorized() ? 0xFFFFB066 : 0xFFFF7A6A, false);
		} else if (!e.owner().isEmpty()) {
			g.text(this.font, Component.translatable("gui.solarquarry.terminal.owner", e.owner()), x, y + 72, GuiDraw.LCD_DIM, false);
		}
	}

	private void line(GuiGraphicsExtractor g, int x, int y, String labelKey, Component value, int color) {
		g.text(this.font, Component.translatable(labelKey), x, y, GuiDraw.LCD_DIM, false);
		g.text(this.font, value, x + 44, y, color, false);
	}
}
