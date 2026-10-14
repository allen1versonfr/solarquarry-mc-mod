package com.solarquarry.client.screen;

import com.solarquarry.menu.SortingTerminalMenu;
import com.solarquarry.network.SortingActionPayload;
import com.solarquarry.network.SortingSnapshotPayload;
import com.solarquarry.pipe.SortRule;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Sorting Terminal: the left list shows every inventory on the pipe network (registered ones marked), the right
 * side edits the selected destination: register/unregister, mode (items only / everything except / fallback),
 * priority, item tags, and the 3 x 3 item templates below the list.
 */
public class SortingTerminalScreen extends AbstractContainerScreen<SortingTerminalMenu> {
	private static final int WIDTH = 256;
	private static final int HEIGHT = 240;
	private static final int LIST_X = 8;
	private static final int LIST_Y = 18;
	private static final int LIST_W = 118;
	private static final int ROW_H = 18;
	private static final int ROWS = 5;
	private static final int PANEL_X = 132;
	private static final int TAG_Y = 96;
	/** Two short notes under the list; hover them for the full explanation. */
	private static final int NOTE_Y = 116;
	private @Nullable SortingSnapshotPayload snapshot;
	private int scroll;
	private IndustrialButton registerButton;
	private IndustrialButton modeButton;
	private IndustrialButton priorityDown;
	private IndustrialButton priorityUp;
	private final EditBox[] tagBoxes = new EditBox[SortRule.MAX_TAGS];
	private boolean updatingTags;

	public SortingTerminalScreen(SortingTerminalMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		this.titleLabelX = 8;
		this.titleLabelY = 6;
		this.inventoryLabelX = 48;
		this.inventoryLabelY = SortingTerminalMenu.INVENTORY_Y - 11;
	}

	@Override
	protected void init() {
		super.init();
		int x = this.leftPos;
		int y = this.topPos;
		this.registerButton = this.addRenderableWidget(new IndustrialButton(x + PANEL_X, y + 47, 116, 14, Component.translatable("gui.solarquarry.sorting.register"),
			input -> this.send(this.isRegistered() ? SortingActionPayload.UNREGISTER : SortingActionPayload.REGISTER, 0, "")));
		this.modeButton = this.addRenderableWidget(new IndustrialButton(x + PANEL_X, y + 63, 116, 14, Component.empty(),
			input -> this.send(SortingActionPayload.NEXT_MODE, 0, "")));
		this.priorityDown = this.addRenderableWidget(new IndustrialButton(x + PANEL_X, y + 79, 14, 14, Component.literal("-"),
			input -> this.send(SortingActionPayload.PRIORITY, -1, "")));
		this.priorityUp = this.addRenderableWidget(new IndustrialButton(x + PANEL_X + 102, y + 79, 14, 14, Component.literal("+"),
			input -> this.send(SortingActionPayload.PRIORITY, 1, "")));
		Tooltip priorityTip = Tooltip.create(Component.translatable("gui.solarquarry.sorting.priority.tooltip"));
		this.priorityDown.setTooltip(priorityTip);
		this.priorityUp.setTooltip(priorityTip);
		for (int i = 0; i < this.tagBoxes.length; i++) {
			EditBox box = new EditBox(this.font, x + PANEL_X, y + TAG_Y + i * 13, 116, 12, Component.translatable("gui.solarquarry.tag_box"));
			box.setMaxLength(96);
			box.setHint(Component.translatable("gui.solarquarry.tag_hint"));
			final int index = i;
			box.setResponder(value -> this.onTagChanged(index, value));
			box.setTooltip(Tooltip.create(Component.translatable("gui.solarquarry.tag.tooltip")));
			this.tagBoxes[i] = this.addRenderableWidget(box);
		}

		this.updateWidgets();
	}

	public void accept(SortingSnapshotPayload payload) {
		if (!payload.terminal().equals(this.menu.getPos())) {
			return;
		}

		boolean selectionChanged = this.snapshot == null || this.snapshot.selected() != payload.selected();
		this.snapshot = payload;
		if (payload.selected() == Long.MIN_VALUE && !payload.entries().isEmpty()) {
			this.select(payload.entries().getFirst().pos());
		}

		if (selectionChanged) {
			this.updatingTags = true;
			for (int i = 0; i < this.tagBoxes.length; i++) {
				if (this.tagBoxes[i] != null) {
					this.tagBoxes[i].setValue(payload.selectedTags().get(i));
				}
			}

			this.updatingTags = false;
		}

		this.updateWidgets();
	}

	public @Nullable SortingSnapshotPayload getSnapshot() {
		return this.snapshot;
	}

	private SortingSnapshotPayload.@Nullable Entry selectedEntry() {
		if (this.snapshot == null || this.snapshot.selected() == Long.MIN_VALUE) {
			return null;
		}

		for (SortingSnapshotPayload.Entry entry : this.snapshot.entries()) {
			if (entry.pos().asLong() == this.snapshot.selected()) {
				return entry;
			}
		}

		return null;
	}

	private boolean isRegistered() {
		SortingSnapshotPayload.Entry entry = this.selectedEntry();
		return entry != null && entry.registered();
	}

