package org.mtr.aurelia.client.map;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The world map's station list: every station the map knows ({@link SeenNetwork}, which takes in the dashboard's
 * stations), sorted by name and filtered by what is typed above it. It is rebuilt only when the stations or the filter
 * change, so drawing it allocates nothing.
 */
public final class StationList {

	public static final int WIDTH = 150;
	/** Height of the filter field the screen places at the panel's top. */
	public static final int FIELD = 16;
	private static final int PAD = 4;
	private static final int ROW = 11;
	private static final int PANEL = 0xD0101418;
	private static final int HOVER = 0x40FFFFFF;
	private static final Text NONE_SEEN = Text.translatable("gui.aurelia.map.stations_none");
	private static final Text NO_MATCH = Text.translatable("gui.aurelia.map.stations_no_match");

	private record Entry(SeenNetwork.Area area, String label, String search) {
	}

	private Entry[] entries = new Entry[0];
	private int[] shown = new int[0];
	private int shownCount;
	private int scroll;
	private String filter = "";
	@Nullable
	private SeenNetwork network;
	private int version;

	/** Called by the filter field whenever its text changes. */
	public void setFilter(String text) {
		filter = text.trim().toLowerCase(Locale.ROOT);
		scroll = 0;
		refilter();
	}

	/** Draws the panel's background and rows; the screen draws the filter field over the top. */
	public void draw(DrawContext context, int left, int top, int height, int mouseX, int mouseY) {
		update();
		final TextRenderer text = MinecraftClient.getInstance().textRenderer;
		context.fill(left, top, left + WIDTH, top + height, PANEL);
		final int listTop = listTop(top);
		if (shownCount == 0) {
			context.drawTextWithShadow(text, entries.length == 0 ? NONE_SEEN : NO_MATCH, left + PAD + 1, listTop + 2, 0x888888);
			return;
		}
		final int rows = rows(top, height);
		final int hovered = row(left, top, height, mouseX, mouseY);
		for (int i = 0; i < rows && scroll + i < shownCount; i++) {
			final Entry entry = entries[shown[scroll + i]];
			final int y = listTop + i * ROW;
			if (scroll + i == hovered) {
				context.fill(left + 1, y, left + WIDTH - 1, y + ROW, HOVER);
			}
			context.fill(left + PAD + 1, y + 2, left + PAD + 7, y + 8, 0xFF000000 | entry.area.color());
			context.drawTextWithShadow(text, entry.label, left + PAD + 11, y + 2, 0xFFFFFFFF);
		}
	}

	public boolean contains(double mouseX, double mouseY, int left, int top, int height) {
		return mouseX >= left && mouseX < left + WIDTH && mouseY >= top && mouseY < top + height;
	}

	/** The station of the row under the cursor, or null. */
	@Nullable
	public SeenNetwork.Area click(double mouseX, double mouseY, int left, int top, int height) {
		final int row = row(left, top, height, mouseX, mouseY);
		return row < 0 ? null : entries[shown[row]].area;
	}

	/** The first station that matches the filter, or null. */
	@Nullable
	public SeenNetwork.Area first() {
		return shownCount == 0 ? null : entries[shown[0]].area;
	}

	public void scroll(double amount, int top, int height) {
		scroll = Math.max(0, Math.min(scroll - (int) Math.signum(amount) * 3, shownCount - rows(top, height)));
	}

	private int row(int left, int top, int height, double mouseX, double mouseY) {
		final int listTop = listTop(top);
		if (mouseX < left || mouseX >= left + WIDTH || mouseY < listTop) {
			return -1;
		}
		final int i = (int) ((mouseY - listTop) / ROW);
		return i < rows(top, height) && scroll + i < shownCount ? scroll + i : -1;
	}

	private static int listTop(int top) {
		return top + PAD + FIELD + PAD;
	}

	private static int rows(int top, int height) {
		return Math.max(0, (top + height - PAD - listTop(top)) / ROW);
	}

	private void update() {
		final SeenNetwork now = MapClient.network();
		if (now != network || now != null && now.stationsVersion() != version) {
			network = now;
			version = now == null ? 0 : now.stationsVersion();
			rebuild();
			refilter();
		}
	}

	private void rebuild() {
		if (network == null) {
			entries = new Entry[0];
			return;
		}
		final TextRenderer text = MinecraftClient.getInstance().textRenderer;
		final List<Entry> list = new ArrayList<>(network.stations().size());
		for (final SeenNetwork.Area area : network.stations()) {
			final String name = displayName(area.name());
			list.add(new Entry(area, text.trimToWidth(name, WIDTH - PAD * 2 - 12), area.name().toLowerCase(Locale.ROOT)));
		}
		list.sort(Comparator.comparing(Entry::label, String.CASE_INSENSITIVE_ORDER));
		entries = list.toArray(new Entry[0]);
		shown = new int[entries.length];
	}

	private void refilter() {
		shownCount = 0;
		for (int i = 0; i < entries.length; i++) {
			if (filter.isEmpty() || entries[i].search.contains(filter)) {
				shown[shownCount++] = i;
			}
		}
		scroll = Math.max(0, Math.min(scroll, shownCount - 1));
	}

	/** An MTR name holds its languages split by {@code |}: the list shows the first that is not blank. */
	private static String displayName(String name) {
		for (final String part : name.split("\\|")) {
			if (!part.isBlank()) {
				return part.trim();
			}
		}
		return name;
	}
}
