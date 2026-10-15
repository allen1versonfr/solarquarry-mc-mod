package com.solarquarry.client.screen;

import com.solarquarry.network.SplitterActionPayload;
import com.solarquarry.splitter.FilteredSplitterBlockEntity;
import com.solarquarry.splitter.FilteredSplitterBlockEntity.OutputState;
import com.solarquarry.splitter.FilteredSplitterMenu;
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
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Filtered Splitter screen. The diagram shows the block from above: green = where matching items go, orange = where
 * all other items go, blue = where items come in. The search panel lists items by name; clicking one adds it to the
 * filter.
 */
public class FilteredSplitterScreen extends AbstractContainerScreen<FilteredSplitterMenu> {
	private static final int WIDTH = 176;
	private static final int HEIGHT = 240;
	private static final int MATCH_COLOR = 0xFF4CE06A;
	private static final int OTHER_COLOR = 0xFFE3A23A;
	private static final int INPUT_COLOR = 0xFF47A7E0;
	private static final int DIAGRAM_X = 8;
	private static final int DIAGRAM_Y = 82;
	private static final int RESULT_X = 8;
	private static final int RESULT_Y = 98;
	private static final int RESULT_COLS = 9;
	private static final int RESULT_ROWS = 2;

	private IndustrialButton modeButton;
	private IndustrialButton componentsButton;
	private IndustrialButton matchButton;
	private IndustrialButton otherButton;
	private IndustrialButton inputButton;
	private IndustrialButton searchButton;
	private final EditBox[] tagBoxes = new EditBox[FilteredSplitterBlockEntity.TAG_SLOTS];
	private EditBox searchBox;
	private boolean searching;
	private final List<Item> results = new ArrayList<>();

	public FilteredSplitterScreen(FilteredSplitterMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		this.titleLabelX = 8;
		this.titleLabelY = 6;
		this.inventoryLabelX = FilteredSplitterMenu.INVENTORY_X;
		this.inventoryLabelY = FilteredSplitterMenu.INVENTORY_Y - 10;
	}

	@Override
	protected void init() {
		super.init();
		int x = this.leftPos;
		int y = this.topPos;
		this.modeButton = this.addRenderableWidget(new IndustrialButton(x + 8, y + 64, 50, 14, Component.empty(), input -> this.press(FilteredSplitterMenu.BUTTON_MODE)));
		this.componentsButton = this.addRenderableWidget(new IndustrialButton(x + 60, y + 64, 50, 14, Component.empty(),
			input -> this.press(FilteredSplitterMenu.BUTTON_COMPONENTS)).accent(0xFF47A7E0));
		this.componentsButton.setTooltip(Tooltip.create(Component.translatable("gui.solarquarry.match.tooltip")));
		this.searchButton = this.addRenderableWidget(new IndustrialButton(x + 112, y + 64, 18, 14, Component.literal("?"), input -> this.toggleSearch()));
		this.searchButton.setTooltip(Tooltip.create(Component.translatable("gui.solarquarry.splitter.search.tooltip")));
		IndustrialButton clear = this.addRenderableWidget(new IndustrialButton(x + 132, y + 64, 18, 14, Component.literal("C"), input -> this.press(FilteredSplitterMenu.BUTTON_CLEAR)));
		clear.setTooltip(Tooltip.create(Component.translatable("gui.solarquarry.splitter.clear.tooltip")));
		IndustrialButton reset = this.addRenderableWidget(new IndustrialButton(x + 152, y + 64, 18, 14, Component.literal("R"), input -> this.press(FilteredSplitterMenu.BUTTON_RESET)).accent(0xFFE04B3A));
		reset.setTooltip(Tooltip.create(Component.translatable("gui.solarquarry.splitter.reset.tooltip")));

		this.matchButton = this.addRenderableWidget(new IndustrialButton(x + 64, y + 82, 106, 14, Component.empty(),
			input -> this.press(FilteredSplitterMenu.BUTTON_MATCH_FACE)).accent(MATCH_COLOR));
		this.matchButton.setTooltip(Tooltip.create(Component.translatable("gui.solarquarry.splitter.match_face.tooltip")));
		this.otherButton = this.addRenderableWidget(new IndustrialButton(x + 64, y + 98, 106, 14, Component.empty(),
			input -> this.press(FilteredSplitterMenu.BUTTON_OTHER_FACE)).accent(OTHER_COLOR));
		this.otherButton.setTooltip(Tooltip.create(Component.translatable("gui.solarquarry.splitter.other_face.tooltip")));
		this.inputButton = this.addRenderableWidget(new IndustrialButton(x + 64, y + 114, 106, 14, Component.empty(),
			input -> this.press(FilteredSplitterMenu.BUTTON_INPUT_FACE)).accent(INPUT_COLOR));
		this.inputButton.setTooltip(Tooltip.create(Component.translatable("gui.solarquarry.splitter.input_face.tooltip")));

		FilteredSplitterBlockEntity splitter = this.splitter();
		for (int i = 0; i < this.tagBoxes.length; i++) {
			EditBox box = new EditBox(this.font, x + 8 + i * 82, y + 140, 80, 12, Component.translatable("gui.solarquarry.tag_box"));
			box.setMaxLength(FilteredSplitterBlockEntity.MAX_TAG_LENGTH);
			box.setHint(Component.translatable("gui.solarquarry.tag_hint"));
			box.setValue(splitter != null ? splitter.getTag(i) : "");
			final int index = i;
			box.setResponder(value -> this.onTagChanged(index, value));
			box.setTooltip(Tooltip.create(Component.translatable("gui.solarquarry.tag.tooltip")));
			this.tagBoxes[i] = this.addRenderableWidget(box);
		}

		this.searchBox = this.addRenderableWidget(new EditBox(this.font, x + 8, y + 82, 162, 12, Component.translatable("gui.solarquarry.splitter.search")));
		this.searchBox.setHint(Component.translatable("gui.solarquarry.splitter.search.hint"));
		this.searchBox.setResponder(value -> this.updateResults());
		this.setSearching(false);
		this.updateLabels();
	}

