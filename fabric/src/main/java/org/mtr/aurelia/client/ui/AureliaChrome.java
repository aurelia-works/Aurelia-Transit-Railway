package org.mtr.aurelia.client.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Flat and rounded rectangles for the Aurelia look. Every method takes the context and numbers it already has.
 * Nothing is constructed here: tick labels come from {@link #NUMBERS}.
 */
public final class AureliaChrome {

	private static final int HANDLE = 6;
	private static final int TICK = 10;
	/** Dwell-slider tick labels fit in this table. A larger number is not drawn. */
	private static final String[] NUMBERS = new String[121];

	static {
		for (int i = 0; i < NUMBERS.length; i++) {
			NUMBERS[i] = Integer.toString(i);
		}
	}

	private AureliaChrome() {
	}

	public static void button(DrawContext context, int x, int y, int w, int h, boolean hovered, boolean active, boolean focused) {
		context.fill(x, y, x + w, y + h, active ? AureliaPalette.BUTTON : AureliaPalette.BG);
		frame(context, x, y, w, h, focused || (hovered && active) ? AureliaPalette.ACCENT : AureliaPalette.BORDER);
		if (hovered && active) {
			context.fill(x, y, x + 2, y + h, AureliaPalette.ACCENT);
		}
	}

	public static void frame(DrawContext context, int x, int y, int w, int h, int color) {
		if (w <= 0 || h <= 0) {
			return;
		}
		context.fill(x, y, x + w, y + 1, color);
		context.fill(x, y + h - 1, x + w, y + h, color);
		context.fill(x, y, x + 1, y + h, color);
		context.fill(x + w - 1, y, x + w, y + h, color);
	}

	public static void checkbox(DrawContext context, int x, int y, int h, boolean checked, boolean hovered) {
		final int box = 12;
		final int top = y + Math.max(0, (h - box) / 2);
		context.fill(x + 3, top, x + 3 + box, top + box, AureliaPalette.FIELD);
		frame(context, x + 3, top, box, box, hovered ? AureliaPalette.ACCENT : AureliaPalette.BORDER);
		if (checked) {
			context.fill(x + 6, top + 3, x + 3 + box - 3, top + box - 3, AureliaPalette.ACCENT);
		}
	}

	/** Panel slider: the label is drawn by the widget, centred, after this returns. */
	public static void slider(DrawContext context, int x, int y, int w, int h, double value, boolean hovered) {
		context.fill(x, y, x + w, y + h, AureliaPalette.FIELD);
		frame(context, x, y, w, h, AureliaPalette.BORDER);
		final int span = Math.max(1, w - HANDLE);
		final int centre = x + (int) (clamp01(value) * span) + HANDLE / 2;
		// A tinted fill up to the value, marked only at the top and bottom edges so it never crosses the label
		context.fill(x + 1, y + 1, centre, y + h - 1, (AureliaPalette.ACCENT & 0xFFFFFF) | 0x48000000);
		final int marker = hovered ? AureliaPalette.ACCENT : AureliaPalette.FG_LIGHT;
		context.fill(centre - 1, y, centre + 1, y + 3, marker);
		context.fill(centre - 1, y + h - 3, centre + 1, y + h, marker);
	}

	/**
	 * Depot and siding slider. MTR draws the value to the right of the track, and tick marks under the dwell slider.
	 * {@code message} is the widget's existing text.
	 */
	public static void shortSlider(DrawContext context, TextRenderer textRenderer, int x, int y, int w, int h, int value, int max, int markerFrequency, int markerDisplayedRatio, boolean hovered, Text message) {
		context.fill(x, y, x + w, y + h, AureliaPalette.FIELD);
		frame(context, x, y, w, h, AureliaPalette.BORDER);
		final int maxSafe = Math.max(1, max);
		final int span = Math.max(1, w - HANDLE);
		final int handleX = x + (int) ((long) span * value / maxSafe);
		context.fill(handleX, y, handleX + HANDLE, y + h, hovered ? AureliaPalette.ACCENT : AureliaPalette.FG_LIGHT);
		if (message != null) {
			context.drawText(textRenderer, message, x + w + 6, y + (h - 8) / 2, AureliaPalette.FG, false);
		}
		if (markerFrequency <= 0 || markerDisplayedRatio <= 0) {
			return;
		}
		final int ticks = maxSafe / markerFrequency;
		for (int i = 1; i <= ticks; i++) {
			final int xOffset = (int) ((long) span * i * markerFrequency / maxSafe);
			final int tickX = x + xOffset + HANDLE / 3;
			context.fill(tickX, y + h, tickX + 2, y + h + TICK, AureliaPalette.BORDER);
			final int shown = i * markerFrequency / markerDisplayedRatio;
			if (shown < 0 || shown >= NUMBERS.length) {
				continue;
			}
			final String label = NUMBERS[shown];
			final int labelX = x + xOffset + HANDLE / 2 - textRenderer.getWidth(label) / 2;
			context.drawText(textRenderer, label, labelX, y + h + TICK + 2, AureliaPalette.FG, false);
		}
	}

	private static double clamp01(double value) {
		if (value < 0) {
			return 0;
		}
		if (value > 1) {
			return 1;
		}
		return value;
	}

	/** A filled rectangle with rounded corners of {@code radius} GUI pixels, drawn row by row so it needs no texture. */
	public static void rounded(DrawContext context, int x, int y, int w, int h, int radius, int color) {
		if (w <= 0 || h <= 0) {
			return;
		}
		final int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
		context.fill(x, y + r, x + w, y + h - r, color);
		for (int row = 0; row < r; row++) {
			final double dy = r - row - 0.5;
			final int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
			context.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
			context.fill(x + inset, y + h - row - 1, x + w - inset, y + h - row, color);
		}
	}

	/** A rounded outline one pixel wide. */
	public static void roundedFrame(DrawContext context, int x, int y, int w, int h, int radius, int color) {
		final int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
		context.fill(x + r, y, x + w - r, y + 1, color);
		context.fill(x + r, y + h - 1, x + w - r, y + h, color);
		context.fill(x, y + r, x + 1, y + h - r, color);
		context.fill(x + w - 1, y + r, x + w, y + h - r, color);
		for (int row = 0; row < r; row++) {
			final double dy = r - row - 0.5;
			final int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
			final int next = row + 1 < r ? (int) Math.round(r - Math.sqrt(Math.max(0, r * r - (dy - 1) * (dy - 1)))) : inset;
			final int span = Math.max(1, inset - next);
			context.fill(x + inset - span + 1, y + row, x + inset + 1, y + row + 1, color);
			context.fill(x + w - inset - 1, y + row, x + w - inset + span - 1, y + row + 1, color);
			context.fill(x + inset - span + 1, y + h - row - 1, x + inset + 1, y + h - row, color);
			context.fill(x + w - inset - 1, y + h - row - 1, x + w - inset + span - 1, y + h - row, color);
		}
	}

	/** A soft shadow under a rounded panel: a few widening layers, darkest nearest the panel, a little lower than it. */
	public static void shadow(DrawContext context, int x, int y, int w, int h, int radius) {
		for (int i = 4; i >= 1; i--) {
			rounded(context, x - i, y - i + 2, w + 2 * i, h + 2 * i, radius + i, (0x16 + (4 - i) * 0x0C) << 24);
		}
	}

	/** {@code color} with its alpha multiplied by {@code alpha} (0 to 1). */
	public static int fade(int color, float alpha) {
		final int a = Math.round(((color >>> 24) & 0xFF) * Math.max(0, Math.min(1, alpha)));
		return (a << 24) | (color & 0xFFFFFF);
	}

	/** A colour part way from {@code from} to {@code to}, alpha included. */
	public static int mix(int from, int to, float t) {
		final float u = Math.max(0, Math.min(1, t));
		int out = 0;
		for (int shift = 0; shift < 32; shift += 8) {
			final int a = (from >>> shift) & 0xFF;
			final int b = (to >>> shift) & 0xFF;
			out |= Math.round(a + (b - a) * u) << shift;
		}
		return out;
	}
}
