package com.aureliatransit.architecture.client;

import org.mtr.aurelia.config.AureliaSettings;
import org.mtr.libraries.com.google.gson.JsonElement;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client looks for the one wrench, in the {@code wrench} section of {@code config/aurelia/settings.json} (formerly
 * {@code wrench.json}). A missing key, or a value that is not a boolean, keeps the default (on).
 */
public final class WrenchSettings {

	private static boolean actionBar = true;
	private static boolean menuLook = true;
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

	public static void load(Path configDir) {
		AureliaSettings.load(configDir);
		try {
			apply(AureliaSettings.section(AureliaSettings.WRENCH));
		} catch (Exception e) {
			defaults();
		}
	}

	/** Reads the old {@code wrench.json} into the section body settings.json keeps. */
	public static JsonObject readLegacy(Path file) throws IOException {
		apply(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject());
		return toJson();
	}

	private static void apply(JsonObject json) {
		defaults();
		final JsonElement element = json.get("actionBar");
		if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
			actionBar = element.getAsBoolean();
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
	}

	private static void defaults() {
		actionBar = true;
		menuLook = true;
		curvePaint = true;
		paintWidth = 100;
		towerLights = 2;
	}

	private static JsonObject toJson() {
		final JsonObject json = new JsonObject();
		json.addProperty("actionBar", actionBar);
		json.addProperty("menuLook", menuLook);
		json.addProperty("curvePaint", curvePaint);
		json.addProperty("paintWidth", paintWidth);
		json.addProperty("towerLights", towerLights);
		return json;
	}

	private static void save() {
		AureliaSettings.put(AureliaSettings.WRENCH, toJson());
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
}
