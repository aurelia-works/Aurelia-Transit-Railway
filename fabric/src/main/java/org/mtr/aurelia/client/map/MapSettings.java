package org.mtr.aurelia.client.map;

import net.fabricmc.loader.api.FabricLoader;
import org.mtr.aurelia.client.AureliaClient;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This client's map switches, in {@code config/aurelia/map.json}: the minimap and its size, zoom and corner, which
 * network layers show, the rail colour, and how many chunks the map reads each tick. A missing file or key keeps the
 * default, so an old config folder loads unchanged. The Aurelia panel's Map tab sets these.
 */
public final class MapSettings {

	public static final int SIZE_MIN = 64;
	public static final int SIZE_MAX = 200;
	/** Minimap zoom in hundredths of a screen pixel per block. */
	public static final int ZOOM_MIN = 25;
	public static final int ZOOM_MAX = 400;
	public static final int HUE_MAX = 360;
	public static final int SCAN_MIN = 1;
	public static final int SCAN_MAX = 16;

	public enum Corner {TOP_RIGHT, TOP_LEFT, BOTTOM_RIGHT, BOTTOM_LEFT}

	private static boolean minimap = true;
	private static int size = 100;
	private static int zoom = 100;
	private static Corner corner = Corner.TOP_RIGHT;
	private static boolean rails = true;
	private static boolean stations = true;
	private static boolean trains = true;
	private static int railHue = 210;
	private static int scanPerTick = 4;

	private MapSettings() {
	}

	public static void load(Path configDir) {
		final Path file = file(configDir);
		if (!Files.isRegularFile(file)) {
			return;
		}
		try {
			final JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			minimap = json.has("minimap") ? json.get("minimap").getAsBoolean() : minimap;
			size = json.has("size") ? clamp(json.get("size").getAsInt(), SIZE_MIN, SIZE_MAX) : size;
			zoom = json.has("zoom") ? clamp(json.get("zoom").getAsInt(), ZOOM_MIN, ZOOM_MAX) : zoom;
			corner = json.has("corner") ? Corner.values()[clamp(json.get("corner").getAsInt(), 0, Corner.values().length - 1)] : corner;
			rails = json.has("rails") ? json.get("rails").getAsBoolean() : rails;
			stations = json.has("stations") ? json.get("stations").getAsBoolean() : stations;
			trains = json.has("trains") ? json.get("trains").getAsBoolean() : trains;
			railHue = json.has("railHue") ? clamp(json.get("railHue").getAsInt(), 0, HUE_MAX) : railHue;
			scanPerTick = json.has("scanPerTick") ? clamp(json.get("scanPerTick").getAsInt(), SCAN_MIN, SCAN_MAX) : scanPerTick;
		} catch (Exception e) {
			AureliaClient.LOGGER.error("[ATR] map.json could not be read; the map keeps its defaults", e);
		}
	}

	public static boolean minimap() {
		return minimap;
	}

	public static int size() {
		return size;
	}

	public static int zoom() {
		return zoom;
	}

	public static Corner corner() {
		return corner;
	}

	public static boolean rails() {
		return rails;
	}

	public static boolean stations() {
		return stations;
	}

	public static boolean trains() {
		return trains;
	}

	public static int railHue() {
		return railHue;
	}

	public static int scanPerTick() {
		return scanPerTick;
	}

	/** The rail colour, from the hue slider. */
	public static int railColor() {
		return 0xFF000000 | Color.HSBtoRGB(railHue / (float) HUE_MAX, 0.45F, 0.95F) & 0xFFFFFF;
	}

	public static void toggleMinimap() {
		minimap = !minimap;
		save();
	}

	public static void setSize(int value) {
		size = clamp(value, SIZE_MIN, SIZE_MAX);
		save();
	}

	public static void setZoom(int value) {
		zoom = clamp(value, ZOOM_MIN, ZOOM_MAX);
		save();
	}

	public static void nextCorner() {
		corner = Corner.values()[(corner.ordinal() + 1) % Corner.values().length];
		save();
	}

	public static void toggleRails() {
		rails = !rails;
		save();
	}

	public static void toggleStations() {
		stations = !stations;
		save();
	}

	public static void toggleTrains() {
		trains = !trains;
		save();
	}

	public static void setRailHue(int value) {
		railHue = clamp(value, 0, HUE_MAX);
		save();
	}

	public static void setScanPerTick(int value) {
		scanPerTick = clamp(value, SCAN_MIN, SCAN_MAX);
		save();
	}

	private static void save() {
		try {
			final Path file = file(FabricLoader.getInstance().getConfigDir());
			Files.createDirectories(file.getParent());
			final JsonObject json = new JsonObject();
			json.addProperty("minimap", minimap);
			json.addProperty("size", size);
			json.addProperty("zoom", zoom);
			json.addProperty("corner", corner.ordinal());
			json.addProperty("rails", rails);
			json.addProperty("stations", stations);
			json.addProperty("trains", trains);
			json.addProperty("railHue", railHue);
			json.addProperty("scanPerTick", scanPerTick);
			Files.writeString(file, json.toString());
		} catch (Exception e) {
			AureliaClient.LOGGER.error("[ATR] map.json could not be saved", e);
		}
	}

	private static Path file(Path configDir) {
		return configDir.resolve("aurelia").resolve("map.json");
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
