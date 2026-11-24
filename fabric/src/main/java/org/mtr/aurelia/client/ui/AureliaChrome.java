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

	/**
	 * A button face. Each state reads on its own: hover lifts the face and lightens the edge, keyboard focus draws a
	 * two-pixel blue ring inside the edge (inside, because MTR packs buttons edge to edge), and a disabled button sinks
	 * into the panel with no edge to speak of.
	 */
	public static void button(DrawContext context, int x, int y, int w, int h, boolean hovered, boolean active, boolean focused) {
		if (w <= 0 || h <= 0) {
			return;
		}
		final boolean hot = hovered && active;
		final int edge = !active ? AureliaPalette.LINE : hot ? AureliaPalette.CONTROL_BORDER : AureliaPalette.OUTLINE;
		final int face = !active ? AureliaPalette.SURFACE : hot ? AureliaPalette.RAISED_HOVER : AureliaPalette.RAISED;
		rounded(context, x, y, w, h, AureliaPalette.RADIUS_SMALL, edge);
		rounded(context, x + 1, y + 1, w - 2, h - 2, AureliaPalette.RADIUS_SMALL - 1, face);
		if (focused && active) {
			focusRing(context, x, y, w, h);
		}
	}

	/** Two pixels of {@link AureliaPalette#FOCUS} just inside a control's edge. */
	public static void focusRing(DrawContext context, int x, int y, int w, int h) {
		roundedFrame(context, x, y, w, h, AureliaPalette.RADIUS_SMALL, AureliaPalette.FOCUS);
		frame(context, x + 1, y + 1, w - 2, h - 2, fade(AureliaPalette.FOCUS, 0.45F));
	}

	/** The text colour of a control's label. */
	public static int labelColor(boolean active) {
		return active ? AureliaPalette.TEXT : AureliaPalette.TEXT_DISABLED;
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

	/** A box with a white tick on the brand blue when checked. The outline alone shows where an empty box is. */
	public static void checkbox(DrawContext context, int x, int y, int h, boolean checked, boolean hovered) {
		checkbox(context, x, y, h, checked, hovered, false);
	}

	public static void checkbox(DrawContext context, int x, int y, int h, boolean checked, boolean hovered, boolean focused) {
		final int box = 12;
		final int left = x + 3;
		final int top = y + Math.max(0, (h - box) / 2);
		final int edge = focused ? AureliaPalette.FOCUS : checked || hovered ? AureliaPalette.ACCENT_LIGHT : AureliaPalette.CONTROL_BORDER;
		rounded(context, left, top, box, box, AureliaPalette.RADIUS_SMALL, edge);
		rounded(context, left + 1, top + 1, box - 2, box - 2, AureliaPalette.RADIUS_SMALL - 1, checked ? AureliaPalette.ACCENT : AureliaPalette.FIELD);
		if (checked) {
			// A tick two pixels thick: the short stroke down to the corner, then the long one up to the right
			for (int i = 0; i < 3; i++) {
				context.fill(left + 3 + i, top + 5 + i, left + 5 + i, top + 7 + i, AureliaPalette.TEXT);
			}
			for (int i = 0; i < 4; i++) {
				context.fill(left + 6 + i, top + 6 - i, left + 8 + i, top + 8 - i, AureliaPalette.TEXT);
			}
		}
	}

	/** Panel slider: the label is drawn by the widget, centred, after this returns. */
	public static void slider(DrawContext context, int x, int y, int w, int h, double value, boolean hovered) {
		slider(context, x, y, w, h, value, hovered, false);
	}

	public static void slider(DrawContext context, int x, int y, int w, int h, double value, boolean hovered, boolean focused) {
		context.fill(x, y, x + w, y + h, AureliaPalette.FIELD);
		frame(context, x, y, w, h, hovered ? AureliaPalette.TEXT_DIM : AureliaPalette.CONTROL_BORDER);
		final int span = Math.max(1, w - HANDLE);
		final int centre = x + (int) (clamp01(value) * span) + HANDLE / 2;
		// A tinted fill up to the value, marked only at the top and bottom edges so it never crosses the label
		context.fill(x + 1, y + 1, centre, y + h - 1, fade(AureliaPalette.ACCENT, 0.55F));
		final int marker = hovered || focused ? AureliaPalette.ACCENT_LIGHT : AureliaPalette.TEXT;
		context.fill(centre - 1, y, centre + 1, y + 4, marker);
		context.fill(centre - 1, y + h - 4, centre + 1, y + h, marker);
		if (focused) {
			frame(context, x, y, w, h, AureliaPalette.FOCUS);
		}
	}

	/**
	 * Depot and siding slider. MTR draws the value to the right of the track, and tick marks under the dwell slider.
	 * {@code message} is the widget's existing text.
	 */
	public static void shortSlider(DrawContext context, TextRenderer textRenderer, int x, int y, int w, int h, int value, int max, int markerFrequency, int markerDisplayedRatio, boolean hovered, Text message) {
		context.fill(x, y, x + w, y + h, AureliaPalette.FIELD);
		frame(context, x, y, w, h, AureliaPalette.CONTROL_BORDER);
		final int maxSafe = Math.max(1, max);
		final int span = Math.max(1, w - HANDLE);
		final int handleX = x + (int) ((long) span * value / maxSafe);
		context.fill(handleX, y, handleX + HANDLE, y + h, hovered ? AureliaPalette.ACCENT_LIGHT : AureliaPalette.TEXT);
		if (message != null) {
			context.drawText(textRenderer, message, x + w + 6, y + (h - 8) / 2, AureliaPalette.TEXT, false);
		}
		if (markerFrequency <= 0 || markerDisplayedRatio <= 0) {
			return;
		}
		final int ticks = maxSafe / markerFrequency;
		for (int i = 1; i <= ticks; i++) {
			final int xOffset = (int) ((long) span * i * markerFrequency / maxSafe);
			final int tickX = x + xOffset + HANDLE / 3;
			context.fill(tickX, y + h, tickX + 2, y + h + TICK, AureliaPalette.OUTLINE);
			final int shown = i * markerFrequency / markerDisplayedRatio;
			if (shown < 0 || shown >= NUMBERS.length) {
				continue;
			}
			final String label = NUMBERS[shown];
			final int labelX = x + xOffset + HANDLE / 2 - textRenderer.getWidth(label) / 2;
			context.drawText(textRenderer, label, labelX, y + h + TICK + 2, AureliaPalette.TEXT_DIM, false);
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
