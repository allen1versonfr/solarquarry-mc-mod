package com.solarquarry.client.screen;

import com.solarquarry.crafting.BenchRecipes;
import com.solarquarry.crafting.FabricationBenchMenu;
import com.solarquarry.network.BenchCraftPayload;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Fabrication Bench: a searchable catalogue of every Solar Quarry recipe on the left, the selected recipe's ingredients
 * (have / need) on the right, and craft buttons that take the ingredients straight from the inventory.
 */
public class FabricationBenchScreen extends AbstractContainerScreen<FabricationBenchMenu> {
	/** Catalogue received from the server when the bench was opened. */
	public static List<BenchRecipes.Entry> catalogue = List.of();

	private static final int WIDTH = 256;
	private static final int HEIGHT = 222;
	private static final int GRID_X = 8;
	private static final int GRID_Y = 34;
	private static final int COLS = 6;
	private static final int ROWS = 5;
	private static final int PANEL_X = 128;
	private static final int NEED_Y = 44;
	private static final int NEED_STEP = 20;
	private EditBox search;
	private IndustrialButton craftOne;
	private IndustrialButton craftMany;
	private List<BenchRecipes.Entry> visible = List.of();
	private int scroll;
	private @Nullable String selected;

	public FabricationBenchScreen(FabricationBenchMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		this.titleLabelX = 8;
		this.titleLabelY = 6;
		this.inventoryLabelX = FabricationBenchMenu.INVENTORY_X;
		this.inventoryLabelY = FabricationBenchMenu.INVENTORY_Y - 11;
	}

	@Override
	protected void init() {
		super.init();
		int x = this.leftPos;
		int y = this.topPos;
		this.search = new EditBox(this.font, x + GRID_X, y + 19, COLS * 18, 12, Component.translatable("gui.solarquarry.bench.search"));
		this.search.setHint(Component.translatable("gui.solarquarry.bench.search"));
		this.search.setResponder(value -> this.filter());
		this.addRenderableWidget(this.search);
		this.craftOne = this.addRenderableWidget(new IndustrialButton(x + PANEL_X, y + 106, 58, 16, Component.translatable("gui.solarquarry.bench.craft"),
			input -> this.craft(1)).accent(0xFF4CE06A));
		this.craftMany = this.addRenderableWidget(new IndustrialButton(x + PANEL_X + 62, y + 106, 58, 16, Component.translatable("gui.solarquarry.bench.craft_max"),
			input -> this.craft(BenchRecipes.MAX_BATCH)));
		this.craftMany.setTooltip(Tooltip.create(Component.translatable("gui.solarquarry.bench.craft_max.tooltip")));
		this.filter();
	}

	private void filter() {
		String query = this.search == null ? "" : this.search.getValue().trim().toLowerCase(Locale.ROOT);
		List<BenchRecipes.Entry> list = new ArrayList<>();
		for (BenchRecipes.Entry entry : catalogue) {
			if (query.isEmpty() || entry.result().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) {
				list.add(entry);
			}
		}

		this.visible = list;
		this.scroll = 0;
		if (this.selected == null && !list.isEmpty()) {
			this.selected = list.getFirst().id();
		}
	}

	private BenchRecipes.@Nullable Entry selectedEntry() {
		for (BenchRecipes.Entry entry : catalogue) {
			if (entry.id().equals(this.selected)) {
				return entry;
			}
		}

		return null;
	}

	private void craft(int times) {
		if (this.selected != null) {
			ClientPlayNetworking.send(new BenchCraftPayload(this.selected, times));
		}
	}

	private static List<Item> items(BenchRecipes.Need need) {
		List<Item> items = new ArrayList<>();
		for (String id : need.options()) {
			Identifier identifier = Identifier.tryParse(id);
			if (identifier != null) {
				BuiltInRegistries.ITEM.getOptional(identifier).ifPresent(items::add);
			}
		}

		return items;
	}

