package com.solarquarry.client.screen;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * Beveled steel push button with a coloured accent strip. Extends the vanilla {@link Button} so it behaves like
 * any other button (keyboard focus, narration, click sound) and can be found by Fabric's client game tests.
 */
public class IndustrialButton extends Button {
	private final Consumer<InputWithModifiers> action;
	private int accent = GuiDraw.AMBER;

	public IndustrialButton(int x, int y, int width, int height, Component message, Consumer<InputWithModifiers> action) {
		super(x, y, width, height, message, button -> { }, Button.DEFAULT_NARRATION);
		this.action = action;
	}

	public IndustrialButton accent(int color) {
		this.accent = color;
		return this;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		this.action.accept(input);
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int x = this.getX();
		int y = this.getY();
		int w = this.getWidth();
		int h = this.getHeight();
		boolean hot = this.active && this.isHoveredOrFocused();
		g.fill(x, y, x + w, y + h, GuiDraw.OUTLINE);
		if (this.active) {
			g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, hot ? 0xFF6A717C : 0xFF555C66, hot ? 0xFF3E434A : 0xFF33383F);
			g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF8B939E);
			g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0xFF1E2125);
			g.fill(x + 1, y + h - 3, x + w - 1, y + h - 2, hot ? this.accent : GuiDraw.darken(this.accent, 0.45F));
		} else {
			g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF2A2E33);
		}

		int color = this.active ? (hot ? 0xFFFFF2D0 : 0xFFE6E9ED) : 0xFF6E747C;
		var font = Minecraft.getInstance().font;
		g.centeredText(font, this.getMessage(), x + w / 2, y + (h - 8) / 2, color);
	}
}
