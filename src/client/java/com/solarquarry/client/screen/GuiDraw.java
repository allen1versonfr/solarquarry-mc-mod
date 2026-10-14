package com.solarquarry.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Procedural drawing for the industrial GUI style (no texture atlas needed, scales cleanly). */
public final class GuiDraw {
	public static final int STEEL = 0xFF3B4048;
	public static final int STEEL_LIGHT = 0xFF5B636E;
	public static final int STEEL_DARK = 0xFF23272C;
	public static final int OUTLINE = 0xFF0D0F12;
	public static final int AMBER = 0xFFE3A23A;
	public static final int LCD_BG = 0xFF0B1611;
	public static final int LCD_GRID = 0xFF102219;
	public static final int LCD_TEXT = 0xFF8CFFC0;
	public static final int LCD_DIM = 0xFF4F9C78;
	public static final int LABEL = 0xFFC9CED6;

	private GuiDraw() {
	}

	/** Raised brushed-steel panel with beveled edges and corner bolts. */
	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, OUTLINE);
		g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF474D56, 0xFF30353C);
		g.fill(x + 1, y + 1, x + w - 1, y + 2, STEEL_LIGHT);
		g.fill(x + 1, y + 1, x + 2, y + h - 1, STEEL_LIGHT);
		g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, STEEL_DARK);
		g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, STEEL_DARK);
		for (int i = y + 4; i < y + h - 4; i += 3) {
			g.fill(x + 3, i, x + w - 3, i + 1, 0x0CFFFFFF);
		}

		bolt(g, x + 3, y + 3);
		bolt(g, x + w - 6, y + 3);
		bolt(g, x + 3, y + h - 6);
		bolt(g, x + w - 6, y + h - 6);
	}

	/** Recessed area. */
	public static void inset(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill) {
		g.fill(x, y, x + w, y + h, fill);
		g.fill(x, y, x + w, y + 1, STEEL_DARK);
		g.fill(x, y, x + 1, y + h, STEEL_DARK);
		g.fill(x, y + h - 1, x + w, y + h, STEEL_LIGHT);
		g.fill(x + w - 1, y, x + w, y + h, STEEL_LIGHT);
	}

	public static void bolt(GuiGraphicsExtractor g, int x, int y) {
		g.fill(x, y, x + 3, y + 3, 0xFF1A1D21);
		g.fill(x, y, x + 2, y + 2, 0xFF8A929C);
		g.fill(x + 1, y + 1, x + 2, y + 2, 0xFFB9C0C8);
	}

	public static void slot(GuiGraphicsExtractor g, int x, int y) {
		inset(g, x - 1, y - 1, 18, 18, 0xFF1F2328);
	}

	public static void hazard(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.enableScissor(x, y, x + w, y + h);
		g.fill(x, y, x + w, y + h, 0xFF1B1B1B);
		for (int i = -h; i < w; i += 8) {
			for (int j = 0; j < h; j++) {
				int sx = x + i + j;
				g.fill(sx, y + j, sx + 4, y + j + 1, AMBER);
			}
		}

		g.disableScissor();
	}

	/** Status light with a soft halo. */
	public static void lamp(GuiGraphicsExtractor g, int x, int y, int color, boolean lit) {
		int base = lit ? color : darken(color, 0.3F);
		g.fill(x - 1, y - 1, x + 7, y + 7, OUTLINE);
		g.fill(x, y, x + 6, y + 6, base);
		g.fill(x + 1, y + 1, x + 3, y + 3, lit ? 0xCCFFFFFF : 0x44FFFFFF);
		if (lit) {
			g.fill(x - 2, y - 2, x + 8, y + 8, (color & 0x00FFFFFF) | 0x30000000);
		}
	}

	public static int darken(int argb, float factor) {
		int a = argb >>> 24;
		int r = (int) (((argb >> 16) & 0xFF) * factor);
		int gr = (int) (((argb >> 8) & 0xFF) * factor);
		int b = (int) ((argb & 0xFF) * factor);
		return a << 24 | r << 16 | gr << 8 | b;
	}

	/** Horizontal segmented progress bar. */
	public static void hbar(GuiGraphicsExtractor g, int x, int y, int w, int h, float fraction, int color) {
		inset(g, x, y, w, h, 0xFF06100B);
		int filled = Math.round((w - 2) * Math.max(0.0F, Math.min(1.0F, fraction)));
		if (filled > 0) {
			g.fillGradient(x + 1, y + 1, x + 1 + filled, y + h - 1, color, darken(color, 0.6F));
		}

		for (int i = x + 4; i < x + w - 1; i += 4) {
			g.fill(i, y + 1, i + 1, y + h - 1, 0x40000000);
		}
	}

	/** Vertical energy cell with glowing segments. */
	public static void energyBar(GuiGraphicsExtractor g, int x, int y, int w, int h, float fraction) {
		inset(g, x, y, w, h, 0xFF080A0C);
		int inner = h - 2;
		int filled = Math.round(inner * Math.max(0.0F, Math.min(1.0F, fraction)));
		if (filled > 0) {
			g.fillGradient(x + 1, y + 1 + inner - filled, x + w - 1, y + h - 1, 0xFFFFE066, 0xFFE0451F);
			g.fill(x + 2, y + 1 + inner - filled, x + 3, y + h - 1, 0x55FFFFFF);
		}

		for (int i = y + 1 + inner - 4; i > y + 1; i -= 4) {
			g.fill(x + 1, i, x + w - 1, i + 1, 0x66000000);
		}
	}

	/** LCD screen background with a faint grid. */
	public static void lcd(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, OUTLINE);
		inset(g, x, y, w, h, LCD_BG);
		for (int i = y + 3; i < y + h - 1; i += 4) {
			g.fill(x + 1, i, x + w - 1, i + 1, LCD_GRID);
		}

		g.fill(x + 1, y + 1, x + w - 1, y + 3, 0x22FFFFFF);
	}
}