	private void select(BlockPos pos) {
		ClientPlayNetworking.send(new SortingActionPayload(this.menu.getPos(), SortingActionPayload.SELECT, pos, 0, ""));
	}

	private void send(int action, int value, String text) {
		SortingSnapshotPayload.Entry entry = this.selectedEntry();
		if (entry != null) {
			ClientPlayNetworking.send(new SortingActionPayload(this.menu.getPos(), action, entry.pos(), value, text));
		}
	}

	private void onTagChanged(int index, String value) {
		if (this.updatingTags) {
			return;
		}

		String trimmed = value.trim();
		if (trimmed.isEmpty() || Identifier.tryParse(trimmed.startsWith("#") ? trimmed.substring(1) : trimmed) != null) {
			this.send(SortingActionPayload.TAG, index, trimmed);
		}
	}

	private void updateWidgets() {
		if (this.registerButton == null) {
			return;
		}

		SortingSnapshotPayload.Entry entry = this.selectedEntry();
		boolean registered = entry != null && entry.registered();
		SortRule.Mode mode = registered ? SortRule.Mode.byOrdinal(entry.mode()) : SortRule.Mode.ALLOW;
		this.registerButton.active = entry != null;
		this.registerButton.setMessage(Component.translatable(registered ? "gui.solarquarry.sorting.unregister" : "gui.solarquarry.sorting.register"));
		this.registerButton.accent(registered ? 0xFFE04B3A : 0xFF4CE06A);
		this.modeButton.active = registered;
		this.modeButton.visible = registered;
		this.modeButton.setMessage(Component.translatable("gui.solarquarry.sorting.mode." + mode.name().toLowerCase(java.util.Locale.ROOT)));
		this.modeButton.setTooltip(Tooltip.create(Component.translatable("gui.solarquarry.sorting.mode." + mode.name().toLowerCase(java.util.Locale.ROOT) + ".tooltip")));
		this.priorityDown.visible = registered;
		this.priorityUp.visible = registered;
		this.priorityDown.active = registered && entry.priority() > SortRule.MIN_PRIORITY;
		this.priorityUp.active = registered && entry.priority() < SortRule.MAX_PRIORITY;
		boolean tags = registered && mode != SortRule.Mode.FALLBACK;
		for (EditBox box : this.tagBoxes) {
			box.visible = tags;
			box.active = tags;
		}

		this.menu.setTemplatesActive(tags);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (this.snapshot != null) {
			int mx = (int) event.x() - this.leftPos - LIST_X;
			int my = (int) event.y() - this.topPos - LIST_Y;
			if (mx >= 0 && mx < LIST_W && my >= 0 && my < ROWS * ROW_H) {
				int index = this.scroll + my / ROW_H;
				List<SortingSnapshotPayload.Entry> list = this.snapshot.entries();
				if (index < list.size()) {
					this.select(list.get(index).pos());
					return true;
				}
			}
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (this.snapshot != null) {
			int max = Math.max(0, this.snapshot.entries().size() - ROWS);
			this.scroll = Math.max(0, Math.min(max, this.scroll - (int) Math.signum(scrollY)));
			return true;
		}

		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		for (EditBox box : this.tagBoxes) {
			if (box != null && box.isFocused()) {
				if (event.isEscape()) {
					return super.keyPressed(event);
				}

				return box.keyPressed(event) || box.canConsumeInput() || super.keyPressed(event);
			}
		}

		return super.keyPressed(event);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		int x = this.leftPos;
		int y = this.topPos;
		GuiDraw.panel(g, x, y, WIDTH, HEIGHT);
		GuiDraw.hazard(g, x + 4, y + 15, WIDTH - 8, 2);
		GuiDraw.lcd(g, x + LIST_X - 2, y + LIST_Y - 2, LIST_W + 4, ROWS * ROW_H + 4);
		if (this.menu.slots.getFirst().isActive()) {
			GuiDraw.inset(g, x + SortingTerminalMenu.GHOST_X - 4, y + SortingTerminalMenu.GHOST_Y - 4, 62, 62, 0xFF13261A);
			for (int i = 0; i < SortRule.MAX_ITEMS; i++) {
				GuiDraw.slot(g, x + SortingTerminalMenu.GHOST_X + (i % 3) * 18, y + SortingTerminalMenu.GHOST_Y + (i / 3) * 18);
			}
		}

		for (int i = SortingTerminalMenu.PLAYER_START; i < this.menu.slots.size(); i++) {
			var slot = this.menu.slots.get(i);
			GuiDraw.slot(g, x + slot.x, y + slot.y);
		}

		if (this.snapshot == null) {
			return;
		}

		List<SortingSnapshotPayload.Entry> list = this.snapshot.entries();
		for (int row = 0; row < ROWS && this.scroll + row < list.size(); row++) {
			SortingSnapshotPayload.Entry entry = list.get(this.scroll + row);
			int ry = y + LIST_Y + row * ROW_H;
			if (entry.pos().asLong() == this.snapshot.selected()) {
				g.fill(x + LIST_X, ry, x + LIST_X + LIST_W, ry + ROW_H, 0xFF1F3A2C);
			}

			g.item(iconOf(entry), x + LIST_X + 1, ry + 1);
			int color = !entry.present() ? 0xFFFF7A6A : entry.registered() ? GuiDraw.LCD_TEXT : GuiDraw.LCD_DIM;
			g.text(this.font, Component.literal(entry.pos().getX() + " " + entry.pos().getY() + " " + entry.pos().getZ()), x + LIST_X + 20, ry + 1, color, false);
			Component tag = entry.registered() ? Component.translatable("gui.solarquarry.sorting.short." + SortRule.Mode.byOrdinal(entry.mode()).name().toLowerCase(java.util.Locale.ROOT))
				: Component.translatable("gui.solarquarry.sorting.short.unused");
			g.text(this.font, tag, x + LIST_X + 20, ry + 9, entry.registered() ? 0xFFFFE066 : GuiDraw.LCD_DIM, false);
			GuiDraw.lamp(g, x + LIST_X + LIST_W - 9, ry + 5, entry.registered() ? 0xFF4CE06A : 0xFF555A60, entry.registered());
		}
	}

	private static ItemStack iconOf(SortingSnapshotPayload.Entry entry) {
		Identifier id = Identifier.tryParse(entry.icon());
		return id == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFF1D8A6, true);
		if (this.snapshot == null) {
			g.text(this.font, Component.translatable("gui.solarquarry.sorting.loading"), LIST_X + 2, LIST_Y + 2, GuiDraw.LCD_DIM, false);
			return;
		}

		if (this.snapshot.entries().isEmpty()) {
			g.textWithWordWrap(this.font, Component.translatable("gui.solarquarry.sorting.empty"), LIST_X + 2, LIST_Y + 2, LIST_W - 4, GuiDraw.LCD_DIM);
		}

		SortingSnapshotPayload.Entry entry = this.selectedEntry();
		if (entry != null) {
			ItemStack icon = iconOf(entry);
			g.text(this.font, icon.isEmpty() ? Component.translatable("gui.solarquarry.sorting.missing") : icon.getHoverName(), PANEL_X, 19, GuiDraw.LABEL, false);
			g.text(this.font, Component.literal(entry.pos().getX() + ", " + entry.pos().getY() + ", " + entry.pos().getZ()), PANEL_X, 28, GuiDraw.LCD_DIM, false);
			Component status = !entry.loaded() ? Component.translatable("gui.solarquarry.sorting.unloaded").withStyle(ChatFormatting.GOLD)
				: !entry.present() ? Component.translatable("gui.solarquarry.sorting.missing").withStyle(ChatFormatting.RED)
				: Component.translatable("gui.solarquarry.sorting.slots", entry.usedSlots(), entry.totalSlots());
			g.text(this.font, status, PANEL_X, 37, GuiDraw.LCD_TEXT, false);
			if (entry.registered()) {
				int p = entry.priority();
				g.centeredText(this.font, Component.translatable("gui.solarquarry.sorting.priority", (p > 0 ? "+" : "") + p), PANEL_X + 58, 82, p == 0 ? GuiDraw.LABEL : 0xFFFFE066);
				SortRule.Mode mode = SortRule.Mode.byOrdinal(entry.mode());
				if (mode != SortRule.Mode.FALLBACK && !entry.hasEntries()) {
					boolean allow = mode == SortRule.Mode.ALLOW;
					g.text(this.font, Component.translatable(allow ? "gui.solarquarry.sorting.warn_empty_allow.short" : "gui.solarquarry.sorting.note_block_all.short"),
						LIST_X, NOTE_Y, allow ? 0xFFFFB066 : GuiDraw.LCD_DIM, false);
				}
			}
		}

		if (!this.snapshot.hasFallback()) {
			g.text(this.font, Component.translatable("gui.solarquarry.sorting.no_fallback.short"), LIST_X, NOTE_Y + 11, GuiDraw.LCD_DIM, false);
		}
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		super.extractTooltip(g, mouseX, mouseY);
		if (this.snapshot == null) {
			return;
		}

		int rx = mouseX - this.leftPos;
		int ry = mouseY - this.topPos;
		if (rx < LIST_X || rx >= LIST_X + LIST_W) {
			return;
		}

		SortingSnapshotPayload.Entry entry = this.selectedEntry();
		if (ry >= NOTE_Y - 1 && ry < NOTE_Y + 9 && entry != null && entry.registered() && !entry.hasEntries()) {
			SortRule.Mode mode = SortRule.Mode.byOrdinal(entry.mode());
			if (mode != SortRule.Mode.FALLBACK) {
				g.setTooltipForNextFrame(this.font, Component.translatable(mode == SortRule.Mode.ALLOW ? "gui.solarquarry.sorting.warn_empty_allow" : "gui.solarquarry.sorting.note_block_all"), mouseX, mouseY);
			}
		} else if (ry >= NOTE_Y + 10 && ry < NOTE_Y + 20 && !this.snapshot.hasFallback()) {
			g.setTooltipForNextFrame(this.font, Component.translatable("gui.solarquarry.sorting.no_fallback"), mouseX, mouseY);
		}
	}
}
