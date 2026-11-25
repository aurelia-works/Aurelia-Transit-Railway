package org.mtr.aurelia.dashboard;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.mtr.aurelia.client.map.MapGround;
import org.mtr.aurelia.client.map.MapPinLayer;
import org.mtr.aurelia.client.map.ServerTiles;
import org.mtr.core.data.AreaBase;
import org.mtr.core.data.Station;
import org.mtr.mod.client.MinecraftClientData;

/**
 * What ATR adds to MTR's dashboard map ({@code WidgetMap}), drawn through {@code DashboardMapMixin}: the explored ground
 * of the whole world under MTR's overlays, in place of MTR's picture of the loaded chunks only; and, zoomed out, a dot and
 * a name for each station too small on screen for MTR to name, so stations far away can still be found; and the
 * player's home and landmark pins, which a right-click on the map sets and removes.
 */
public final class DashboardMapLayers {

	/** A station narrower than this on screen gets a dot; MTR's own rectangle is too small to see. */
	private static final int DOT_BELOW = 6;
	/** MTR names a station once it is this wide on screen ({@code WidgetMap.canDrawAreaText}). */
	private static final int MTR_LABEL_WIDTH = 80;
	private static final int MAX_LABELS = 64;
	private static final int OUTLINE = 0xFF000000;
	private static final int LINE = 9;
	/** Screen boxes of the names drawn this frame, four ints each, so names never overlap. */
	private static final int[] LABELS = new int[MAX_LABELS * 4];

	private DashboardMapLayers() {
	}

	public static void ground(DrawContext context, int left, int top, int width, int height, double centerX, double centerZ, double scale) {
		context.enableScissor(left, top, left + width, top + height);
		MapGround.draw(context, left, top, width, height, centerX, centerZ, scale);
		ServerTiles.askInView(width, height, centerX, centerZ, scale);
		context.disableScissor();
	}

	public static void farStations(DrawContext context, int left, int top, int width, int height, double centerX, double centerZ, double scale) {
		final TextRenderer text = MinecraftClient.getInstance().textRenderer;
		int labels = 0;
		context.enableScissor(left, top, left + width, top + height);
		for (final Station station : MinecraftClientData.getDashboardInstance().stations) {
			if (!AreaBase.validCorners(station)) {
				continue;
			}
			final double screenSize = Math.max(station.getMaxX() - station.getMinX(), station.getMaxZ() - station.getMinZ()) * scale;
			if (screenSize >= MTR_LABEL_WIDTH) {
				continue;
			}
			final int x = (int) Math.round(left + width / 2.0 + ((station.getMinX() + station.getMaxX()) / 2.0 - centerX) * scale);
			final int y = (int) Math.round(top + height / 2.0 + ((station.getMinZ() + station.getMaxZ()) / 2.0 - centerZ) * scale);
			if (x < left - MTR_LABEL_WIDTH || x > left + width + MTR_LABEL_WIDTH || y < top - 8 || y > top + height + 8) {
				continue;
			}
			if (screenSize < DOT_BELOW) {
				context.fill(x - 3, y - 3, x + 3, y + 3, OUTLINE);
				context.fill(x - 2, y - 2, x + 2, y + 2, 0xFF000000 | station.getColor());
			}
			if (labels < MAX_LABELS) {
				final String name = firstLanguage(station.getName());
				final int labelWidth = text.getWidth(name);
				final int labelX = x - labelWidth / 2;
				final int labelY = y + 4;
				if (!overlaps(labels, labelX, labelY, labelWidth)) {
					LABELS[labels * 4] = labelX;
					LABELS[labels * 4 + 1] = labelY;
					LABELS[labels * 4 + 2] = labelX + labelWidth;
					LABELS[labels * 4 + 3] = labelY + LINE;
					labels++;
					context.drawTextWithShadow(text, name, labelX, labelY, 0xFFFFFFFF);
				}
			}
		}
		context.disableScissor();
	}

	/** The player's home and landmark pins, over everything MTR draws. */
	public static void pins(DrawContext context, int left, int top, int width, int height, double centerX, double centerZ, double scale) {
		context.enableScissor(left, top, left + width, top + height);
		MapPinLayer.draw(context, left, top, width, height, centerX, centerZ, scale);
		context.disableScissor();
	}

	/** MTR writes a name in several languages as {@code first|second}; the first part is enough for a map label. */
	private static String firstLanguage(String name) {
		final int bar = name.indexOf('|');
		return bar < 0 ? name : name.substring(0, bar);
	}

	private static boolean overlaps(int labels, int x, int y, int width) {
		for (int i = 0; i < labels; i++) {
			if (x < LABELS[i * 4 + 2] + 2 && x + width + 2 > LABELS[i * 4] && y < LABELS[i * 4 + 3] && y + LINE > LABELS[i * 4 + 1]) {
				return true;
			}
		}
		return false;
	}
}
