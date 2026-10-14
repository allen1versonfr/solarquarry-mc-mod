package com.solarquarry.client.screen;

import com.solarquarry.blockentity.ItemPipeBlockEntity;
import com.solarquarry.menu.FilterPipeMenu;
import com.solarquarry.network.FilterConfigPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;

/** Filter pipe settings: allow/block mode, item templates, item tags and route priority. */
public class FilterPipeScreen extends AbstractContainerScreen<FilterPipeMenu> {
	private static final int WIDTH = 176;
	private static final int HEIGHT = 230;
	private static final int TAG_Y = 96;
	private static final int TAG_STEP = 15;
	private IndustrialButton modeButton;
	private IndustrialButton componentsButton;
	private IndustrialButton priorityDown;
	private IndustrialButton priorityUp;
	private final EditBox[] tagBoxes = new EditBox[ItemPipeBlockEntity.TAG_SLOTS];

	public FilterPipeScreen(FilterPipeMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		this.titleLabelX = 8;
		this.titleLabelY = 6;
		this.inventoryLabelX = FilterPipeMenu.INVENTORY_X;
		this.inventoryLabelY = FilterPipeMenu.INVENTORY_Y - 11;
	}

	@Override
	protected void init() {
		super.init();
		int x = this.leftPos;
		int y = this.topPos;
		this.modeButton = this.addRenderableWidget(new IndustrialButton(x + 6, y + 26, 52, 16, Component.empty(),
			input -> this.press(FilterPipeMenu.BUTTON_TOGGLE_MODE)));
		this.componentsButton = this.addRenderableWidget(new IndustrialButton(x + 6, y + 46, 52, 16, Component.empty(),
			input -> this.press(FilterPipeMenu.BUTTON_TOGGLE_COMPONENTS)).accent(0xFF47A7E0));
		this.componentsButton.setTooltip(Tooltip.create(Component.translatable("gui.solarquarry.match.tooltip")));
		this.priorityDown = this.addRenderableWidget(new IndustrialButton(x + 122, y + 40, 14, 14, Component.literal("-"),
			input -> this.press(FilterPipeMenu.BUTTON_PRIORITY_DOWN)));
		this.priorityUp = this.addRenderableWidget(new IndustrialButton(x + 156, y + 40, 14, 14, Component.literal("+"),
			input -> this.press(FilterPipeMenu.BUTTON_PRIORITY_UP)));
		Tooltip priorityTip = Tooltip.create(Component.translatable("gui.solarquarry.priority.tooltip"));
		this.priorityDown.setTooltip(priorityTip);
		this.priorityUp.setTooltip(priorityTip);

		ItemPipeBlockEntity pipe = this.pipe();
		for (int i = 0; i < this.tagBoxes.length; i++) {
			EditBox box = new EditBox(this.font, x + 8, y + TAG_Y + i * TAG_STEP, 110, 12, Component.translatable("gui.solarquarry.tag_box"));
			box.setMaxLength(ItemPipeBlockEntity.MAX_TAG_LENGTH);
			box.setHint(Component.translatable("gui.solarquarry.tag_hint"));
			box.setValue(pipe != null ? pipe.getTag(i) : "");
			final int index = i;
			box.setResponder(value -> this.onTagChanged(index, value));
			box.setTooltip(Tooltip.create(Component.translatable("gui.solarquarry.tag.tooltip")));
			this.tagBoxes[i] = this.addRenderableWidget(box);
		}

		this.updateLabels();
	}

	private ItemPipeBlockEntity pipe() {
		if (this.minecraft != null && this.minecraft.level != null && this.minecraft.level.getBlockEntity(this.menu.getPos()) instanceof ItemPipeBlockEntity pipe) {
			return pipe;
		}

		return null;
	}

	private void onTagChanged(int index, String value) {
		String trimmed = value.trim();
		if (trimmed.isEmpty() || Identifier.tryParse(trimmed.startsWith("#") ? trimmed.substring(1) : trimmed) != null) {
			ClientPlayNetworking.send(new FilterConfigPayload(this.menu.getPos(), index, trimmed));
		}
	}

