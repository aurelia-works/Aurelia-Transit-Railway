package com.aureliatransit.architecture.client;

import org.mtr.libraries.com.google.gson.JsonElement;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client looks for the one wrench and the ATR menu, in a new file {@code config/aurelia/wrench.json}. Missing file, missing
 * key or a value that is not a boolean keeps the default (on), so old configs are untouched and need no migration.
 */
public final class WrenchSettings {

	private static final Logger LOGGER = LoggerFactory.getLogger("ATR Aurelia");
	private static boolean actionBar = true;
	private static boolean menuLook = true;
	private static boolean planRoof = true;
	private static boolean curvePaint = true;
	/** The curved taxiway lines' width, in percent of a painted cell. */
	private static int paintWidth = 100;
	private static final int[] PAINT_WIDTHS = {50, 75, 100, 125};
	/** The warning lights on bridge towers and pylons: 0 off, 1 steady, 2 flashing at night. */
	private static int towerLights = 2;

	private WrenchSettings() {
	}

	/** Whether switching the wrench mode shows it on the action bar. */
	public static boolean actionBar() {
		return actionBar;
	}

	public static void toggleActionBar() {
		actionBar = !actionBar;
		save();
	}

	/** Whether the ATR menu uses the dark Aurelia look; off leaves the vanilla background. */
	public static boolean menuLook() {
		return menuLook;
	}

	public static void toggleMenuLook() {
		menuLook = !menuLook;
		save();
	}

	public static void load(Path configDir) {
		final Path file = file(configDir);
		if (!Files.isRegularFile(file)) {
			return;
		}
		try {
			final JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			final JsonElement element = json.get("actionBar");
			if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
				actionBar = element.getAsBoolean();
			}
			final JsonElement roof = json.get("planRoof");
			if (roof != null && roof.isJsonPrimitive() && roof.getAsJsonPrimitive().isBoolean()) {
				planRoof = roof.getAsBoolean();
			}
			final JsonElement look = json.get("menuLook");
			if (look != null && look.isJsonPrimitive() && look.getAsJsonPrimitive().isBoolean()) {
				menuLook = look.getAsBoolean();
			}
			final JsonElement curve = json.get("curvePaint");
			if (curve != null && curve.isJsonPrimitive() && curve.getAsJsonPrimitive().isBoolean()) {
				curvePaint = curve.getAsBoolean();
			}
			final JsonElement lights = json.get("towerLights");
			if (lights != null && lights.isJsonPrimitive() && lights.getAsJsonPrimitive().isNumber()) {
				towerLights = Math.max(0, Math.min(2, lights.getAsInt()));
			}
			final JsonElement widthValue = json.get("paintWidth");
			if (widthValue != null && widthValue.isJsonPrimitive() && widthValue.getAsJsonPrimitive().isNumber()) {
				paintWidth = Math.max(25, Math.min(200, widthValue.getAsInt()));
			}
		} catch (Exception e) {
			LOGGER.error("[ATR] wrench.json could not be read; keeps the defaults", e);
		}
	}

	private static void save() {
		try {
			final Path file = file(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir());
			Files.createDirectories(file.getParent());
			final JsonObject json = new JsonObject();
			json.addProperty("actionBar", actionBar);
			json.addProperty("menuLook", menuLook);
			json.addProperty("planRoof", planRoof);
			json.addProperty("curvePaint", curvePaint);
			json.addProperty("paintWidth", paintWidth);
			json.addProperty("towerLights", towerLights);
			Files.writeString(file, json.toString());
		} catch (Exception e) {
			LOGGER.error("[ATR] wrench.json could not be written", e);
		}
	}

	/** Whether the station editor's plan from above shows roofs as a light wash over what they cover. */
	public static boolean planRoof() {
		return planRoof;
	}

	public static void togglePlanRoof() {
		planRoof = !planRoof;
		save();
	}

	/** Whether the smooth yellow lines round a taxiway's curves are drawn. */
	public static boolean curvePaint() {
		return curvePaint;
	}

	public static void toggleCurvePaint() {
		curvePaint = !curvePaint;
		save();
	}

	/** 0 off, 1 steady, 2 flashing at night: the warning lights on top of bridge towers and pylons. */
	public static int towerLights() {
		return towerLights;
	}

	public static void cycleTowerLights() {
		towerLights = (towerLights + 1) % 3;
		save();
	}

	public static int paintWidth() {
		return paintWidth;
	}

	public static void cyclePaintWidth() {
		int next = PAINT_WIDTHS[0];
		for (int i = 0; i < PAINT_WIDTHS.length; i++) {
			if (PAINT_WIDTHS[i] == paintWidth) {
				next = PAINT_WIDTHS[(i + 1) % PAINT_WIDTHS.length];
			}
		}
		paintWidth = next;
		save();
	}

	private static Path file(Path configDir) {
		return configDir.resolve("aurelia").resolve("wrench.json");
	}
}
