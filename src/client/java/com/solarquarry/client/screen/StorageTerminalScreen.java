package com.solarquarry.client.screen;

import com.solarquarry.network.StorageActionPayload;
import com.solarquarry.network.StorageSnapshotPayload;
import com.solarquarry.storage.StorageTerminalMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Storage Terminal, storage view: everything in the chests, barrels and crates on the pipe network as one list.
 * Click an item to take a stack (right-click: half, shift-click: straight into your inventory). Click the list while
 * holding an item, or shift-click items in your inventory, to store them; stored items go where the sorting rules say.
 */
public class StorageTerminalScreen extends AbstractContainerScreen<StorageTerminalMenu> {
	private static final int WIDTH = 176;
	private static final int HEIGHT = 222;
	private static final int GRID_X = 8;
	private static final int GRID_Y = 36;
	private static final int COLS = 9;
	private static final int ROWS = 5;
	private @Nullable StorageSnapshotPayload snapshot;
	private final List<StorageSnapshotPayload.Entry> visible = new ArrayList<>();
	private EditBox search;
	private int scroll;

	public StorageTerminalScreen(StorageTerminalMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		this.titleLabelX = 8;
		this.titleLabelY = 6;
		this.inventoryLabelX = 8;
		this.inventoryLabelY = -1000; // the status line takes its place
	}

	@Override
	protected void init() {
		super.init();
		int x = this.leftPos;
		int y = this.topPos;
		this.search = this.addRenderableWidget(new EditBox(this.font, x + 8, y + 21, 98, 12, Component.translatable("gui.solarquarry.storage.search")));
		this.search.setHint(Component.translatable("gui.solarquarry.storage.search.hint"));
		this.search.setResponder(value -> {
			this.scroll = 0;
			this.filter();
		});
		IndustrialButton depositAll = this.addRenderableWidget(new IndustrialButton(x + 108, y + 20, 28, 14, Component.translatable("gui.solarquarry.storage.store"),
			input -> this.send(StorageActionPayload.DEPOSIT_INVENTORY, ItemStack.EMPTY, 0)).accent(0xFF4CE06A));
		depositAll.setTooltip(Tooltip.create(Component.translatable("gui.solarquarry.storage.store.tooltip")));
		IndustrialButton rules = this.addRenderableWidget(new IndustrialButton(x + 138, y + 20, 32, 14, Component.translatable("gui.solarquarry.storage.rules"),
			input -> this.send(StorageActionPayload.OPEN_RULES, ItemStack.EMPTY, 0)).accent(0xFF47A7E0));
		rules.setTooltip(Tooltip.create(Component.translatable("gui.solarquarry.storage.rules.tooltip")));
		this.filter();
	}

	public void accept(StorageSnapshotPayload payload) {
		if (!payload.terminal().equals(this.menu.getPos())) {
			return;
		}

		this.snapshot = payload;
		this.filter();
	}

	private void filter() {
		this.visible.clear();
		if (this.snapshot == null) {
			return;
		}

		String query = this.search == null ? "" : this.search.getValue().trim().toLowerCase(Locale.ROOT);
		for (StorageSnapshotPayload.Entry entry : this.snapshot.entries()) {
			if (query.isEmpty() || entry.stack().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) {
				this.visible.add(entry);
			}
		}

		int maxScroll = Math.max(0, (this.visible.size() + COLS - 1) / COLS - ROWS);
		this.scroll = Math.clamp(this.scroll, 0, maxScroll);
	}

	private void send(int action, ItemStack item, int amount) {
		ClientPlayNetworking.send(new StorageActionPayload(this.menu.getPos(), action, item, amount));
	}

	private int indexAt(double mouseX, double mouseY) {
		int col = (int) Math.floor((mouseX - this.leftPos - GRID_X) / 18.0);
		int row = (int) Math.floor((mouseY - this.topPos - GRID_Y) / 18.0);
		if (col < 0 || col >= COLS || row < 0 || row >= ROWS) {
			return -2; // outside the grid
		}

		int index = (this.scroll + row) * COLS + col;
		return index < this.visible.size() ? index : -1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int index = this.indexAt(event.x(), event.y());
		if (index == -2) {
			return super.mouseClicked(event, doubleClick);
		}

		boolean right = event.button() == 1;
		ItemStack carried = this.menu.getCarried();
		if (!carried.isEmpty()) {
			this.send(right ? StorageActionPayload.DEPOSIT_ONE_FROM_CURSOR : StorageActionPayload.DEPOSIT_CURSOR, ItemStack.EMPTY, 0);
			return true;
		}

		if (index >= 0) {
			StorageSnapshotPayload.Entry entry = this.visible.get(index);
			int max = entry.stack().getMaxStackSize();
			if (event.hasShiftDown()) {
				this.send(StorageActionPayload.WITHDRAW_TO_INVENTORY, entry.stack(), max);
			} else {
				int amount = right ? (int) Math.max(1, (Math.min(max, entry.amount()) + 1) / 2) : max;
				this.send(StorageActionPayload.WITHDRAW_TO_CURSOR, entry.stack(), amount);
			}
		}

		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int rows = (this.visible.size() + COLS - 1) / COLS;
		this.scroll = Math.clamp(this.scroll - (int) Math.signum(scrollY), 0, Math.max(0, rows - ROWS));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.search != null && this.search.isFocused()) {
			if (event.isEscape()) {
				return super.keyPressed(event);
			}

			return this.search.keyPressed(event) || this.search.canConsumeInput() || super.keyPressed(event);
		}