	private void press(int id) {
		if (this.minecraft != null && this.minecraft.gameMode != null) {
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
		}
	}

	private void updateLabels() {
		if (this.modeButton == null) {
			return;
		}

		boolean whitelist = this.menu.isWhitelist();
		this.modeButton.setMessage(Component.translatable(whitelist ? "gui.solarquarry.whitelist" : "gui.solarquarry.blacklist"));
		this.modeButton.accent(whitelist ? 0xFF4CE06A : 0xFFE04B3A);
		this.modeButton.setTooltip(Tooltip.create(Component.translatable(whitelist ? "gui.solarquarry.filter_help_whitelist" : "gui.solarquarry.filter_help_blacklist")));
		this.componentsButton.setMessage(Component.translatable(this.menu.isMatchComponents() ? "gui.solarquarry.match_exact" : "gui.solarquarry.match_item"));
		int priority = this.menu.getPriority();
		this.priorityDown.active = priority > ItemPipeBlockEntity.MIN_PRIORITY;
		this.priorityUp.active = priority < ItemPipeBlockEntity.MAX_PRIORITY;
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		this.updateLabels();
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
		GuiDraw.hazard(g, x + 4, y + 17, WIDTH - 8, 3);
		boolean whitelist = this.menu.isWhitelist();
		GuiDraw.inset(g, x + FilterPipeMenu.FILTER_X - 4, y + FilterPipeMenu.FILTER_Y - 4, 62, 62, whitelist ? 0xFF13261A : 0xFF2A1416);
		for (int i = 0; i < 9; i++) {
			GuiDraw.slot(g, x + FilterPipeMenu.FILTER_X + (i % 3) * 18, y + FilterPipeMenu.FILTER_Y + (i / 3) * 18);
		}

		for (int i = 9; i < this.menu.slots.size(); i++) {
			var slot = this.menu.slots.get(i);
			GuiDraw.slot(g, x + slot.x, y + slot.y);
		}

		GuiDraw.inset(g, x + 120, y + 24, 52, 34, 0xFF1A1D20);
		GuiDraw.hazard(g, x + 4, y + 83, WIDTH - 8, 2);
		GuiDraw.lamp(g, x + WIDTH - 16, y + 6, whitelist ? 0xFF4CE06A : 0xFFE04B3A, true);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFF1D8A6, true);
		g.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, GuiDraw.LABEL, false);
		g.centeredText(this.font, Component.translatable("gui.solarquarry.priority"), 146, 27, GuiDraw.LABEL);
		int priority = this.menu.getPriority();
		g.centeredText(this.font, Component.literal((priority > 0 ? "+" : "") + priority), 146, 43, priority == 0 ? GuiDraw.LCD_DIM : 0xFFFFE066);
		g.text(this.font, Component.translatable("gui.solarquarry.tags"), 8, 87, GuiDraw.LABEL, false);
		for (int i = 0; i < this.tagBoxes.length; i++) {
			Component status = this.tagStatus(this.tagBoxes[i].getValue());
			g.text(this.font, status, 122, TAG_Y + i * TAG_STEP + 2, status.getStyle().getColor() != null ? status.getStyle().getColor().getValue() | 0xFF000000 : GuiDraw.LCD_DIM, false);
		}
	}

	/** Shows whether a typed tag exists and how many items it covers (tags are synced to clients). */
	private Component tagStatus(String raw) {
		String value = raw.trim();
		if (value.startsWith("#")) {
			value = value.substring(1);
		}

		if (value.isEmpty()) {
			return Component.empty();
		}

		Identifier id = Identifier.tryParse(value);
		if (id == null) {
			return Component.translatable("gui.solarquarry.tag.invalid").withColor(0xFF7A6A);
		}

		TagKey<Item> key = TagKey.create(Registries.ITEM, id);
		int count = 0;
		for (var ignored : BuiltInRegistries.ITEM.getTagOrEmpty(key)) {
			count++;
		}

		return count == 0
			? Component.translatable("gui.solarquarry.tag.empty").withColor(0xFFB066)
			: Component.translatable("gui.solarquarry.tag.items", count).withColor(0x8CFFC0);
	}
}
