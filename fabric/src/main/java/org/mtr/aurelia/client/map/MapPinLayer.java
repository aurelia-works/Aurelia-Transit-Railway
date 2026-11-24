package org.mtr.aurelia.client.map;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import javax.annotation.Nullable;

import java.util.List;

/**
 * Draws the {@link MapPins} on any map view: landmarks as gold diamonds with their names, readable from any zoom, and the
 * home as a small house. Names never overlap; one that would is left out. Nothing is allocated per frame.
 */
public final class MapPinLayer {

	/** A pin within this many screen pixels of the cursor is the one a right-click acts on. */
	public static final int HIT = 6;
	/** {@link #hit} result for the home pin; a landmark's index is returned as itself. */
	public static final int HOME = -2;
	public static final int NONE = -1;
	public static final Text HOME_LABEL = Text.translatable("gui.aurelia.map.home");
	private static final int OUTLINE = 0xFF000000;
	private static final int LANDMARK = 0xFFFFC23D;
	private static final int HOME_COLOR = 0xFFFF7A45;
	private static final int LINE = 9;
	private static final int MAX_LABELS = 64;
	private static final int[] LABELS = new int[MAX_LABELS * 4];

	private MapPinLayer() {
	}

	public static void draw(DrawContext context, int left, int top, int width, int height, double centerX, double centerZ, double scale) {
		final TextRenderer text = MinecraftClient.getInstance().textRenderer;
		final List<MapPins.Pin> landmarks = MapPins.landmarks();
		final MapPins.Pin home = MapPins.home();
		int labels = 0;
		if (home != null) {
			final int x = screen(home.x(), left, width, centerX, scale);
			final int y = screen(home.z(), top, height, centerZ, scale);
			if (inView(x, y, left, top, width, height)) {
				house(context, x, y);
				labels = label(context, text, null, x, y, labels);
			}
		}
		for (int i = 0; i < landmarks.size(); i++) {
			final MapPins.Pin pin = landmarks.get(i);
			final int x = screen(pin.x(), left, width, centerX, scale);
			final int y = screen(pin.z(), top, height, centerZ, scale);
			if (inView(x, y, left, top, width, height)) {
				diamond(context, x, y);
				labels = label(context, text, pin.name(), x, y, labels);
			}
		}
	}

	/** The pin under a screen point: {@link #HOME}, a landmark's index, or {@link #NONE}. */
	public static int hit(double mouseX, double mouseY, int left, int top, int width, int height, double centerX, double centerZ, double scale) {
		final MapPins.Pin home = MapPins.home();
		if (home != null && near(home, mouseX, mouseY, left, top, width, height, centerX, centerZ, scale)) {
			return HOME;
		}
		final List<MapPins.Pin> landmarks = MapPins.landmarks();
		for (int i = landmarks.size() - 1; i >= 0; i--) {
			if (near(landmarks.get(i), mouseX, mouseY, left, top, width, height, centerX, centerZ, scale)) {
				return i;
			}
		}
		return NONE;
	}

	private static boolean near(MapPins.Pin pin, double mouseX, double mouseY, int left, int top, int width, int height, double centerX, double centerZ, double scale) {
		return Math.abs(screen(pin.x(), left, width, centerX, scale) - mouseX) <= HIT && Math.abs(screen(pin.z(), top, height, centerZ, scale) - mouseY) <= HIT;
	}

	private static int screen(int block, int start, int size, double center, double scale) {
		return (int) Math.round(start + size / 2.0 + (block + 0.5 - center) * scale);
	}

	private static boolean inView(int x, int y, int left, int top, int width, int height) {
		return x >= left - 4 && y >= top - 4 && x <= left + width + 4 && y <= top + height + 4;
	}

	private static void diamond(DrawContext context, int x, int y) {
		for (int row = -4; row <= 4; row++) {
			final int half = 4 - Math.abs(row);
			context.fill(x - half - 1, y + row, x + half + 2, y + row + 1, OUTLINE);
		}
		for (int row = -3; row <= 3; row++) {
			final int half = 3 - Math.abs(row);
			context.fill(x - half, y + row, x + half + 1, y + row + 1, LANDMARK);
		}
	}

	private static void house(DrawContext context, int x, int y) {
		// roof, then walls, then the door
		for (int row = 0; row < 4; row++) {
			context.fill(x - row - 1, y - 5 + row, x + row + 2, y - 4 + row, OUTLINE);
			context.fill(x - row, y - 4 + row, x + row + 1, y - 3 + row, HOME_COLOR);
		}
		context.fill(x - 4, y - 1, x + 5, y + 5, OUTLINE);
		context.fill(x - 3, y - 1, x + 4, y + 4, HOME_COLOR);
		context.fill(x, y + 1, x + 1, y + 4, OUTLINE);
	}

	/** Draws a name under a pin unless it would overlap one already drawn; {@code name} null is the home. Returns the label count. */
	private static int label(DrawContext context, TextRenderer text, @Nullable String name, int x, int y, int labels) {
		if (labels >= MAX_LABELS) {
			return labels;
		}
		final int width = name == null ? text.getWidth(HOME_LABEL) : text.getWidth(name);
		final int labelX = x - width / 2;
		final int labelY = y + 6;
		for (int i = 0; i < labels; i++) {
			if (labelX < LABELS[i * 4 + 2] + 2 && labelX + width + 2 > LABELS[i * 4] && labelY < LABELS[i * 4 + 3] && labelY + LINE > LABELS[i * 4 + 1]) {
				return labels;
			}
		}
		LABELS[labels * 4] = labelX;
		LABELS[labels * 4 + 1] = labelY;
		LABELS[labels * 4 + 2] = labelX + width;
		LABELS[labels * 4 + 3] = labelY + LINE;
		if (name == null) {
			context.drawTextWithShadow(text, HOME_LABEL, labelX, labelY, 0xFFFFFFFF);
		} else {
			context.drawTextWithShadow(text, name, labelX, labelY, 0xFFFFFFFF);
		}
		return labels + 1;
	}
}
