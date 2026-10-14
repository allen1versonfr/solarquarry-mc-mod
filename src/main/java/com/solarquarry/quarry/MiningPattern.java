package com.solarquarry.quarry;

/**
 * Pure geometry of a quarry's work volume, independent of the world.
 *
 * <p>Cells are addressed by a linear step index {@code 0 <= step < total()}. Each step maps to local coordinates:
 * <ul>
 * <li>{@code lateral}: 0..width-1 across the area (the quarry sits at {@code lateral == width / 2})</li>
 * <li>{@code forward}: 1..length, distance in front of the machine's back face (the work volume starts one block behind it)</li>
 * <li>{@code dy}: vertical offset from the quarry's own Y level</li>
 * </ul>
 * Rows alternate direction (serpentine) and every other layer runs back, so the drill head never jumps across the area.
 */
public record MiningPattern(QuarryMode mode, int width, int length, int height, int layers) {
	public static final int MAX_TUNNEL_HEIGHT = 16;
	public static final int MAX_CLEAR_HEIGHT = 128;
	/** Tunnels may be longer than the area limit, up to this multiple of it. */
	public static final int TUNNEL_LENGTH_FACTOR = 4;

	/** Local coordinates of one cell. */
	public record Cell(int lateral, int dy, int forward) {
	}

	public MiningPattern {
		width = Math.max(1, width);
		length = Math.max(1, length);
		height = Math.max(1, height);
		layers = Math.max(0, layers);
	}

	/**
	 * Builds the pattern for a quarry.
	 *
	 * @param startY the quarry's Y level
	 * @param bottomY lowest Y to mine in downward modes (inclusive)
	 * @param topLimitY highest buildable Y (inclusive), used to clamp upward modes
	 */
	public static MiningPattern of(QuarryMode mode, int width, int length, int height, int startY, int bottomY, int topLimitY) {
		return switch (mode) {
			case STRIP_MINE, ORE_PRIORITY -> new MiningPattern(mode, width, length, 1, Math.max(0, startY - bottomY + 1));
			case TUNNEL_BORE -> {
				int h = Math.max(1, Math.min(height, Math.min(MAX_TUNNEL_HEIGHT, topLimitY - startY + 1)));
				yield new MiningPattern(mode, width, length, h, h);
			}
			case AREA_CLEAR -> {
				int h = Math.max(1, Math.min(height, Math.min(MAX_CLEAR_HEIGHT, topLimitY - startY + 1)));
				yield new MiningPattern(mode, width, length, h, h);
			}
		};
	}

	public long cellsPerLayer() {
		return this.mode == QuarryMode.TUNNEL_BORE ? (long) this.width * this.height : (long) this.width * this.length;
	}

	public long total() {
		if (this.mode == QuarryMode.TUNNEL_BORE) {
			return (long) this.width * this.height * this.length;
		}

		return this.cellsPerLayer() * this.layers;
	}

	public boolean isComplete(long step) {
		return step >= this.total();
	}

	public Cell cell(long step) {
		if (step < 0) {
			step = 0;
		}

		if (this.mode == QuarryMode.TUNNEL_BORE) {
			long perSlice = (long) this.width * this.height;
			int slice = (int) (step / perSlice);
			int within = (int) (step % perSlice);
			int row = within / this.width;
			int col = within % this.width;
			if ((row & 1) == 1) {
				col = this.width - 1 - col;
			}

			int dy = this.height - 1 - row;
			return new Cell(col, dy, 1 + slice);
		}

		long perLayer = this.cellsPerLayer();
		int layer = (int) (step / perLayer);
		int within = (int) (step % perLayer);
		int row = within / this.width;
		int col = within % this.width;
		if ((row & 1) == 1) {
			col = this.width - 1 - col;
		}

		if ((layer & 1) == 1) {
			row = this.length - 1 - row;
		}

		int dy = this.mode == QuarryMode.AREA_CLEAR ? this.height - 1 - layer : -layer;
		return new Cell(col, dy, 1 + row);
	}

	/** Lateral offset of a cell from the quarry's center line. */
	public int lateralOffset(int lateral) {
		return lateral - this.width / 2;
	}

	/** Index of the first step of the layer (or tunnel slice) containing {@code step}. */
	public long layerStart(long step) {
		long per = this.cellsPerLayer();
		return step < 0 ? 0 : step / per * per;
	}

	/** Lowest dy reached by the pattern. */
	public int minDy() {
		return switch (this.mode) {
			case STRIP_MINE, ORE_PRIORITY -> -Math.max(0, this.layers - 1);
			case TUNNEL_BORE, AREA_CLEAR -> 0;
		};
	}

	/** Highest dy reached by the pattern. */
	public int maxDy() {
		return switch (this.mode) {
			case STRIP_MINE, ORE_PRIORITY -> 0;
			case TUNNEL_BORE, AREA_CLEAR -> this.height - 1;
		};
	}

	/** Progress in permille, clamped to 0..1000. */
	public int permille(long step) {
		long total = this.total();
		if (total <= 0) {
			return 1000;
		}

		return (int) Math.max(0, Math.min(1000, step * 1000 / total));
	}
}