	/** How many matching items the player carries for one requirement. */
	private int have(BenchRecipes.Need need) {
		List<Item> items = items(need);
		int total = 0;
		Inventory inventory = this.minecraft.player.getInventory();
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty() && items.contains(stack.getItem())) {
				total += stack.getCount();
			}
		}

		return total;
	}

	private boolean canCraft(BenchRecipes.Entry entry) {
		for (BenchRecipes.Need need : entry.needs()) {
			if (this.have(need) < need.count()) {
				return false;
			}
		}

		return true;
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		BenchRecipes.Entry entry = this.selectedEntry();
		boolean ok = entry != null && this.canCraft(entry);
		this.craftOne.active = ok;
		this.craftMany.active = ok;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int col = (int) Math.floor((event.x() - this.leftPos - GRID_X) / 18.0);
		int row = (int) Math.floor((event.y() - this.topPos - GRID_Y) / 18.0);
		if (col >= 0 && col < COLS && row >= 0 && row < ROWS) {
			int index = this.scroll * COLS + row * COLS + col;
			if (index < this.visible.size()) {
				this.selected = this.visible.get(index).id();
				return true;
			}
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int rows = (this.visible.size() + COLS - 1) / COLS;
		int max = Math.max(0, rows - ROWS);
		this.scroll = Math.clamp(this.scroll - (int) Math.signum(scrollY), 0, max);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.search.isFocused() && !event.isEscape()) {
			return this.search.keyPressed(event) || this.search.canConsumeInput() || super.keyPressed(event);
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
		GuiDraw.lcd(g, x + GRID_X - 1, y + GRID_Y - 1, COLS * 18 + 2, ROWS * 18 + 2);
		for (int row = 0; row < ROWS; row++) {
			for (int col = 0; col < COLS; col++) {
				int index = this.scroll * COLS + row * COLS + col;
				if (index >= this.visible.size()) {
					continue;
				}

				BenchRecipes.Entry entry = this.visible.get(index);
				int sx = x + GRID_X + col * 18;
				int sy = y + GRID_Y + row * 18;
				if (entry.id().equals(this.selected)) {
					g.fill(sx, sy, sx + 18, sy + 18, 0xFF2F5A3C);
				} else if (this.canCraft(entry)) {
					g.fill(sx, sy, sx + 18, sy + 18, 0xFF15301F);
				}

				g.item(entry.result(), sx + 1, sy + 1);
			}
		}

		GuiDraw.inset(g, x + PANEL_X - 4, y + 18, WIDTH - PANEL_X, 108, 0xFF1A1D20);
		BenchRecipes.Entry entry = this.selectedEntry();
		if (entry != null) {
			GuiDraw.slot(g, x + PANEL_X, y + 22);
			g.item(entry.result(), x + PANEL_X + 1, y + 23);
			for (int i = 0; i < entry.needs().size(); i++) {
				int nx = x + PANEL_X + (i % 2) * 62;
				int ny = y + NEED_Y + (i / 2) * NEED_STEP;
				GuiDraw.slot(g, nx, ny);
				List<Item> options = items(entry.needs().get(i));
				if (!options.isEmpty()) {
					// Tag ingredients cycle through their items once a second.
					Item shown = options.get((int) (System.currentTimeMillis() / 1000L % options.size()));
					g.item(new ItemStack(shown), nx + 1, ny + 1);
				}
			}
		}

		for (int i = 0; i < this.menu.slots.size(); i++) {
			var slot = this.menu.slots.get(i);
			GuiDraw.slot(g, x + slot.x, y + slot.y);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFF1D8A6, true);
		g.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, GuiDraw.LABEL, false);
		if (catalogue.isEmpty()) {
			g.textWithWordWrap(this.font, Component.translatable("gui.solarquarry.bench.loading"), GRID_X + 2, GRID_Y + 2, COLS * 18 - 4, GuiDraw.LCD_DIM);
		}

		BenchRecipes.Entry entry = this.selectedEntry();
		if (entry == null) {
			return;
		}

		Component name = entry.result().getHoverName();
		String text = this.font.plainSubstrByWidth(name.getString(), WIDTH - PANEL_X - 30);
		g.text(this.font, Component.literal(text), PANEL_X + 22, 23, GuiDraw.LABEL, false);
		if (entry.result().getCount() > 1) {
			g.text(this.font, Component.translatable("gui.solarquarry.bench.makes", entry.result().getCount()), PANEL_X + 22, 32, GuiDraw.LCD_DIM, false);
		}

		for (int i = 0; i < entry.needs().size(); i++) {
			BenchRecipes.Need need = entry.needs().get(i);
			int have = this.have(need);
			int nx = PANEL_X + (i % 2) * 62 + 20;
			int ny = NEED_Y + (i / 2) * NEED_STEP + 5;
			g.text(this.font, Component.literal(Math.min(have, 999) + "/" + need.count()), nx, ny, have >= need.count() ? GuiDraw.LCD_TEXT : 0xFFFF7A6A, false);
		}
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		super.extractTooltip(g, mouseX, mouseY);
		int rx = mouseX - this.leftPos;
		int ry = mouseY - this.topPos;
		int col = Math.floorDiv(rx - GRID_X, 18);
		int row = Math.floorDiv(ry - GRID_Y, 18);
		if (col >= 0 && col < COLS && row >= 0 && row < ROWS) {
			int index = this.scroll * COLS + row * COLS + col;
			if (index < this.visible.size()) {
				g.setTooltipForNextFrame(this.font, this.visible.get(index).result().getHoverName(), mouseX, mouseY);
			}

			return;
		}

		BenchRecipes.Entry entry = this.selectedEntry();
		if (entry == null) {
			return;
		}

		for (int i = 0; i < entry.needs().size(); i++) {
			int nx = PANEL_X + (i % 2) * 62;
			int ny = NEED_Y + (i / 2) * NEED_STEP;
			if (rx >= nx && rx < nx + 18 && ry >= ny && ry < ny + 18) {
				List<Item> options = items(entry.needs().get(i));
				if (!options.isEmpty()) {
					Item shown = options.get((int) (System.currentTimeMillis() / 1000L % options.size()));
					g.setTooltipForNextFrame(this.font, new ItemStack(shown).getHoverName(), mouseX, mouseY);
				}
			}
		}
	}

	/** Called when a new catalogue arrives while the screen is open. */
	public void refresh() {
		this.filter();
	}
}