		return super.keyPressed(event);
	}

	static String shortCount(long amount) {
		if (amount < 1000) {
			return Long.toString(amount);
		}

		if (amount < 10_000) {
			return String.format(Locale.ROOT, "%.1fk", amount / 1000.0);
		}

		if (amount < 1_000_000) {
			return (amount / 1000) + "k";
		}

		return String.format(Locale.ROOT, "%.1fM", amount / 1_000_000.0);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		int x = this.leftPos;
		int y = this.topPos;
		GuiDraw.panel(g, x, y, WIDTH, HEIGHT);
		GuiDraw.hazard(g, x + 4, y + 16, WIDTH - 8, 2);
		GuiDraw.inset(g, x + GRID_X - 2, y + GRID_Y - 2, COLS * 18 + 4, ROWS * 18 + 4, 0xFF15181B);
		for (int r = 0; r < ROWS; r++) {
			for (int c = 0; c < COLS; c++) {
				int sx = x + GRID_X + c * 18;
				int sy = y + GRID_Y + r * 18;
				GuiDraw.slot(g, sx, sy);
				int index = (this.scroll + r) * COLS + c;
				if (index < this.visible.size()) {
					StorageSnapshotPayload.Entry entry = this.visible.get(index);
					g.item(entry.stack(), sx + 1, sy + 1);
					g.itemDecorations(this.font, entry.stack(), sx + 1, sy + 1, shortCount(entry.amount()));
				}
			}
		}

		int rows = (this.visible.size() + COLS - 1) / COLS;
		if (rows > ROWS) {
			int trackH = ROWS * 18;
			int thumbH = Math.max(8, trackH * ROWS / rows);
			int thumbY = (trackH - thumbH) * this.scroll / Math.max(1, rows - ROWS);
			g.fill(x + WIDTH - 5, y + GRID_Y, x + WIDTH - 3, y + GRID_Y + trackH, GuiDraw.STEEL_DARK);
			g.fill(x + WIDTH - 5, y + GRID_Y + thumbY, x + WIDTH - 3, y + GRID_Y + thumbY + thumbH, GuiDraw.AMBER);
		}

		for (var slot : this.menu.slots) {
			GuiDraw.slot(g, x + slot.x, y + slot.y);
		}

		boolean connected = this.snapshot != null && this.snapshot.inventories() > 0;
		GuiDraw.lamp(g, x + WIDTH - 16, y + 6, connected ? 0xFF4CE06A : 0xFFE04B3A, true);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFF1D8A6, true);
		Component status;
		int color;
		if (this.snapshot == null) {
			status = Component.translatable("gui.solarquarry.storage.loading");
			color = GuiDraw.LCD_DIM;
		} else if (this.snapshot.inventories() == 0) {
			status = Component.translatable("gui.solarquarry.storage.none");
			color = 0xFFFF7A6A;
		} else {
			int free = Math.max(0, this.snapshot.totalSlots() - this.snapshot.usedSlots());
			status = Component.translatable("gui.solarquarry.storage.status", this.snapshot.inventories(), free, this.snapshot.totalSlots());
			color = free == 0 ? 0xFFFF7A6A : GuiDraw.LCD_TEXT;
		}

		g.text(this.font, status, 8, GRID_Y + ROWS * 18 + 4, color, false);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		super.extractTooltip(g, mouseX, mouseY);
		int index = this.indexAt(mouseX, mouseY);
		if (index >= 0) {
			StorageSnapshotPayload.Entry entry = this.visible.get(index);
			g.setTooltipForNextFrame(this.font, List.of(entry.stack().getHoverName().getVisualOrderText(),
				Component.translatable("gui.solarquarry.storage.stored", entry.amount()).withColor(0x8CFFC0).getVisualOrderText(),
				Component.translatable("gui.solarquarry.storage.take.hint").withColor(0x9AA3AE).getVisualOrderText()), mouseX, mouseY);
		} else if (index == -1 && this.snapshot != null && this.snapshot.entries().isEmpty()) {
			g.setTooltipForNextFrame(this.font, Component.translatable("gui.solarquarry.storage.help"), mouseX, mouseY);
		}
	}
}