	private FilteredSplitterBlockEntity splitter() {
		if (this.minecraft != null && this.minecraft.level != null && this.minecraft.level.getBlockEntity(this.menu.getPos()) instanceof FilteredSplitterBlockEntity splitter) {
			return splitter;
		}

		return null;
	}

	private void toggleSearch() {
		this.setSearching(!this.searching);
	}

	private void setSearching(boolean searching) {
		this.searching = searching;
		this.matchButton.visible = !searching;
		this.otherButton.visible = !searching;
		this.inputButton.visible = !searching;
		this.searchBox.visible = searching;
		this.searchBox.setFocused(searching);
		this.setFocused(searching ? this.searchBox : null);
		if (searching) {
			this.updateResults();
		}
	}

	private void updateResults() {
		this.results.clear();
		String query = this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);
		if (query.isEmpty()) {
			return;
		}

		for (Item item : BuiltInRegistries.ITEM) {
			if (item == Items.AIR) {
				continue;
			}

			Identifier id = BuiltInRegistries.ITEM.getKey(item);
			String name = new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT);
			if (name.contains(query) || id.getPath().contains(query)) {
				this.results.add(item);
				if (this.results.size() >= RESULT_COLS * RESULT_ROWS) {
					break;
				}
			}
		}
	}

	private void onTagChanged(int index, String value) {
		String trimmed = value.trim();
		if (trimmed.isEmpty() || Identifier.tryParse(trimmed.startsWith("#") ? trimmed.substring(1) : trimmed) != null) {
			ClientPlayNetworking.send(new SplitterActionPayload(this.menu.getPos(), SplitterActionPayload.SET_TAG, index, trimmed));
		}
	}

	private void press(int id) {
		if (this.minecraft != null && this.minecraft.gameMode != null) {
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
		}
	}

	private static Component faceName(Direction face) {
		return Component.translatable("gui.solarquarry.face." + face.getSerializedName());
	}

	private void updateLabels() {
		if (this.modeButton == null) {
			return;
		}

		boolean whitelist = this.menu.isWhitelist();
		this.modeButton.setMessage(Component.translatable(whitelist ? "gui.solarquarry.whitelist" : "gui.solarquarry.blacklist"));
		this.modeButton.accent(whitelist ? 0xFF4CE06A : 0xFFE04B3A);
		this.modeButton.setTooltip(Tooltip.create(Component.translatable(whitelist ? "gui.solarquarry.splitter.help_whitelist" : "gui.solarquarry.splitter.help_blacklist")));
		this.componentsButton.setMessage(Component.translatable(this.menu.isMatchComponents() ? "gui.solarquarry.match_exact" : "gui.solarquarry.match_item"));
		this.matchButton.setMessage(Component.translatable("gui.solarquarry.splitter.match_face", faceName(this.menu.matchFace())));
		this.otherButton.setMessage(Component.translatable("gui.solarquarry.splitter.other_face", faceName(this.menu.otherFace())));
		Direction input = this.menu.inputFace();
		this.inputButton.setMessage(Component.translatable("gui.solarquarry.splitter.input_face",
			input == null ? Component.translatable("gui.solarquarry.face.any") : faceName(input)));
		this.searchButton.accent(this.searching ? 0xFF4CE06A : 0xFF8A93A0);
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		this.updateLabels();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (this.searching) {
			int col = (int) Math.floor((event.x() - this.leftPos - RESULT_X) / 18.0);
			int row = (int) Math.floor((event.y() - this.topPos - RESULT_Y) / 18.0);
			if (col >= 0 && col < RESULT_COLS && row >= 0 && row < RESULT_ROWS) {
				int index = row * RESULT_COLS + col;
				if (index < this.results.size()) {
					Identifier id = BuiltInRegistries.ITEM.getKey(this.results.get(index));
					ClientPlayNetworking.send(new SplitterActionPayload(this.menu.getPos(), SplitterActionPayload.ADD_ITEM, 0, id.toString()));
					return true;
				}
			}
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		List<EditBox> boxes = new ArrayList<>(List.of(this.tagBoxes));
		boxes.add(this.searchBox);
		for (EditBox box : boxes) {
			if (box != null && box.isFocused() && box.visible) {
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
		GuiDraw.hazard(g, x + 4, y + 17, WIDTH - 8, 3);
		boolean whitelist = this.menu.isWhitelist();
		GuiDraw.inset(g, x + FilteredSplitterMenu.FILTER_X - 2, y + FilteredSplitterMenu.FILTER_Y - 2, 166, 40, whitelist ? 0xFF13261A : 0xFF2A1416);
		for (int i = 0; i < FilteredSplitterBlockEntity.FILTER_SLOTS; i++) {
			GuiDraw.slot(g, x + FilteredSplitterMenu.FILTER_X + (i % 9) * 18, y + FilteredSplitterMenu.FILTER_Y + (i / 9) * 18);
		}

		for (int i = FilteredSplitterBlockEntity.FILTER_SLOTS; i < this.menu.slots.size(); i++) {
			var slot = this.menu.slots.get(i);
			GuiDraw.slot(g, x + slot.x, y + slot.y);
		}

		if (this.searching) {
			GuiDraw.inset(g, x + RESULT_X - 2, y + RESULT_Y - 2, 166, 40, 0xFF1A1D20);
			for (int i = 0; i < RESULT_COLS * RESULT_ROWS; i++) {
				int sx = x + RESULT_X + (i % RESULT_COLS) * 18;
				int sy = y + RESULT_Y + (i / RESULT_COLS) * 18;
				GuiDraw.slot(g, sx, sy);
				if (i < this.results.size()) {
					g.item(new ItemStack(this.results.get(i)), sx + 1, sy + 1);
				}
			}
		} else {
			this.drawDiagram(g, x + DIAGRAM_X, y + DIAGRAM_Y);
		}

		GuiDraw.hazard(g, x + 4, y + 135, WIDTH - 8, 2);
		OutputState worst = this.worstState();
		int lamp = switch (worst) {
			case OK, IDLE -> 0xFF4CE06A;
			case FULL -> 0xFFE3A23A;
			case DISCONNECTED -> 0xFFE04B3A;
		};
		GuiDraw.lamp(g, x + WIDTH - 16, y + 6, lamp, worst != OutputState.IDLE);
	}

	private OutputState worstState() {
		OutputState a = this.menu.matchState();
		OutputState b = this.menu.otherState();
		return a.ordinal() >= b.ordinal() ? a : b;
	}

	/**
	 * A plus-shaped map of the block seen from above (north at the top): the four sides around the centre, with up and
	 * down as two small cells in the corners. Each side is coloured by its role.
	 */
	private void drawDiagram(GuiGraphicsExtractor g, int x, int y) {
		GuiDraw.inset(g, x, y, 52, 50, 0xFF15181B);
		int cx = x + 20;
		int cy = y + 19;
		g.fill(cx, cy, cx + 12, cy + 12, GuiDraw.STEEL_LIGHT);
		this.cell(g, Direction.NORTH, cx, cy - 13);
		this.cell(g, Direction.SOUTH, cx, cy + 13);
		this.cell(g, Direction.WEST, cx - 13, cy);
		this.cell(g, Direction.EAST, cx + 13, cy);
		this.cell(g, Direction.UP, x + 3, y + 3);
		this.cell(g, Direction.DOWN, x + 3, y + 35);
	}

	private void cell(GuiGraphicsExtractor g, Direction face, int x, int y) {
		int color = this.roleColor(face);
		g.fill(x, y, x + 12, y + 12, color);
		String letter = face.getSerializedName().substring(0, 1).toUpperCase(Locale.ROOT);
		g.centeredText(this.font, Component.literal(letter), x + 6, y + 2, color == 0xFF2A2E33 ? GuiDraw.LCD_DIM : 0xFF101010);
	}

	private int roleColor(Direction face) {
		if (face == this.menu.matchFace()) {
			return MATCH_COLOR;
		}

		if (face == this.menu.otherFace()) {
			return OTHER_COLOR;
		}

		Direction input = this.menu.inputFace();
		return input == null || input == face ? INPUT_COLOR : 0xFF2A2E33;
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFF1D8A6, true);
		g.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, GuiDraw.LABEL, false);
		if (!this.searching) {
			Component status = this.statusLine();
			g.text(this.font, status, 64, 130 - 0, status.getStyle().getColor() != null ? status.getStyle().getColor().getValue() | 0xFF000000 : GuiDraw.LCD_TEXT, false);
		}

		for (int i = 0; i < this.tagBoxes.length; i++) {
			String value = this.tagBoxes[i].getValue().trim();
			boolean bad = !value.isEmpty() && Identifier.tryParse(value.startsWith("#") ? value.substring(1) : value) == null;
			this.tagBoxes[i].setTextColor(bad ? 0xFFFF7A6A : 0xFFE0E0E0);
		}
	}

	private Component statusLine() {
		OutputState match = this.menu.matchState();
		OutputState other = this.menu.otherState();
		if (match == OutputState.DISCONNECTED) {
			return Component.translatable("gui.solarquarry.splitter.status.match_disconnected").withColor(0xFF7A6A);
		}

		if (other == OutputState.DISCONNECTED) {
			return Component.translatable("gui.solarquarry.splitter.status.other_disconnected").withColor(0xFF7A6A);
		}

		if (match == OutputState.FULL) {
			return Component.translatable("gui.solarquarry.splitter.status.match_full").withColor(0xFFB066);
		}

		if (other == OutputState.FULL) {
			return Component.translatable("gui.solarquarry.splitter.status.other_full").withColor(0xFFB066);
		}

		return Component.translatable("gui.solarquarry.splitter.status.ok", this.menu.matchedCount(), this.menu.otherCount()).withColor(0x8CFFC0);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		super.extractTooltip(g, mouseX, mouseY);
		int rx = mouseX - this.leftPos;
		int ry = mouseY - this.topPos;
		if (this.searching) {
			int col = Math.floorDiv(rx - RESULT_X, 18);
			int row = Math.floorDiv(ry - RESULT_Y, 18);
			if (col >= 0 && col < RESULT_COLS && row >= 0 && row < RESULT_ROWS) {
				int index = row * RESULT_COLS + col;
				if (index < this.results.size()) {
					g.setTooltipForNextFrame(this.font, Component.translatable("gui.solarquarry.splitter.search.add", new ItemStack(this.results.get(index)).getHoverName()), mouseX, mouseY);
				}
			}
		} else if (rx >= DIAGRAM_X && rx < DIAGRAM_X + 52 && ry >= DIAGRAM_Y && ry < DIAGRAM_Y + 50) {
			g.setTooltipForNextFrame(this.font, Component.translatable("gui.solarquarry.splitter.diagram.tooltip"), mouseX, mouseY);
		}
	}
}
